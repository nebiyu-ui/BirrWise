package et.birrwise.app.domain

import et.birrwise.app.domain.repository.TransactionsSnapshot
import java.time.YearMonth
import java.time.ZoneId

data class BudgetLine(
    val budget: Budget,
    val categoryName: String,
    val progress: BudgetProgress
)

data class PausedBudget(val budget: Budget, val categoryName: String)

/** Everything the Budgets tab shows for one month. Spending is for active accounts only. */
data class BudgetOverview(
    val month: YearMonth,
    /** Budgets in effect in this month, by category name. */
    val lines: List<BudgetLine>,
    /** Paused budgets, whatever the month. */
    val paused: List<PausedBudget>,
    /** All budgets of this month added together, or null when no budget applies. */
    val total: BudgetProgress?,
    /** Money spent in categories that have no budget, including entries without a category. */
    val unbudgetedSpending: Money,
    /** Entries waiting for review in this month. They are not counted in any budget. */
    val pendingExcluded: Int,
    val excludedFromReports: Int,
    val unreadableTransactions: Int,
    /** True once any budget exists, even if none applies to the chosen month. */
    val hasAnyBudget: Boolean
)

object BudgetOverviewBuilder {

    fun build(
        budgets: List<Budget>,
        categories: List<Category>,
        accounts: List<Account>,
        snapshot: TransactionsSnapshot,
        month: YearMonth,
        zone: ZoneId
    ): BudgetOverview {
        val activeIds = accounts.filter { !it.archived }.map { it.id }.toSet()
        val period = ReportingPeriods.month(month, zone)
        val zero = Money.zero(DEFAULT_CURRENCY_CODE)

        val spending = FinanceEngine.categorySpending(
            DEFAULT_CURRENCY_CODE, snapshot.transactions, period, activeIds
        )
        val summary = FinanceEngine.periodSummary(
            DEFAULT_CURRENCY_CODE, snapshot.transactions, period, activeIds
        )
        val spentByCategory = spending.filter { it.categoryId != null }.associate { it.categoryId to it.amount }
        val names = categories.associate { it.id to it.name }

        val applicable = budgets
            .filter { BudgetRules.appliesToMonth(it, month, zone) }
            .groupBy { it.categoryId }
            .mapNotNull { (_, versions) -> versions.maxByOrNull { it.periodStart } }

        val lines = applicable
            .map { budget ->
                val spent = spentByCategory[budget.categoryId] ?: zero
                BudgetLine(
                    budget = budget,
                    categoryName = names[budget.categoryId].orEmpty(),
                    progress = BudgetMath.progress(budget.limit, spent, budget.alertThresholdPercent)
                )
            }
            .sortedBy { it.categoryName.lowercase() }

        val total = if (lines.isEmpty()) {
            null
        } else {
            val totalLimit = lines.fold(zero) { sum, line -> sum + line.budget.limit }
            val totalSpent = lines.fold(zero) { sum, line -> sum + line.progress.spent }
            BudgetMath.progress(totalLimit, totalSpent)
        }

        val budgetedCategories = lines.map { it.budget.categoryId }.toSet()
        val unbudgeted = spending
            .filter { it.categoryId == null || it.categoryId !in budgetedCategories }
            .filter { it.amount.isPositive }
            .fold(zero) { sum, item -> sum + item.amount }

        val paused = budgets
            .filter { it.status == BudgetStatus.PAUSED }
            .map { PausedBudget(it, names[it.categoryId].orEmpty()) }
            .sortedBy { it.categoryName.lowercase() }

        return BudgetOverview(
            month = month,
            lines = lines,
            paused = paused,
            total = total,
            unbudgetedSpending = unbudgeted,
            pendingExcluded = summary.pendingTransactionsExcluded,
            excludedFromReports = summary.excludedFromReports,
            unreadableTransactions = snapshot.unreadableCount,
            hasAnyBudget = budgets.isNotEmpty()
        )
    }
}

data class BudgetMonth(val month: YearMonth, val progress: BudgetProgress)

object BudgetHistory {

    const val MAX_MONTHS = 24

    /**
     * How one category's budget did in each month, newest first, back to the month it started
     * (at most [MAX_MONTHS]). Months when it was paused are left out, not shown as zero.
     */
    fun build(
        versions: List<Budget>,
        accounts: List<Account>,
        transactions: List<Transaction>,
        currentMonth: YearMonth,
        zone: ZoneId
    ): List<BudgetMonth> {
        if (versions.isEmpty()) return emptyList()
        val categoryId = versions.first().categoryId
        val activeIds = accounts.filter { !it.archived }.map { it.id }.toSet()
        val earliest = versions.minOf { YearMonth.from(it.periodStart.atZone(zone)) }

        val result = mutableListOf<BudgetMonth>()
        var month = currentMonth
        var count = 0
        while (!month.isBefore(earliest) && count < MAX_MONTHS) {
            val budget = versions
                .filter { BudgetRules.appliesToMonth(it, month, zone) }
                .maxByOrNull { it.periodStart }
            if (budget != null) {
                val spent = FinanceEngine.categorySpending(
                    DEFAULT_CURRENCY_CODE,
                    transactions,
                    ReportingPeriods.month(month, zone),
                    activeIds
                ).firstOrNull { it.categoryId == categoryId }?.amount
                    ?: Money.zero(DEFAULT_CURRENCY_CODE)
                result += BudgetMonth(
                    month,
                    BudgetMath.progress(budget.limit, spent, budget.alertThresholdPercent)
                )
            }
            month = month.minusMonths(1)
            count++
        }
        return result
    }
}
