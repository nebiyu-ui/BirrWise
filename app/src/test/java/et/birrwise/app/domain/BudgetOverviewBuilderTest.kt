package et.birrwise.app.domain

import et.birrwise.app.domain.TransactionStatus.PENDING_REVIEW
import et.birrwise.app.domain.TransactionType.EXPENSE
import et.birrwise.app.domain.TransactionType.REFUND
import et.birrwise.app.domain.TransactionType.TRANSFER_IN
import et.birrwise.app.domain.TransactionType.TRANSFER_OUT
import et.birrwise.app.domain.repository.TransactionsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class BudgetOverviewBuilderTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)

    private fun start(month: YearMonth) = BudgetRules.monthStart(month, addis)

    private val accounts = listOf(
        account("A1"),
        account("A2"),
        account("A3").copy(archived = true)
    )
    private val categories = listOf(
        Category(id = "food", name = "Food", type = CategoryType.EXPENSE),
        Category(id = "fuel", name = "Fuel", type = CategoryType.EXPENSE),
        Category(id = "fun", name = "Fun", type = CategoryType.EXPENSE),
        Category(id = "pay", name = "Pay", type = CategoryType.INCOME)
    )

    private val food = Budget("b-food", "food", etb(5000), 80, start(october), null, true)
    private val fuel = Budget("b-fuel", "fuel", etb(1000), 80, start(november), null, true)
    private val pausedFun = Budget("b-fun", "fun", etb(300), 80, start(october), start(november), false)

    private val day = at("2026-11-10T09:00:00Z")

    private val transactions = listOf(
        tx("f1", "A1", EXPENSE, 300_000L, occurredAt = day, categoryId = "food"),
        tx("fr", "A1", REFUND, 50_000L, occurredAt = day, categoryId = "food"),
        tx("u1", "A1", EXPENSE, 90_000L, occurredAt = day, categoryId = "fuel"),
        tx("un", "A1", EXPENSE, 20_000L, occurredAt = day),
        tx("fun1", "A1", EXPENSE, 15_000L, occurredAt = day, categoryId = "fun"),
        tx("pend", "A1", EXPENSE, 40_000L, occurredAt = day, categoryId = "food", status = PENDING_REVIEW),
        tx("arch", "A3", EXPENSE, 99_900L, occurredAt = day, categoryId = "food"),
        tx("out", "A1", TRANSFER_OUT, 70_000L, occurredAt = day, transferGroupId = "g"),
        tx("in", "A2", TRANSFER_IN, 70_000L, occurredAt = day, transferGroupId = "g"),
        tx("f-oct", "A1", EXPENSE, 100_000L, occurredAt = at("2026-10-12T09:00:00Z"), categoryId = "food")
    )

    private fun build(
        month: YearMonth = november,
        budgets: List<Budget> = listOf(food, fuel, pausedFun),
        entries: List<Transaction> = transactions,
        unreadable: Int = 0
    ): BudgetOverview = BudgetOverviewBuilder.build(
        budgets, categories, accounts, TransactionsSnapshot(entries, unreadable), month, addis
    )

    @Test
    fun eachBudgetShowsItsCategoryNetSpendingForTheMonth() {
        val overview = build()
        assertEquals(listOf("Food", "Fuel"), overview.lines.map { it.categoryName })

        val foodLine = overview.lines[0].progress
        // 3,000 spent minus a 500 refund. Pending, archived-account, and transfer entries are not counted.
        assertEquals(etb(2500), foodLine.spent)
        assertEquals(50, foodLine.percentUsed)
        assertEquals(etb(2500), foodLine.remaining)
        assertFalse(foodLine.thresholdReached)

        val fuelLine = overview.lines[1].progress
        assertEquals(etb(900), fuelLine.spent)
        assertEquals(90, fuelLine.percentUsed)
        assertTrue(fuelLine.thresholdReached)
        assertFalse(fuelLine.isOverBudget)
    }

    @Test
    fun theTotalAddsUpAllBudgetsOfTheMonth() {
        val total = build().total!!
        assertEquals(etb(6000), total.limit)
        assertEquals(etb(3400), total.spent)
        assertEquals(etb(2600), total.remaining)
        assertEquals(56, total.percentUsed)
    }

    @Test
    fun spendingOutsideAnyBudgetIsReportedSeparately() {
        // 200 without a category and 150 in a category whose budget is paused.
        assertEquals(etb(350), build().unbudgetedSpending)
    }

    @Test
    fun pendingEntriesAreNotCountedButAreMentioned() {
        val overview = build(unreadable = 2)
        assertEquals(1, overview.pendingExcluded)
        assertEquals(2, overview.unreadableTransactions)
    }

    @Test
    fun pausedBudgetsAreListedSeparately() {
        val overview = build()
        assertEquals(listOf("Fun"), overview.paused.map { it.categoryName })
        assertTrue(overview.hasAnyBudget)
    }

    @Test
    fun anEarlierMonthUsesOnlyTheBudgetsThatWereInEffectThen() {
        val overview = build(month = october)
        // Fuel started in November. The fun budget was still running in October.
        assertEquals(listOf("Food", "Fun"), overview.lines.map { it.categoryName })
        assertEquals(20, overview.lines[0].progress.percentUsed)
        assertEquals(etb(1000), overview.lines[0].progress.spent)
    }

    @Test
    fun aMonthBeforeAnyBudgetHasNoLinesAndNoTotal() {
        val overview = build(month = september)
        assertTrue(overview.lines.isEmpty())
        assertNull(overview.total)
        assertTrue(overview.hasAnyBudget)
    }

    @Test
    fun overspendingIsShownAsAnAmountOverTheLimit() {
        val overspent = transactions + tx("more", "A1", EXPENSE, 30_000L, occurredAt = day, categoryId = "fuel")
        val fuelLine = build(entries = overspent).lines[1].progress
        assertEquals(etb(1200), fuelLine.spent)
        assertTrue(fuelLine.isOverBudget)
        assertEquals(etb(200), fuelLine.overspent)
        assertEquals(etb(0), fuelLine.remaining)
        assertEquals(120, fuelLine.percentUsed)
    }

    @Test
    fun aChangedLimitDoesNotRewriteEarlierMonths() {
        val oldVersion = Budget("b1", "food", etb(4000), 80, start(october), start(november), true)
        val newVersion = Budget("b2", "food", etb(5000), 80, start(november), null, true)
        val versions = listOf(oldVersion, newVersion)

        assertEquals(etb(4000), build(month = october, budgets = versions).lines.single().progress.limit)
        assertEquals(etb(5000), build(month = november, budgets = versions).lines.single().progress.limit)
    }

    @Test
    fun noBudgetsAtAll() {
        val overview = build(budgets = emptyList())
        assertFalse(overview.hasAnyBudget)
        assertTrue(overview.lines.isEmpty())
        assertNull(overview.total)
    }
}
