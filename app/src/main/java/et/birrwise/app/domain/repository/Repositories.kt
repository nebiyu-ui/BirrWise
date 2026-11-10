package et.birrwise.app.domain.repository

import et.birrwise.app.domain.Account
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.Person
import et.birrwise.app.domain.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Stored transactions that could be read. Rows that cannot be turned into a valid transaction
 * (for example a zero amount) are counted in [unreadableCount] instead of being hidden, so screens
 * can warn that the history is incomplete.
 */
data class TransactionsSnapshot(
    val transactions: List<Transaction>,
    val unreadableCount: Int
)

enum class CategoryDeleteResult {
    DELETED,
    NOT_FOUND,
    SYSTEM_CATEGORY,
    HAS_TRANSACTIONS,
    HAS_SUBCATEGORIES
}

interface AccountRepository {
    fun observeAccounts(includeArchived: Boolean): Flow<List<Account>>
    suspend fun getAccount(id: String): Account?
    suspend fun createAccount(account: Account)

    /** The currency of an account that already has transactions cannot be changed. */
    suspend fun updateAccount(account: Account)

    /** Archiving hides an account but never deletes it or its transactions. */
    suspend fun setArchived(id: String, archived: Boolean)
}

interface TransactionRepository {
    fun observeTransactions(): Flow<TransactionsSnapshot>
    fun observeAccountTransactions(accountId: String): Flow<TransactionsSnapshot>
    suspend fun getTransaction(id: String): Transaction?

    /** Transfer legs that belong to a transfer group must be added with [recordTransfer]. */
    suspend fun addTransaction(transaction: Transaction)

    /** Edits of a transfer leg may not change its type, amount, account, time, or group. */
    suspend fun updateTransaction(transaction: Transaction)

    /** Deleting a leg of a transfer group deletes the whole group, so no half transfer is left. */
    suspend fun deleteTransaction(id: String)

    /**
     * Saves both legs of a transfer, and the optional fee charged to the sending account, together
     * or not at all.
     */
    suspend fun recordTransfer(outLeg: Transaction, inLeg: Transaction, fee: Transaction? = null)
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun getCategory(id: String): Category?

    /** Adds the built-in categories. Safe to call any number of times. */
    suspend fun ensureDefaultCategories()
    suspend fun createCategory(category: Category)
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(id: String): CategoryDeleteResult
}

interface PersonRepository {
    fun observePeople(includeHidden: Boolean): Flow<List<Person>>
    suspend fun getPerson(id: String): Person?
    suspend fun createPerson(person: Person)
    suspend fun updatePerson(person: Person)
}
