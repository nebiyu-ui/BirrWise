package et.birrwise.app.data

import android.database.sqlite.SQLiteException
import et.birrwise.app.domain.Budget
import et.birrwise.app.domain.BudgetChange
import et.birrwise.app.domain.BudgetRules
import et.birrwise.app.domain.BudgetStatus
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.CategoryType
import et.birrwise.app.domain.etb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class RoomBudgetRepositoryTest : DatabaseTestBase() {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")
    private val budgets by lazy { RoomBudgetRepository(db, clock) }
    private val categories by lazy { RoomCategoryRepository(db, clock) }

    private fun budget(id: String = "b1", category: String = "sys_food_groceries") = Budget(
        id, category, etb(5000), 80,
        BudgetRules.monthStart(YearMonth.of(2026, 10), addis), null, true
    )

    @Test
    fun aBudgetRoundTripsThroughTheDatabase() = runBlocking<Unit> {
        categories.ensureDefaultCategories()
        val original = budget()
        budgets.apply(listOf(BudgetChange.Insert(original)))

        assertEquals(listOf(original), budgets.observeBudgets().first())
        assertEquals(listOf(original), budgets.getBudgetsForCategory("sys_food_groceries"))
    }

    @Test
    fun updatingAndInsertingTogetherIsOneStep() = runBlocking<Unit> {
        categories.ensureDefaultCategories()
        val original = budget()
        budgets.apply(listOf(BudgetChange.Insert(original)))

        val closed = original.copy(periodEnd = BudgetRules.monthStart(YearMonth.of(2026, 11), addis))
        val next = original.copy(id = "b2", limit = etb(6000), periodStart = closed.periodEnd!!, periodEnd = null)
        budgets.apply(listOf(BudgetChange.Update(closed), BudgetChange.Insert(next)))

        val saved = budgets.getBudgetsForCategory("sys_food_groceries")
        assertEquals(2, saved.size)
        assertEquals(BudgetStatus.ENDED, saved.first { it.id == "b1" }.status)
        assertEquals(etb(6000), BudgetRules.current(saved)!!.limit)
    }

    @Test
    fun ifOneChangeFailsNoneIsApplied() = runBlocking<Unit> {
        categories.ensureDefaultCategories()
        val original = budget()
        budgets.apply(listOf(BudgetChange.Insert(original)))

        val closed = original.copy(periodEnd = BudgetRules.monthStart(YearMonth.of(2026, 11), addis))
        val badInsert = original.copy(id = "b2", categoryId = "no-such-category", periodEnd = null)
        assertThrows(SQLiteException::class.java) {
            runBlocking { budgets.apply(listOf(BudgetChange.Update(closed), BudgetChange.Insert(badInsert))) }
        }
        // The update was rolled back too.
        assertEquals(listOf(original), budgets.observeBudgets().first())
    }

    @Test
    fun updatingAMissingBudgetIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { budgets.apply(listOf(BudgetChange.Update(budget("ghost")))) }
        }
    }

    @Test
    fun deletingACategorysBudgetRemovesAllItsVersionsAndNothingElse() = runBlocking<Unit> {
        categories.ensureDefaultCategories()
        budgets.apply(
            listOf(
                BudgetChange.Insert(budget("b1", "sys_food_groceries")),
                BudgetChange.Insert(budget("b2", "sys_food_groceries")),
                BudgetChange.Insert(budget("b3", "sys_fuel"))
            )
        )
        budgets.deleteForCategory("sys_food_groceries")
        assertEquals(listOf("b3"), budgets.observeBudgets().first().map { it.id })
    }

    @Test
    fun deletingACategoryDeletesItsBudgets() = runBlocking<Unit> {
        categories.createCategory(Category(id = "custom", name = "Custom", type = CategoryType.EXPENSE))
        budgets.apply(listOf(BudgetChange.Insert(budget("b1", "custom"))))

        db.categoryDao().deleteById("custom")

        assertTrue(budgets.observeBudgets().first().isEmpty())
    }
}
