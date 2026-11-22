package et.birrwise.app.ui.budgets

import et.birrwise.app.domain.Budget
import et.birrwise.app.domain.BudgetChange
import et.birrwise.app.domain.BudgetRules
import et.birrwise.app.domain.BudgetStatus
import et.birrwise.app.domain.repository.BudgetRepository
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.YearMonth
import java.util.UUID

/** Pausing, resuming, and deleting a category's budget. Each returns true when it worked. */
class BudgetLifecycle(
    private val budgets: BudgetRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemDefaultZone()
) {

    suspend fun pause(categoryId: String): Boolean = applyChanges(categoryId) { versions ->
        val current = BudgetRules.current(versions)
        if (current != null && current.status == BudgetStatus.ONGOING) {
            BudgetRules.pause(current, YearMonth.now(clock), clock.zone)
        } else {
            emptyList()
        }
    }

    suspend fun resume(categoryId: String): Boolean = applyChanges(categoryId) { versions ->
        val current = BudgetRules.current(versions)
        if (current != null && current.status == BudgetStatus.PAUSED) {
            BudgetRules.resume(current, newId(), YearMonth.now(clock), clock.zone)
        } else {
            emptyList()
        }
    }

    /** Deletes the budget and its history for the category. */
    suspend fun delete(categoryId: String): Boolean =
        try {
            budgets.deleteForCategory(categoryId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    private suspend fun applyChanges(
        categoryId: String,
        build: (List<Budget>) -> List<BudgetChange>
    ): Boolean =
        try {
            budgets.apply(build(budgets.getBudgetsForCategory(categoryId)))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
}
