package et.birrwise.app.data

import androidx.room.withTransaction
import et.birrwise.app.data.db.BirrWiseDatabase
import et.birrwise.app.data.db.TransactionEntity
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.Person
import et.birrwise.app.domain.Transaction
import et.birrwise.app.domain.TransactionType
import et.birrwise.app.domain.repository.AccountRepository
import et.birrwise.app.domain.repository.CategoryDeleteResult
import et.birrwise.app.domain.repository.CategoryRepository
import et.birrwise.app.domain.repository.PersonRepository
import et.birrwise.app.domain.repository.TransactionRepository
import et.birrwise.app.domain.repository.TransactionsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

class RoomAccountRepository(
    private val database: BirrWiseDatabase,
    private val clock: Clock = Clock.systemUTC()
) : AccountRepository {

    private val dao = database.accountDao()

    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> {
        val source = if (includeArchived) dao.observeAll() else dao.observeActive()
        return source.map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getAccount(id: String): Account? = dao.getById(id)?.toDomain()

    override suspend fun createAccount(account: Account) {
        val now = clock.millis()
        dao.insert(account.toEntity(createdAt = now, updatedAt = now))
    }

    override suspend fun updateAccount(account: Account) {
        database.withTransaction {
            val existing = dao.getById(account.id)
                ?: throw IllegalArgumentException("Account ${account.id} does not exist")
            if (existing.currencyCode != account.currencyCode && dao.transactionCount(account.id) > 0) {
                throw IllegalStateException("The currency of an account with transactions cannot be changed")
            }
            dao.update(account.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis()))
        }
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        val existing = dao.getById(id) ?: throw IllegalArgumentException("Account $id does not exist")
        dao.update(existing.copy(archived = archived, updatedAt = clock.millis()))
    }
}

class RoomTransactionRepository(
    private val database: BirrWiseDatabase,
    private val clock: Clock = Clock.systemUTC()
) : TransactionRepository {

    private val dao = database.transactionDao()

    override fun observeTransactions(): Flow<TransactionsSnapshot> =
        dao.observeAll().map { it.toSnapshot() }

    override fun observeAccountTransactions(accountId: String): Flow<TransactionsSnapshot> =
        dao.observeForAccount(accountId).map { it.toSnapshot() }

    override suspend fun getTransaction(id: String): Transaction? = dao.getById(id)?.toDomainOrNull()

    override suspend fun addTransaction(transaction: Transaction) {
        requireConsistentReversalLink(transaction)
        require(!(isTransferLeg(transaction) && transaction.transferGroupId != null)) {
            "Transfer legs that belong to a transfer group must be saved with recordTransfer"
        }
        val now = clock.millis()
        dao.insert(transaction.toEntity(createdAt = now, updatedAt = now))
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        requireConsistentReversalLink(transaction)
        database.withTransaction {
            val existing = dao.getById(transaction.id)
                ?: throw IllegalArgumentException("Transaction ${transaction.id} does not exist")
            val updated = transaction.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis())
            if (existing.transferGroupId != null || updated.transferGroupId != null) {
                require(
                    updated.transferGroupId == existing.transferGroupId &&
                        updated.transactionType == existing.transactionType &&
                        updated.amountMinorUnits == existing.amountMinorUnits &&
                        updated.currencyCode == existing.currencyCode &&
                        updated.accountId == existing.accountId &&
                        updated.occurredAt == existing.occurredAt
                ) { "A transfer leg cannot change its type, amount, account, time, or group" }
            }
            dao.update(updated)
        }
    }

    override suspend fun deleteTransaction(id: String) {
        val existing = dao.getById(id) ?: return
        val group = existing.transferGroupId
        if (group == null) {
            dao.deleteById(id)
        } else {
            database.withTransaction {
                for (leg in dao.getByTransferGroup(group)) {
                    dao.deleteById(leg.id)
                }
            }
        }
    }

    override suspend fun recordTransfer(outLeg: Transaction, inLeg: Transaction) {
        require(outLeg.type == TransactionType.TRANSFER_OUT) { "The first leg must be TRANSFER_OUT" }
        require(inLeg.type == TransactionType.TRANSFER_IN) { "The second leg must be TRANSFER_IN" }
        val group = outLeg.transferGroupId
        require(!group.isNullOrBlank() && group == inLeg.transferGroupId) {
            "Both legs must share one transfer group id"
        }
        require(outLeg.accountId != inLeg.accountId) { "A transfer needs two different accounts" }
        require(outLeg.amount == inLeg.amount) { "Both legs must have the same amount and currency" }
        val now = clock.millis()
        database.withTransaction {
            dao.insert(outLeg.toEntity(createdAt = now, updatedAt = now))
            dao.insert(inLeg.toEntity(createdAt = now, updatedAt = now))
        }
    }

    private fun isTransferLeg(transaction: Transaction): Boolean =
        transaction.type == TransactionType.TRANSFER_IN || transaction.type == TransactionType.TRANSFER_OUT

    private fun requireConsistentReversalLink(transaction: Transaction) {
        val isReversal = transaction.type == TransactionType.REVERSAL
        require(!isReversal || transaction.reversesTransactionId != null) {
            "A reversal must say which transaction it reverses"
        }
        require(isReversal || transaction.reversesTransactionId == null) {
            "Only a reversal can point to a reversed transaction"
        }
    }

    private fun List<TransactionEntity>.toSnapshot(): TransactionsSnapshot {
        val valid = mutableListOf<Transaction>()
        var unreadable = 0
        for (row in this) {
            val transaction = row.toDomainOrNull()
            if (transaction != null) valid += transaction else unreadable++
        }
        return TransactionsSnapshot(valid, unreadable)
    }
}

