package et.birrwise.app.ui.transactions

import et.birrwise.app.domain.EntryKind
import et.birrwise.app.domain.ETB
import et.birrwise.app.domain.Money
import et.birrwise.app.domain.TransactionFormError
import et.birrwise.app.domain.TransactionFormInput
import et.birrwise.app.domain.TransactionType
import et.birrwise.app.domain.account
import et.birrwise.app.domain.tx
import et.birrwise.app.ui.accounts.FakeAccountRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TransactionEditorViewModelTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")
    private val now: Instant = Instant.parse("2026-11-07T09:00:00Z")
    private val clock: Clock = Clock.fixed(now, addis)
    private val today: LocalDate = LocalDate.of(2026, 11, 7)

    private class Fixture(
        accounts: FakeAccountRepository,
        val transactions: FakeTransactionRepository = FakeTransactionRepository(),
        val categories: FakeCategoryRepository = FakeCategoryRepository(),
        val accountRepository: FakeAccountRepository = accounts
    )

    private fun counter(): () -> String {
        var n = 0
        return { "id-${++n}" }
    }

    private fun viewModel(
        fixture: Fixture,
        transactionId: String? = null,
        presetAccountId: String? = null
    ) = TransactionEditorViewModel(
        transactionId = transactionId,
        presetAccountId = presetAccountId,
        accounts = fixture.accountRepository,
        categories = fixture.categories,
        transactions = fixture.transactions,
        newId = counter(),
        clock = clock
    )

    private fun input(
        kind: EntryKind = EntryKind.EXPENSE,
        accountId: String? = "A1",
        toAccountId: String? = null,
        amount: String = "250.50",
        fee: String = ""
    ) = TransactionFormInput(
        kind, accountId, toAccountId, amount, fee, today, null, "Lunch", "", "", false
    )

    @Test
    fun noAccountsMeansTheNoAccountsState() = runBlocking<Unit> {
        val vm = viewModel(Fixture(FakeAccountRepository()))
        vm.load()
        assertEquals(TransactionEditorLoadState.NoAccounts, vm.load.value)
    }

    @Test
    fun loadSeedsCategoriesAndOffersOnlyActiveAccounts() = runBlocking<Unit> {
        val fixture = Fixture(
            FakeAccountRepository(
                listOf(account("A1"), account("A2").copy(archived = true))
            )
        )
        val vm = viewModel(fixture, presetAccountId = "A1")
        vm.load()

        val ready = vm.load.value as TransactionEditorLoadState.Ready
        assertEquals(listOf("A1"), ready.accounts.map { it.id })
        assertEquals(15, ready.categories.size)
        assertEquals("A1", ready.presetAccountId)
        assertEquals(null, ready.existing)
    }

    @Test
    fun unknownTransactionIsNotFound() = runBlocking<Unit> {
        val vm = viewModel(Fixture(FakeAccountRepository(listOf(account("A1")))), transactionId = "nope")
        vm.load()
        assertEquals(TransactionEditorLoadState.NotFound, vm.load.value)
    }

    @Test
    fun anArchivedAccountStaysAvailableForItsOwnTransaction() = runBlocking<Unit> {
        val fixture = Fixture(
            FakeAccountRepository(listOf(account("A1"), account("A2").copy(archived = true))),
            FakeTransactionRepository(listOf(tx("t1", "A2", TransactionType.EXPENSE, 100L)))
        )
        val vm = viewModel(fixture, transactionId = "t1")
        vm.load()
        val ready = vm.load.value as TransactionEditorLoadState.Ready
        assertEquals(setOf("A1", "A2"), ready.accounts.map { it.id }.toSet())
    }

    @Test
    fun aTransferLoadsBothLegs() = runBlocking<Unit> {
        val out = tx("out", "A1", TransactionType.TRANSFER_OUT, 100L, transferGroupId = "g")
        val into = tx("in", "A2", TransactionType.TRANSFER_IN, 100L, transferGroupId = "g")
        val other = tx("x", "A1", TransactionType.EXPENSE, 50L)
        val fixture = Fixture(
            FakeAccountRepository(listOf(account("A1"), account("A2"))),
            FakeTransactionRepository(listOf(out, into, other))
        )
        val vm = viewModel(fixture, transactionId = "out")
        vm.load()
        val ready = vm.load.value as TransactionEditorLoadState.Ready
        assertEquals(setOf("out", "in"), ready.transferLegs.map { it.id }.toSet())
    }

    @Test
    fun savingAnExpenseAddsIt() = runBlocking<Unit> {
        val fixture = Fixture(FakeAccountRepository(listOf(account("A1"))))
        val vm = viewModel(fixture)
        vm.load()
        vm.save(input())

        assertEquals(TransactionEditorSaveState.Saved, vm.save.value)
        val saved = fixture.transactions.transactions.single()
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(Money(25_050L, ETB), saved.amount)
        assertEquals(now, saved.occurredAt)
        assertEquals("Lunch", saved.description)
    }

    @Test
    fun savingATransferWithAFeeRecordsThreeEntries() = runBlocking<Unit> {
        val fixture = Fixture(FakeAccountRepository(listOf(account("A1"), account("A2"))))
        val vm = viewModel(fixture)
        vm.load()
        vm.save(input(kind = EntryKind.TRANSFER, toAccountId = "A2", amount = "300", fee = "10"))

        assertEquals(TransactionEditorSaveState.Saved, vm.save.value)
        val types = fixture.transactions.transactions.map { it.type }.toSet()
        assertEquals(
            setOf(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN, TransactionType.FEE),
            types
        )
        assertEquals(3, fixture.transactions.transactions.size)
    }

    @Test
    fun invalidInputSavesNothingAndCanBeFixed() = runBlocking<Unit> {
        val fixture = Fixture(FakeAccountRepository(listOf(account("A1"))))
        val vm = viewModel(fixture)
        vm.load()

        vm.save(input(amount = ""))
        assertEquals(
            TransactionEditorSaveState.Invalid(setOf(TransactionFormError.AMOUNT_REQUIRED)),
            vm.save.value
        )
        assertTrue(fixture.transactions.transactions.isEmpty())

        vm.save(input(amount = "40"))
        assertEquals(TransactionEditorSaveState.Saved, vm.save.value)
        assertEquals(1, fixture.transactions.transactions.size)
    }

    @Test
    fun editingUpdatesTheSameTransaction() = runBlocking<Unit> {
        val existing = tx("t1", "A1", TransactionType.EXPENSE, 10_000L, occurredAt = now)
        val fixture = Fixture(
            FakeAccountRepository(listOf(account("A1"))),
            FakeTransactionRepository(listOf(existing))
        )
        val vm = viewModel(fixture, transactionId = "t1")
        vm.load()
        vm.save(input(amount = "300"))

        assertEquals(TransactionEditorSaveState.Saved, vm.save.value)
        val saved = fixture.transactions.transactions.single()
        assertEquals("t1", saved.id)
        assertEquals(Money(30_000L, ETB), saved.amount)
    }

    @Test
    fun aWriteFailureMeansFailedNotACrash() = runBlocking<Unit> {
        val fixture = Fixture(FakeAccountRepository(listOf(account("A1"))))
        fixture.transactions.failWritesWith = RuntimeException("disk full")
        val vm = viewModel(fixture)
        vm.load()
        vm.save(input())
        assertEquals(TransactionEditorSaveState.Failed, vm.save.value)
    }

    @Test
    fun deletingRemovesTheTransactionAndATransferGoesAsAWhole() = runBlocking<Unit> {
        val single = tx("t1", "A1", TransactionType.EXPENSE, 100L)
        val out = tx("out", "A1", TransactionType.TRANSFER_OUT, 100L, transferGroupId = "g")
        val into = tx("in", "A2", TransactionType.TRANSFER_IN, 100L, transferGroupId = "g")
        val fixture = Fixture(
            FakeAccountRepository(listOf(account("A1"), account("A2"))),
            FakeTransactionRepository(listOf(single, out, into))
        )

        val first = viewModel(fixture, transactionId = "t1")
        first.load()
        assertTrue(first.delete())
        assertEquals(2, fixture.transactions.transactions.size)

        val second = viewModel(fixture, transactionId = "in")
        second.load()
        assertTrue(second.delete())
        assertTrue(fixture.transactions.transactions.isEmpty())
    }

    @Test
    fun deletingWithoutATransactionDoesNothing() = runBlocking<Unit> {
        val vm = viewModel(Fixture(FakeAccountRepository(listOf(account("A1")))))
        vm.load()
        assertFalse(vm.delete())
    }
}
