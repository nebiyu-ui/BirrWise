package et.birrwise.app.data

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import et.birrwise.app.data.db.BirrWiseDatabase
import et.birrwise.app.data.db.ImportBatchEntity
import et.birrwise.app.data.db.TransactionEntity
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.AccountType
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.CategoryType
import et.birrwise.app.domain.ETB
import et.birrwise.app.domain.FinanceEngine
import et.birrwise.app.domain.ImportStatus
import et.birrwise.app.domain.Money
import et.birrwise.app.domain.Person
import et.birrwise.app.domain.SourceType
import et.birrwise.app.domain.Transaction
import et.birrwise.app.domain.TransactionStatus
import et.birrwise.app.domain.TransactionType.EXPENSE
import et.birrwise.app.domain.TransactionType.INCOME
import et.birrwise.app.domain.TransactionType.REVERSAL
import et.birrwise.app.domain.TransactionType.TRANSFER_IN
import et.birrwise.app.domain.TransactionType.TRANSFER_OUT
import et.birrwise.app.domain.account
import et.birrwise.app.domain.etb
import et.birrwise.app.domain.repository.CategoryDeleteResult
import et.birrwise.app.domain.tx
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RoomRepositoriesTest : DatabaseTestBase() {

    private val accounts by lazy { RoomAccountRepository(db, clock) }
    private val transactions by lazy { RoomTransactionRepository(db, clock) }
    private val categories by lazy { RoomCategoryRepository(db, clock) }
    private val people by lazy { RoomPersonRepository(db, clock) }

    private suspend fun seedAccounts(vararg ids: String) {
        for (id in ids) accounts.createAccount(account(id, openingBirr = 1000))
    }

    private fun transferLegs(
        group: String = "g1",
        amountMinor: Long = 30_000L,
        from: String = "A1",
        to: String = "A2"
    ): Pair<Transaction, Transaction> = Pair(
        tx("out-$group", from, TRANSFER_OUT, amountMinor, transferGroupId = group),
        tx("in-$group", to, TRANSFER_IN, amountMinor, transferGroupId = group)
    )

    // ---- Accounts -------------------------------------------------------------------------

    @Test
    fun accountRoundTripKeepsEveryField() = runBlocking<Unit> {
        val original = Account(
            id = "A1",
            name = "Main account",
            currencyCode = ETB,
            openingBalance = Money(150_050L, ETB),
            openingBalanceDate = Instant.parse("2026-02-01T00:00:00Z"),
            provider = "Commercial Bank of Ethiopia",
            type = AccountType.BANK,
            archived = false,
            notes = "salary account"
        )
        accounts.createAccount(original)
        assertEquals(original, accounts.getAccount("A1"))
    }

    @Test
    fun archivingHidesAnAccountButKeepsItsHistory() = runBlocking<Unit> {
        seedAccounts("A1")
        transactions.addTransaction(tx("t1", "A1", EXPENSE, 100L))
        accounts.setArchived("A1", true)

        assertTrue(accounts.observeAccounts(false).first().isEmpty())
        val all = accounts.observeAccounts(true).first()
        assertEquals(1, all.size)
        assertTrue(all[0].archived)
        assertNotNull(transactions.getTransaction("t1"))
    }

    @Test
    fun currencyOfAnAccountWithTransactionsCannotChange() = runBlocking<Unit> {
        seedAccounts("A1")
        transactions.addTransaction(tx("t1", "A1", EXPENSE, 100L))
        val changed = Account("A1", "Account A1", "USD", Money(0L, "USD"))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { accounts.updateAccount(changed) }
        }
    }

    @Test
    fun anAccountWithTransactionsCannotBeDeleted() = runBlocking<Unit> {
        seedAccounts("A1")
        transactions.addTransaction(tx("t1", "A1", EXPENSE, 100L))
        val entity = db.accountDao().getById("A1")!!
        assertThrows(SQLiteException::class.java) {
            runBlocking { db.accountDao().delete(entity) }
        }
        assertNotNull(transactions.getTransaction("t1"))
    }

    // ---- Transactions ---------------------------------------------------------------------

    @Test
    fun transactionRoundTripKeepsEveryField() = runBlocking<Unit> {
        seedAccounts("A1")
        categories.createCategory(Category(id = "c1", name = "Snacks", type = CategoryType.EXPENSE))
        people.createPerson(Person(id = "p1", displayName = "Abebe Kebede"))
        db.importBatchDao().insert(
            ImportBatchEntity(
                id = "b1",
                originalFilename = "statement.csv",
                sourceType = SourceType.CSV,
                accountId = "A1",
                importedAt = 1L,
                recordsDetected = 1,
                recordsImported = 1,
                recordsSkipped = 0,
                recordsNeedingReview = 0,
                status = ImportStatus.COMPLETED,
                validationSummary = null
            )
        )
        val original = Transaction(
            id = "t1",
            accountId = "A1",
            type = EXPENSE,
            amount = Money(12_345L, ETB),
            occurredAt = Instant.parse("2026-02-14T08:30:00Z"),
            categoryId = "c1",
            status = TransactionStatus.PENDING_REVIEW,
            isExcludedFromReports = true,
            description = "Lunch",
            merchantName = "Cafe X",
            counterparty = "CAFE X PLC",
            personId = "p1",
            sourceType = SourceType.SMS,
            externalReference = "FT123",
            importBatchId = "b1",
            notes = "with the team",
            reviewedAt = Instant.parse("2026-02-15T10:00:00Z")
        )
        transactions.addTransaction(original)
        assertEquals(original, transactions.getTransaction("t1"))
    }

    @Test
    fun updatingAndDeletingATransactionPersists() = runBlocking<Unit> {
        seedAccounts("A1")
        val original = tx("t1", "A1", EXPENSE, 10_000L)
        transactions.addTransaction(original)

        val edited = original.copy(description = "Corrected", amount = Money(12_500L, ETB))
        transactions.updateTransaction(edited)
        assertEquals(edited, transactions.getTransaction("t1"))

        transactions.deleteTransaction("t1")
        assertNull(transactions.getTransaction("t1"))
    }

    @Test
    fun transactionForAnUnknownAccountIsRejected() {
        assertThrows(SQLiteException::class.java) {
            runBlocking { transactions.addTransaction(tx("t1", "missing", EXPENSE, 100L)) }
        }
    }

    @Test
    fun editingATransactionChangesTheCalculatedBalance() = runBlocking<Unit> {
        accounts.createAccount(account("A1", openingBirr = 1000))
        val original = tx("e1", "A1", EXPENSE, 10_000L)
        transactions.addTransaction(original)
        val savedAccount = accounts.getAccount("A1")!!

        val before = FinanceEngine
            .accountBalance(savedAccount, transactions.observeTransactions().first().transactions)
            .balance
        transactions.updateTransaction(original.copy(amount = Money(25_000L, ETB)))
        val after = FinanceEngine
            .accountBalance(savedAccount, transactions.observeTransactions().first().transactions)
            .balance

        assertEquals(etb(900), before)
        assertEquals(etb(750), after)
    }

    @Test
    fun unreadableRowsAreCountedNotHidden() = runBlocking<Unit> {
        seedAccounts("A1")
        transactions.addTransaction(tx("good", "A1", INCOME, 100L))
        db.transactionDao().insert(
            TransactionEntity(
                id = "bad",
                accountId = "A1",
                transactionType = EXPENSE,
                amountMinorUnits = 0L,
                currencyCode = ETB,
                occurredAt = 1L,
                description = "",
                merchantName = null,
                counterparty = null,
                personId = null,
                categoryId = null,
                sourceType = SourceType.MANUAL,
                externalReference = null,
                importBatchId = null,
                transferGroupId = null,
                reversesTransactionId = null,
                status = TransactionStatus.CONFIRMED,
                notes = null,
                createdAt = 1L,
                updatedAt = 1L,
                reviewedAt = null,
                isExcludedFromReports = false
            )
        )
        val snapshot = transactions.observeTransactions().first()
        assertEquals(1, snapshot.transactions.size)
        assertEquals("good", snapshot.transactions[0].id)
        assertEquals(1, snapshot.unreadableCount)
    }

    // ---- Transfers ------------------------------------------------------------------------

    @Test
    fun transferIsSavedAsTwoLegsAndKeepsTheCombinedBalance() = runBlocking<Unit> {
        seedAccounts("A1", "A2")
        val (out, inn) = transferLegs()
        transactions.recordTransfer(out, inn)

        val all = transactions.observeTransactions().first().transactions
        assertEquals(2, all.size)
        val a1 = FinanceEngine.accountBalance(accounts.getAccount("A1")!!, all).balance
        val a2 = FinanceEngine.accountBalance(accounts.getAccount("A2")!!, all).balance
        assertEquals(etb(700), a1)
        assertEquals(etb(1300), a2)
        assertEquals(etb(2000), a1 + a2)
    }

    @Test
    fun aTransferWithOneBadLegSavesNothing() = runBlocking<Unit> {
        seedAccounts("A1")
        val (out, inn) = transferLegs(to = "ghost")
        assertThrows(SQLiteException::class.java) {
            runBlocking { transactions.recordTransfer(out, inn) }
        }
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun invalidTransfersAreRejectedBeforeSaving() = runBlocking<Unit> {
        seedAccounts("A1", "A2")
        val (out, inn) = transferLegs()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { transactions.recordTransfer(out, inn.copy(accountId = "A1")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { transactions.recordTransfer(out, inn.copy(amount = Money(1L, ETB))) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { transactions.recordTransfer(out, inn.copy(transferGroupId = "other")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { transactions.recordTransfer(inn, out) }
        }
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun groupedTransferLegsCannotBeAddedOneByOne() {
        val (out, _) = transferLegs()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { transactions.addTransaction(out) }
        }
    }

    @Test
    fun deletingOneLegDeletesTheWholeTransfer() = runBlocking<Unit> {
        seedAccounts("A1", "A2")
        val (out, inn) = transferLegs()
        transactions.recordTransfer(out, inn)
        transactions.deleteTransaction("in-g1")
        assertEquals(0, db.transactionDao().count())
    }

    @Test
    fun aTransferLegCanBeAnnotatedButNotRewritten() = runBlocking<Unit> {
        seedAccounts("A1", "A2")
        val (out, inn) = transferLegs()
        transactions.recordTransfer(out, inn)

        val annotated = out.copy(notes = "rent share")
        transactions.updateTransaction(annotated)
        assertEquals("rent share", transactions.getTransaction("out-g1")!!.notes)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { transactions.updateTransaction(out.copy(amount = Money(99L, ETB))) }
        }
    }

    // ---- Reversals ------------------------------------------------------------------------

    @Test
    fun reversalMustPointToAnExistingTransaction() = runBlocking<Unit> {
        seedAccounts("A1")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { transactions.addTransaction(tx("r0", "A1", REVERSAL, 100L)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transactions.addTransaction(tx("x0", "A1", EXPENSE, 100L, reversesTransactionId = "e1"))
            }
        }
        assertThrows(SQLiteException::class.java) {
            runBlocking {
                transactions.addTransaction(tx("r1", "A1", REVERSAL, 100L, reversesTransactionId = "ghost"))
            }
        }
    }

    @Test
    fun aReversedTransactionCannotBeDeletedFirst() = runBlocking<Unit> {
        seedAccounts("A1")
        transactions.addTransaction(tx("e1", "A1", EXPENSE, 100L))
        transactions.addTransaction(tx("r1", "A1", REVERSAL, 100L, reversesTransactionId = "e1"))

        assertThrows(SQLiteException::class.java) {
            runBlocking { transactions.deleteTransaction("e1") }
        }
        transactions.deleteTransaction("r1")
        transactions.deleteTransaction("e1")
        assertNull(transactions.getTransaction("e1"))
    }

    // ---- Categories, people, import batches -----------------------------------------------

    @Test
    fun defaultCategoriesAreAddedOnceNoMatterHowOftenCalled() = runBlocking<Unit> {
        categories.ensureDefaultCategories()
        categories.ensureDefaultCategories()
        val all = categories.observeCategories().first()
        assertEquals(15, all.size)
        assertTrue(all.all { it.isSystem })
        assertEquals(CategoryType.TRANSFER, all.first { it.id == "sys_transfers" }.type)
        assertEquals(CategoryType.INCOME, all.first { it.id == "sys_income" }.type)
    }

    @Test
    fun categoryDeletionExplainsWhyItIsRefused() = runBlocking<Unit> {
        categories.ensureDefaultCategories()
        categories.createCategory(Category(id = "parent", name = "Parent", type = CategoryType.EXPENSE))
        categories.createCategory(
            Category(id = "child", name = "Child", type = CategoryType.EXPENSE, parentId = "parent")
        )
        categories.createCategory(Category(id = "used", name = "Used", type = CategoryType.EXPENSE))
        categories.createCategory(Category(id = "free", name = "Free", type = CategoryType.EXPENSE))
        seedAccounts("A1")
        transactions.addTransaction(tx("t1", "A1", EXPENSE, 100L, categoryId = "used"))

        assertEquals(CategoryDeleteResult.SYSTEM_CATEGORY, categories.deleteCategory("sys_food_groceries"))
        assertEquals(CategoryDeleteResult.HAS_TRANSACTIONS, categories.deleteCategory("used"))
        assertEquals(CategoryDeleteResult.HAS_SUBCATEGORIES, categories.deleteCategory("parent"))
        assertEquals(CategoryDeleteResult.DELETED, categories.deleteCategory("free"))
        assertEquals(CategoryDeleteResult.NOT_FOUND, categories.deleteCategory("free"))
        assertNotNull(categories.getCategory("used"))
    }

    @Test
    fun builtInCategoriesCannotBeEdited() = runBlocking<Unit> {
        categories.ensureDefaultCategories()
        val builtIn = categories.getCategory("sys_fuel")!!
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { categories.updateCategory(builtIn.copy(name = "Petrol")) }
        }
    }

    @Test
    fun deletingAPersonKeepsTheirTransactions() = runBlocking<Unit> {
        seedAccounts("A1")
        people.createPerson(Person(id = "p1", displayName = "Sara"))
        transactions.addTransaction(tx("t1", "A1", INCOME, 100L).copy(personId = "p1"))

        db.personDao().delete(db.personDao().getById("p1")!!)

        val after = transactions.getTransaction("t1")
        assertNotNull(after)
        assertNull(after!!.personId)
    }

    @Test
    fun hiddenPeopleAreLeftOutUnlessAsked() = runBlocking<Unit> {
        people.createPerson(Person(id = "p1", displayName = "Sara"))
        people.createPerson(Person(id = "p2", displayName = "Shop Owner", isHidden = true))
        assertEquals(listOf("p1"), people.observePeople(false).first().map { it.id })
        assertEquals(2, people.observePeople(true).first().size)
    }

    @Test
    fun deletingAnImportBatchKeepsItsTransactions() = runBlocking<Unit> {
        seedAccounts("A1")
        db.importBatchDao().insert(
            ImportBatchEntity(
                id = "b1",
                originalFilename = null,
                sourceType = SourceType.CSV,
                accountId = "A1",
                importedAt = 1L,
                recordsDetected = 1,
                recordsImported = 1,
                recordsSkipped = 0,
                recordsNeedingReview = 0,
                status = ImportStatus.COMPLETED,
                validationSummary = null
            )
        )
        transactions.addTransaction(tx("t1", "A1", EXPENSE, 100L).copy(importBatchId = "b1"))

        db.importBatchDao().delete(db.importBatchDao().getById("b1")!!)

        val after = transactions.getTransaction("t1")
        assertNotNull(after)
        assertNull(after!!.importBatchId)
    }

    // ---- Persistence ----------------------------------------------------------------------

    @Test
    fun dataSurvivesClosingAndReopeningTheDatabase() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileName = "persistence-test.db"
        context.deleteDatabase(fileName)

        val first = Room.databaseBuilder(context, BirrWiseDatabase::class.java, fileName)
            .allowMainThreadQueries()
            .build()
        RoomAccountRepository(first, clock).createAccount(account("A1", openingBirr = 100))
        RoomTransactionRepository(first, clock).addTransaction(tx("t1", "A1", INCOME, 5_000L))
        first.close()

        val second = Room.databaseBuilder(context, BirrWiseDatabase::class.java, fileName)
            .allowMainThreadQueries()
            .build()
        try {
            val savedAccount = RoomAccountRepository(second, clock).getAccount("A1")!!
            val snapshot = RoomTransactionRepository(second, clock).observeTransactions().first()
            assertEquals(1, snapshot.transactions.size)
            assertEquals(etb(150), FinanceEngine.accountBalance(savedAccount, snapshot.transactions).balance)
        } finally {
            second.close()
            context.deleteDatabase(fileName)
        }
    }
}
