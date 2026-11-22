package et.birrwise.app.ui.budgets

import et.birrwise.app.domain.Budget
import et.birrwise.app.domain.BudgetChange
import et.birrwise.app.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An in-memory budget repository for tests. Failures can be switched on. */
class FakeBudgetRepository(initial: List<Budget> = emptyList()) : BudgetRepository {

    private val state = MutableStateFlow(initial)

    var failWritesWith: Exception? = null

    val budgets: List<Budget> get() = state.value

    override fun observeBudgets(): Flow<List<Budget>> = state

    override suspend fun getBudgetsForCategory(categoryId: String): List<Budget> =
        state.value.filter { it.categoryId == categoryId }

    override suspend fun apply(changes: List<BudgetChange>) {
        failWritesWith?.let { throw it }
        var updated = state.value
        for (change in changes) {
            updated = when (change) {
                is BudgetChange.Insert -> updated + change.budget
                is BudgetChange.Update -> {
                    require(updated.any { it.id == change.budget.id }) { "Budget does not exist" }
                    updated.map { if (it.id == change.budget.id) change.budget else it }
                }
            }
        }
        state.value = updated
    }

    override suspend fun deleteForCategory(categoryId: String) {
        failWritesWith?.let { throw it }
        state.value = state.value.filter { it.categoryId != categoryId }
    }
}
