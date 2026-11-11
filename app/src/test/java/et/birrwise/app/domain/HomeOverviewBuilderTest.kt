package et.birrwise.app.domain

import et.birrwise.app.domain.TransactionStatus.PENDING_REVIEW
import et.birrwise.app.domain.TransactionType.EXPENSE
import et.birrwise.app.domain.TransactionType.INCOME
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

class HomeOverviewBuilderTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")
    private val november: YearMonth = YearMonth.of(2026, 11)

    private val a1 = account("A1", openingBirr = 1000)
    private val a2 = account("A2", openingBirr = 500)
    private val archived = account("A3", openingBirr = 200).copy(archived = true)

    private val transactions = listOf(
        tx("i1", "A1", INCOME, 100_000L, occurredAt = at("2026-11-03T09:00:00Z")),
        tx("e1", "A1", EXPENSE, 25_000L, occurredAt = at("2026-11-04T09:00:00Z")),
        tx("e2", "A2", EXPENSE, 10_000L, occurredAt = at("2026-10-30T09:00:00Z")),
        tx("out-g", "A1", TRANSFER_OUT, 30_000L, occurredAt = at("2026-11-05T09:00:00Z"), transferGroupId = "g"),
        tx("in-g", "A2", TRANSFER_IN, 30_000L, occurredAt = at("2026-11-05T09:00:00Z"), transferGroupId = "g"),
        tx("p1", "A1", INCOME, 50_000L, occurredAt = at("2026-11-06T09:00:00Z"), status = PENDING_REVIEW),
        tx("x1", "A3", EXPENSE, 5_000L, occurredAt = at("2026-11-04T09:00:00Z"))
    )

    private fun build(
        month: YearMonth = november,
        accounts: List<Account> = listOf(a1, a2, archived),
        entries: List<Transaction> = transactions,
        unreadable: Int = 0
    ): HomeOverview = HomeOverviewBuilder.build(
        accounts,
        emptyList(),
        TransactionsSnapshot(entries, unreadable),
        month,
        addis
    )

    @Test
    fun monthTotalsCountConfirmedEntriesOnActiveAccountsOnly() {
        val summary = build().summary
        assertEquals(etb(1000), summary.income)
        assertEquals(etb(250), summary.expenses)
        assertEquals(etb(750), summary.netCashFlow)
        // The transfer, the pending entry, and the archived account's expense are not in the totals.
        assertEquals(1, summary.pendingTransactionsExcluded)
    }

    @Test
    fun totalBalanceIsTheSumOfActiveAccounts() {
        // A1: 1,000 + 1,000 - 250 - 300 = 1,450. A2: 500 - 100 + 300 = 700. The archived account is left out.
        assertEquals(etb(2150), build().totalBalance)
    }

    @Test
    fun accountListHoldsOnlyActiveAccounts() {
        assertEquals(listOf("A1", "A2"), build().accounts.map { it.account.id })
    }

    @Test
    fun anotherMonthHasItsOwnTotalsButTheSameBalance() {
        val october = build(month = YearMonth.of(2026, 10))
        assertEquals(etb(0), october.summary.income)
        assertEquals(etb(100), october.summary.expenses)
        assertEquals(etb(-100), october.summary.netCashFlow)
        assertEquals(etb(2150), october.totalBalance)
    }

    @Test
    fun monthBoundariesFollowLocalTime() {
        // 21:30 UTC on 31 October is already 1 November in Addis Ababa.
        val entries = listOf(tx("b1", "A1", INCOME, 10_000L, occurredAt = at("2026-10-31T21:30:00Z")))
        assertEquals(etb(100), build(entries = entries).summary.income)
        assertEquals(etb(0), build(month = YearMonth.of(2026, 10), entries = entries).summary.income)
    }

    @Test
    fun recentListsTheLatestFiveNewestFirstFromActiveAccounts() {
        val recent = build().recent.map { it.transaction.id }
        // The pending entry is shown, the older October entry and the archived account's entry are not.
        assertEquals(listOf("p1", "in-g", "out-g", "e1", "i1"), recent)
    }

    @Test
    fun warningsAreCounted() {
        val base = build(unreadable = 2)
        assertEquals(1, base.pendingReviewCount)
        assertEquals(0, base.unpairedTransferLegs)
        assertEquals(2, base.unreadableTransactions)

        val withLoneLegs = build(
            entries = transactions + listOf(
                tx("lonely", "A1", TRANSFER_OUT, 1_000L, occurredAt = at("2026-11-02T09:00:00Z")),
                tx("lonely-archived", "A3", TRANSFER_OUT, 1_000L, occurredAt = at("2026-11-02T09:00:00Z"))
            )
        )
        // The lone leg on the archived account is not counted.
        assertEquals(1, withLoneLegs.unpairedTransferLegs)
    }

    @Test
    fun noAccountsMeansNothingToShow() {
        val overview = build(accounts = emptyList(), entries = emptyList())
        assertFalse(overview.hasAccounts)
        assertFalse(overview.hasAnyTransactions)
        assertNull(overview.totalBalance)
        assertTrue(overview.recent.isEmpty())
        assertEquals(etb(0), overview.summary.income)
    }

    @Test
    fun onlyArchivedAccountsCountAsNoAccounts() {
        val overview = build(accounts = listOf(archived))
        assertFalse(overview.hasAccounts)
        assertNull(overview.totalBalance)
    }

    @Test
    fun accountsWithoutTransactionsShowTheirOpeningBalances() {
        val overview = build(entries = emptyList())
        assertTrue(overview.hasAccounts)
        assertFalse(overview.hasAnyTransactions)
        assertEquals(etb(1500), overview.totalBalance)
    }
}
