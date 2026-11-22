package et.birrwise.app.domain

import et.birrwise.app.domain.TransactionType.EXPENSE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class BudgetHistoryTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")
    private fun start(month: YearMonth) = BudgetRules.monthStart(month, addis)
    private val accounts = listOf(account("A1"), account("A9").copy(archived = true))

    private fun spend(id: String, birr: Long, iso: String, accountId: String = "A1") =
        tx(id, accountId, EXPENSE, birr * 100, occurredAt = at(iso), categoryId = "food")

    @Test
    fun eachMonthUsesTheLimitThatAppliedThenNewestFirst() {
        val versions = listOf(
            Budget("b1", "food", etb(4000), 80, start(YearMonth.of(2026, 10)), start(YearMonth.of(2026, 11)), true),
            Budget("b2", "food", etb(5000), 80, start(YearMonth.of(2026, 11)), null, true)
        )
        val history = BudgetHistory.build(
            versions,
            accounts,
            listOf(
                spend("o", 1000, "2026-10-12T09:00:00Z"),
                spend("n", 2500, "2026-11-12T09:00:00Z"),
                spend("archived", 999, "2026-11-12T09:00:00Z", accountId = "A9")
            ),
            YearMonth.of(2026, 11),
            addis
        )

        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), history.map { it.month })
        assertEquals(etb(5000), history[0].progress.limit)
        assertEquals(etb(2500), history[0].progress.spent)
        assertEquals(50, history[0].progress.percentUsed)
        assertEquals(etb(4000), history[1].progress.limit)
        assertEquals(etb(1000), history[1].progress.spent)
        assertEquals(25, history[1].progress.percentUsed)
    }

    @Test
    fun monthsWhilePausedAreLeftOutNotShownAsZero() {
        val versions = listOf(
            Budget("b1", "food", etb(4000), 80, start(YearMonth.of(2026, 10)), start(YearMonth.of(2026, 12)), true),
            Budget("b2", "food", etb(4000), 80, start(YearMonth.of(2027, 2)), null, true)
        )
        val months = BudgetHistory.build(versions, accounts, emptyList(), YearMonth.of(2027, 2), addis).map { it.month }
        assertEquals(
            listOf(YearMonth.of(2027, 2), YearMonth.of(2026, 11), YearMonth.of(2026, 10)),
            months
        )
    }

    @Test
    fun historyIsLimitedToTwoYears() {
        val versions = listOf(Budget("b1", "food", etb(100), 80, start(YearMonth.of(2020, 1)), null, true))
        val history = BudgetHistory.build(versions, accounts, emptyList(), YearMonth.of(2026, 11), addis)
        assertEquals(BudgetHistory.MAX_MONTHS, history.size)
        assertEquals(YearMonth.of(2026, 11), history.first().month)
    }

    @Test
    fun noVersionsMeansNoHistory() {
        assertTrue(BudgetHistory.build(emptyList(), accounts, emptyList(), YearMonth.of(2026, 11), addis).isEmpty())
    }
}
