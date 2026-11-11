package et.birrwise.app.domain

import et.birrwise.app.domain.repository.TransactionsSnapshot
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId

/** Everything the Home screen shows for one month. All figures are for active (not archived) accounts. */
data class HomeOverview(
    val month: YearMonth,
    val period: ReportingPeriod,
    /** Tracked balance of all active accounts, or null when there are none. */
    val totalBalance: Money?,
    /** Confirmed income, expenses, and net cash flow for the month. Transfers are not counted. */
    val summary: PeriodSummary,
    val accounts: List<AccountSummary>,
    /** The latest transactions of all active accounts, newest first. */
    val recent: List<TransactionListItem>,
    /** Transactions marked "needs review" on active accounts, whatever their date. */
    val pendingReviewCount: Int,
    /** Transfer entries on active accounts that have no matching other side. */
    val unpairedTransferLegs: Int,
    val unreadableTransactions: Int,
    val hasAccounts: Boolean,
    val hasAnyTransactions: Boolean
)

object HomeOverviewBuilder {

    const val RECENT_COUNT = 5

    fun build(
        accounts: List<Account>,
        categories: List<Category>,
        snapshot: TransactionsSnapshot,
        month: YearMonth,
        zone: ZoneId
    ): HomeOverview {
        val active = accounts.filter { !it.archived }
        val activeIds = active.map { it.id }.toSet()
        val activeTransactions = snapshot.transactions.filter { it.accountId in activeIds }

        val period = ReportingPeriods.month(month, zone)
        val summary = FinanceEngine.periodSummary(
            currencyCode = DEFAULT_CURRENCY_CODE,
            transactions = snapshot.transactions,
            period = period,
            accountIds = activeIds
        )

        val summaries = AccountSummaries.build(active, snapshot.transactions)
        val totalBalance = AccountSummaries.totalsByCurrency(summaries)
            .firstOrNull { it.currencyCode == DEFAULT_CURRENCY_CODE }

        val unpaired = FinanceEngine.unpairedTransferLegs(snapshot.transactions)

        return HomeOverview(
            month = month,
            period = period,
            totalBalance = totalBalance,
            summary = summary,
            accounts = summaries,
            recent = TransactionList.build(activeTransactions, active, categories).take(RECENT_COUNT),
            pendingReviewCount = activeTransactions.count { it.status == TransactionStatus.PENDING_REVIEW },
            unpairedTransferLegs = activeTransactions.count { it.id in unpaired },
            unreadableTransactions = snapshot.unreadableCount,
            hasAccounts = active.isNotEmpty(),
            hasAnyTransactions = activeTransactions.isNotEmpty()
        )
    }
}

/** Moving between months on the Home screen. The future is not offered. */
object HomeMonths {

    fun current(clock: Clock): YearMonth = YearMonth.now(clock)

    fun previous(month: YearMonth): YearMonth = month.minusMonths(1)

    fun canGoNext(month: YearMonth, current: YearMonth): Boolean = month.isBefore(current)

    fun next(month: YearMonth, current: YearMonth): YearMonth =
        if (canGoNext(month, current)) month.plusMonths(1) else month
}
