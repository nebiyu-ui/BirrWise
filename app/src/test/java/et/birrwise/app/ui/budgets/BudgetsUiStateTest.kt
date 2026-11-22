package et.birrwise.app.ui.budgets

import et.birrwise.app.domain.Budget
import et.birrwise.app.domain.BudgetRules
import et.birrwise.app.domain.BudgetStatus
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.CategoryType
import et.birrwise.app.domain.account
import et.birrwise.app.domain.etb
import et.birrwise.app.domain.repository.TransactionsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class BudgetsUiStateTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)

    private val categories = listOf(Category(id = "food", name = "Food", type = CategoryType.EXPENSE))
    private val budget = Budget("b1", "food", etb(5000), 80, BudgetRules.monthStart(october, addis), null, true)

    private fun state(month: YearMonth) = budgetsUiState(
        budgets = listOf(budget),
        categories = categories,
        accounts = listOf(account("A1")),
        snapshot = TransactionsSnapshot(emptyList(), 0),
        month = month,
        currentMonth = november,
        zone = addis
    ) as BudgetsUiState.Loaded

    @Test
    fun theCurrentMonthCannotGoForward() {
        assertFalse(state(november).canGoNext)
        assertTrue(state(october).canGoNext)
    }

    @Test
    fun aMonthWithABudgetHasALine() {
        val overview = state(november).overview
        assertEquals(1, overview.lines.size)
        assertEquals(etb(0), overview.lines.single().progress.spent)
    }

    @Test
    fun detailStateFindsTheCurrentVersionAndItsHistory() {
        val detail = budgetDetailUiState(
            categoryId = "food",
            budgets = listOf(budget),
            categories = categories,
            accounts = listOf(account("A1")),
            snapshot = TransactionsSnapshot(emptyList(), 0),
            currentMonth = november,
            zone = addis
        ) as BudgetDetailUiState.Loaded

        assertEquals("Food", detail.categoryName)
        assertEquals(BudgetStatus.ONGOING, detail.budget.status)
        // October and November.
        assertEquals(2, detail.history.size)
    }

    @Test
    fun detailStateSaysNotFoundForACategoryWithoutABudget() {
        val detail = budgetDetailUiState(
            "fuel", listOf(budget), categories, listOf(account("A1")),
            TransactionsSnapshot(emptyList(), 0), november, addis
        )
        assertEquals(BudgetDetailUiState.NotFound, detail)
    }
}
