package et.birrwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class BudgetRulesTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)
    private val december = YearMonth.of(2026, 12)

    private fun start(month: YearMonth) = BudgetRules.monthStart(month, addis)

    private fun budget(
        id: String = "b1",
        startMonth: YearMonth = october,
        endMonth: YearMonth? = null,
        active: Boolean = true,
        limit: Money = etb(5000),
        threshold: Int = 80
    ) = Budget(id, "food", limit, threshold, start(startMonth), endMonth?.let { start(it) }, active)

    @Test
    fun aNewBudgetStartsInTheCurrentMonthAndIsOngoing() {
        val created = BudgetRules.create("b1", "food", etb(5000), 80, november, addis)
        assertEquals(start(november), created.periodStart)
        assertNull(created.periodEnd)
        assertEquals(BudgetStatus.ONGOING, created.status)
        // 1 November in Addis Ababa begins at 21:00 UTC on 31 October.
        assertEquals(at("2026-10-31T21:00:00Z"), created.periodStart)
    }

    @Test
    fun statusFollowsTheFlags() {
        assertEquals(BudgetStatus.ONGOING, budget().status)
        assertEquals(BudgetStatus.ENDED, budget(endMonth = november).status)
        assertEquals(BudgetStatus.PAUSED, budget(endMonth = november, active = false).status)
    }

    @Test
    fun appliesToMonthsFromTheStartUpToButNotIncludingTheEnd() {
        val ended = budget(startMonth = october, endMonth = december)
        assertFalse(BudgetRules.appliesToMonth(ended, YearMonth.of(2026, 9), addis))
        assertTrue(BudgetRules.appliesToMonth(ended, october, addis))
        assertTrue(BudgetRules.appliesToMonth(ended, november, addis))
        assertFalse(BudgetRules.appliesToMonth(ended, december, addis))
        assertTrue(BudgetRules.appliesToMonth(budget(), YearMonth.of(2030, 1), addis))
    }

    @Test
    fun budgetsRejectInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) { budget(limit = etb(0)) }
        assertThrows(IllegalArgumentException::class.java) { budget(threshold = 0) }
        assertThrows(IllegalArgumentException::class.java) { budget(threshold = 101) }
        assertThrows(IllegalArgumentException::class.java) {
            Budget("b", "food", etb(10), 80, start(november), start(october), true)
        }
    }

    // ---- changing terms ------------------------------------------------------------------

    @Test
    fun unchangedTermsDoNothing() {
        assertTrue(BudgetRules.changeTerms(budget(), etb(5000), 80, "new", november, addis).isEmpty())
    }

    @Test
    fun changingABudgetStartedThisMonthUpdatesItInPlace() {
        val existing = budget(startMonth = november)
        val changes = BudgetRules.changeTerms(existing, etb(6000), 90, "new", november, addis)
        assertEquals(
            listOf<BudgetChange>(BudgetChange.Update(existing.copy(limit = etb(6000), alertThresholdPercent = 90))),
            changes
        )
    }

    @Test
    fun changingAnOlderBudgetStartsANewVersionSoHistoryKeepsTheOldLimit() {
        val existing = budget(startMonth = october)
        val changes = BudgetRules.changeTerms(existing, etb(6000), 90, "new-id", november, addis)

        assertEquals(2, changes.size)
        val closed = (changes[0] as BudgetChange.Update).budget
        assertEquals("b1", closed.id)
        assertEquals(etb(5000), closed.limit)
        assertEquals(start(november), closed.periodEnd)
        assertEquals(BudgetStatus.ENDED, closed.status)

        val created = (changes[1] as BudgetChange.Insert).budget
        assertEquals("new-id", created.id)
        assertEquals(etb(6000), created.limit)
        assertEquals(90, created.alertThresholdPercent)
        assertEquals(start(november), created.periodStart)
        assertNull(created.periodEnd)
        assertEquals(BudgetStatus.ONGOING, created.status)
    }

    @Test
    fun onlyAnOngoingBudgetCanBeChanged() {
        val paused = budget(endMonth = november, active = false)
        assertThrows(IllegalArgumentException::class.java) {
            BudgetRules.changeTerms(paused, etb(1), 80, "x", november, addis)
        }
    }

    // ---- pause and resume ----------------------------------------------------------------

    @Test
    fun pausingStopsTheBudgetFromTheCurrentMonth() {
        val changes = BudgetRules.pause(budget(startMonth = october), november, addis)
        val paused = (changes.single() as BudgetChange.Update).budget
        assertEquals(BudgetStatus.PAUSED, paused.status)
        assertEquals(start(november), paused.periodEnd)
        // Earlier months keep their history, the current month is no longer tracked.
        assertTrue(BudgetRules.appliesToMonth(paused, october, addis))
        assertFalse(BudgetRules.appliesToMonth(paused, november, addis))
    }

    @Test
    fun pausingABudgetCreatedThisMonthLeavesNothingTracked() {
        val paused = (BudgetRules.pause(budget(startMonth = november), november, addis).single() as BudgetChange.Update).budget
        assertEquals(paused.periodStart, paused.periodEnd)
        assertFalse(BudgetRules.appliesToMonth(paused, november, addis))
    }

    @Test
    fun resumingStartsANewVersionAndTurnsTheOldOneIntoHistory() {
        val paused = budget(startMonth = october, endMonth = november, active = false)
        val changes = BudgetRules.resume(paused, "new-id", YearMonth.of(2027, 2), addis)

        val history = (changes[0] as BudgetChange.Update).budget
        assertEquals("b1", history.id)
        assertEquals(BudgetStatus.ENDED, history.status)

        val resumed = (changes[1] as BudgetChange.Insert).budget
        assertEquals("new-id", resumed.id)
        assertEquals(BudgetStatus.ONGOING, resumed.status)
        assertEquals(start(YearMonth.of(2027, 2)), resumed.periodStart)
        assertEquals(paused.limit, resumed.limit)
        // The months while it was paused stay untracked.
        assertFalse(BudgetRules.appliesToMonth(history, december, addis))
        assertFalse(BudgetRules.appliesToMonth(resumed, december, addis))
    }

    @Test
    fun onlyAPausedBudgetCanBeResumedAndOnlyAnOngoingOneCanBePaused() {
        assertThrows(IllegalArgumentException::class.java) {
            BudgetRules.resume(budget(), "x", november, addis)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BudgetRules.pause(budget(endMonth = november, active = false), november, addis)
        }
    }

    @Test
    fun currentPrefersTheOngoingVersionThenThePausedOne() {
        val ended = budget(id = "old", endMonth = november)
        val ongoing = budget(id = "now", startMonth = november)
        val paused = budget(id = "paused", endMonth = november, active = false)
        assertEquals("now", BudgetRules.current(listOf(ended, ongoing))?.id)
        assertEquals("paused", BudgetRules.current(listOf(ended, paused))?.id)
        assertNull(BudgetRules.current(listOf(ended)))
        assertNull(BudgetRules.current(emptyList()))
    }
}
