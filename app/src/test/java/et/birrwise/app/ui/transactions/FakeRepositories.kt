package et.birrwise.app.ui.transactions

import et.birrwise.app.data.DefaultCategories
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.Transaction
import et.birrwise.app.domain.repository.CategoryDeleteResult
import et.birrwise.app.domain.repository.CategoryRepository
import et.birrwise.app.domain.repository.TransactionRepository
import et.birrwise.app.domain.repository.TransactionsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory transaction repository for tests. Writes can be made to fail. */
class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {

    private val state = MutableStateFlow(initial)

    var failWritesWith: Exception? = null

    val transactions: List<Transaction> get() = state.value

    override fun observeTransactions(): Flow<TransactionsSnapshot> =
        state.map { TransactionsSnapshot(it, 0) }

    override fun observeAccountTransactions(accountId: String): Flow<TransactionsSnapshot> =
        state.map { list -> TransactionsSnapshot(list.filter { it.accountId == accountId }, 0) }

    override suspend fun getTransaction(id: String): Transaction? =
        state.value.firstOrNull { it.id == id }

    override suspend fun addTransaction(transaction: Transaction) {
        failWritesWith?.let { throw it }
        state.value = state.value + transaction
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        failWritesWith?.let { throw it }
        state.value = state.value.map { if (it.id == transaction.id) transaction else it }
    }

    /** Like the real repository, deleting a transfer leg deletes the whole transfer. */
    override suspend fun deleteTransaction(id: String) {
        failWritesWith?.let { throw it }
        val group = state.value.firstOrNull { it.id == id }?.transferGroupId
        state.value = state.value.filter { it.id != id && (group == null || it.transferGroupId != group) }
    }

    override suspend fun recordTransfer(outLeg: Transaction, inLeg: Transaction, fee: Transaction?) {
        failWritesWith?.let { throw it }
        state.value = state.value + listOfNotNull(outLeg, inLeg, fee)
    }
}

/** An in-memory category repository for tests. */
class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {

    private val state = MutableStateFlow(initial)

    val categories: List<Category> get() = state.value

    override fun observeCategories(): Flow<List<Category>> = state

    override suspend fun getCategory(id: String): Category? = state.value.firstOrNull { it.id == id }

    override suspend fun ensureDefaultCategories() {
        val known = state.value.map { it.id }.toSet()
        val missing = DefaultCategories.definitions
            .filter { it.id !in known }
            .map {
                Category(
                    id = it.id,
                    name = it.name,
                    localizedNameKey = it.localizationKey,
                    type = it.type,
                    isSystem = true
                )
            }
        state.value = state.value + missing
    }

    override suspend fun createCategory(category: Category) {
        state.value = state.value + category
    }

    override suspend fun updateCategory(category: Category) {
        state.value = state.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun deleteCategory(id: String): CategoryDeleteResult = CategoryDeleteResult.NOT_FOUND
}
