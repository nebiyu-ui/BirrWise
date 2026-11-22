package et.birrwise.app.ui.budgets

import et.birrwise.app.domain.Budget
import et.birrwise.app.domain.BudgetRules
import et.birrwise.app.domain.BudgetStatus
import et.birrwise.app.domain.etb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class BudgetLifecycleTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-07T09:00:00Z"), addis)
    private val november = YearMonth.of(2026, 11)
    private val october = YearMonth.of(2026, 10)

    private fun ongoing(start: YearMonth = october) = Budget(
        "b1", "food", etb(5000), 80, BudgetRules.monthStart(start, addis), null, true
    )

    private fun lifecycle(repository: FakeBudgetRepository) =
        BudgetLifecycle(repository, newId = { "new-id" }, clock = clock)

    @Test
    fun pausingMakesTheBudgetPausedFromThisMonth() = runBlocking<Unit> {
        val repository = FakeBudgetRepository(listOf(ongoing()))
        assertTrue(lifecycle(repository).pause("food"))
        val paused = repository.budgets.single()
        assertEquals(BudgetStatus.PAUSED, paused.status)
        assertEquals(BudgetRules.monthStart(november, addis), paused.periodEnd)
    }

    @Test
    fun resumingStartsANewVersionThisMonth() = runBlocking<Unit> {
        val repository = FakeBudgetRepository(listOf(ongoing()))
        val lifecycle = lifecycle(repository)
        lifecycle.pause("food")
        assertTrue(lifecycle.resume("food"))

        assertEquals(2, repository.budgets.size)
        val current = BudgetRules.current(repository.budgets)!!
        assertEquals("new-id", current.id)
        assertEquals(BudgetStatus.ONGOING, current.status)
        assertEquals(BudgetRules.monthStart(november, addis), current.periodStart)
        assertEquals(BudgetStatus.ENDED, repository.budgets.first { it.id == "b1" }.status)
    }

    @Test
    fun pausingOrResumingWhenNotApplicableChangesNothing() = runBlocking<Unit> {
        val repository = FakeBudgetRepository(listOf(ongoing()))
        val lifecycle = lifecycle(repository)
        // Resuming an ongoing budget, and acting on a category with no budget, are harmless no-ops.
        assertTrue(lifecycle.resume("food"))
        assertTrue(lifecycle.pause("unknown"))
        assertEquals(listOf(ongoing()), repository.budgets)
    }

    @Test
    fun deletingRemovesEveryVersionOfTheCategory() = runBlocking<Unit> {
        val other = Budget("b2", "fuel", etb(100), 80, BudgetRules.monthStart(october, addis), null, true)
        val repository = FakeBudgetRepository(listOf(ongoing(), other))
        assertTrue(lifecycle(repository).delete("food"))
        assertEquals(listOf("b2"), repository.budgets.map { it.id })
    }

    @Test
    fun failuresReturnFalseInsteadOfCrashing() = runBlocking<Unit> {
        val repository = FakeBudgetRepository(listOf(ongoing()))
        repository.failWritesWith = RuntimeException("disk full")
        val lifecycle = lifecycle(repository)
        assertFalse(lifecycle.pause("food"))
        assertFalse(lifecycle.delete("food"))
        assertEquals(1, repository.budgets.size)
    }
}