class RoomCategoryRepository(
    private val database: BirrWiseDatabase,
    private val clock: Clock = Clock.systemUTC()
) : CategoryRepository {

    private val dao = database.categoryDao()

    override fun observeCategories(): Flow<List<Category>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getCategory(id: String): Category? = dao.getById(id)?.toDomain()

    override suspend fun ensureDefaultCategories() {
        dao.insertAllIgnoringExisting(DefaultCategories.entities(clock.millis()))
    }

    override suspend fun createCategory(category: Category) {
        require(!category.isSystem) { "Built-in categories are created by the app itself" }
        val now = clock.millis()
        dao.insert(category.toEntity(createdAt = now, updatedAt = now))
    }

    override suspend fun updateCategory(category: Category) {
        val existing = dao.getById(category.id)
            ?: throw IllegalArgumentException("Category ${category.id} does not exist")
        require(!existing.isSystemCategory) { "Built-in categories cannot be edited" }
        require(!category.isSystem) { "A custom category cannot become a built-in one" }
        dao.update(category.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis()))
    }

    override suspend fun deleteCategory(id: String): CategoryDeleteResult {
        val existing = dao.getById(id) ?: return CategoryDeleteResult.NOT_FOUND
        if (existing.isSystemCategory) return CategoryDeleteResult.SYSTEM_CATEGORY
        return database.withTransaction {
            if (dao.transactionCount(id) > 0) {
                CategoryDeleteResult.HAS_TRANSACTIONS
            } else if (dao.childCount(id) > 0) {
                CategoryDeleteResult.HAS_SUBCATEGORIES
            } else {
                dao.deleteById(id)
                CategoryDeleteResult.DELETED
            }
        }
    }
}

class RoomPersonRepository(
    private val database: BirrWiseDatabase,
    private val clock: Clock = Clock.systemUTC()
) : PersonRepository {

    private val dao = database.personDao()

    override fun observePeople(includeHidden: Boolean): Flow<List<Person>> {
        val source = if (includeHidden) dao.observeAll() else dao.observeVisible()
        return source.map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getPerson(id: String): Person? = dao.getById(id)?.toDomain()

    override suspend fun createPerson(person: Person) {
        val now = clock.millis()
        dao.insert(person.toEntity(createdAt = now, updatedAt = now))
    }

    override suspend fun updatePerson(person: Person) {
        val existing = dao.getById(person.id)
            ?: throw IllegalArgumentException("Person ${person.id} does not exist")
        dao.update(person.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis()))
    }
}
