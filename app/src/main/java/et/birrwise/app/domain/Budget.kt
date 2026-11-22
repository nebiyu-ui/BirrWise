package et.birrwise.app.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class BudgetStatus {
    /** Tracked every month from [Budget.periodStart] until it is paused. */
    ONGOING,

    /** Stopped. It keeps the history it already has and can be resumed. */
    PAUSED,

    /** An earlier version, replaced when the limit was changed. Only used for history. */
    ENDED
}

/**
 * A monthly spending limit for one expense category. It applies to every calendar month from
 * [periodStart] (the start of a month) up to, but not including, [periodEnd] (null means no end).
 * Changing a limit starts a new version, so earlier months keep the limit they had.
 */
data class Budget(
    val id: String,
    val categoryId: String,
    val limit: Money,
    val alertThresholdPercent: Int,
    val periodStart: Instant,
    val periodEnd: Instant?,
    val active: Boolean
) {
    init {
        require(id.isNotBlank()) { "Budget id must not be blank" }
        require(categoryId.isNotBlank()) { "Budget category must not be blank" }
        require(limit.isPositive) { "A budget limit must be greater than zero" }
        require(alertThresholdPercent in 1..100) { "Alert threshold must be between 1 and 100" }
        require(periodEnd == null || periodEnd >= periodStart) { "A budget cannot end before it starts" }
    }

    val status: BudgetStatus
        get() = when {
            !active -> BudgetStatus.PAUSED
            periodEnd == null -> BudgetStatus.ONGOING
            else -> BudgetStatus.ENDED
        }
}

sealed interface BudgetChange {
    data class Insert(val budget: Budget) : BudgetChange
    data class Update(val budget: Budget) : BudgetChange
}

object BudgetRules {

    const val DEFAULT_THRESHOLD_PERCENT = 80
    val THRESHOLD_CHOICES: List<Int> = listOf(50, 60, 70, 80, 90, 100)

    fun monthStart(month: YearMonth, zone: ZoneId): Instant =
        month.atDay(1).atStartOfDay(zone).toInstant()

    fun appliesToMonth(budget: Budget, month: YearMonth, zone: ZoneId): Boolean {
        val start = monthStart(month, zone)
        val end = budget.periodEnd
        return start >= budget.periodStart && (end == null || start < end)
    }

    /** The version of a category's budget that is in effect now: the ongoing one, else the paused one. */
    fun current(versions: List<Budget>): Budget? =
        versions.firstOrNull { it.status == BudgetStatus.ONGOING }
            ?: versions.firstOrNull { it.status == BudgetStatus.PAUSED }

    /** A new budget starts in the current month. */
    fun create(
        id: String,
        categoryId: String,
        limit: Money,
        thresholdPercent: Int,
        currentMonth: YearMonth,
        zone: ZoneId
    ): Budget = Budget(
        id = id,
        categoryId = categoryId,
        limit = limit,
        alertThresholdPercent = thresholdPercent,
        periodStart = monthStart(currentMonth, zone),
        periodEnd = null,
        active = true
    )

    /**
     * New terms apply from the current month. If the budget started earlier, the old version is
     * closed at the start of this month and a new version begins, so earlier months keep the old
     * limit. If it started this month, it is simply updated. Nothing changes if the terms are equal.
     */
    fun changeTerms(
        existing: Budget,
        limit: Money,
        thresholdPercent: Int,
        newId: String,
        currentMonth: YearMonth,
        zone: ZoneId
    ): List<BudgetChange> {
        require(existing.status == BudgetStatus.ONGOING) { "Only an ongoing budget can be changed" }
        if (existing.limit == limit && existing.alertThresholdPercent == thresholdPercent) return emptyList()
        val start = monthStart(currentMonth, zone)
        return if (existing.periodStart >= start) {
            listOf(
                BudgetChange.Update(existing.copy(limit = limit, alertThresholdPercent = thresholdPercent))
            )
        } else {
            listOf(
                BudgetChange.Update(existing.copy(periodEnd = start)),
                BudgetChange.Insert(
                    existing.copy(
                        id = newId,
                        limit = limit,
                        alertThresholdPercent = thresholdPercent,
                        periodStart = start,
                        periodEnd = null
                    )
                )
            )
        }
    }

    /** Pausing stops the budget from the current month on. Earlier months keep their history. */
    fun pause(existing: Budget, currentMonth: YearMonth, zone: ZoneId): List<BudgetChange> {
        require(existing.status == BudgetStatus.ONGOING) { "Only an ongoing budget can be paused" }
        val start = monthStart(currentMonth, zone)
        val end = if (existing.periodStart > start) existing.periodStart else start
        return listOf(BudgetChange.Update(existing.copy(active = false, periodEnd = end)))
    }

    /** Resuming starts a new version in the current month. The months while paused stay untracked. */
    fun resume(
        paused: Budget,
        newId: String,
        currentMonth: YearMonth,
        zone: ZoneId
    ): List<BudgetChange> {
        require(paused.status == BudgetStatus.PAUSED) { "Only a paused budget can be resumed" }
        val start = monthStart(currentMonth, zone)
        return listOf(
            BudgetChange.Update(paused.copy(active = true)),
            BudgetChange.Insert(
                paused.copy(id = newId, active = true, periodStart = start, periodEnd = null)
            )
        )
    }
}

enum class BudgetFormError {
    CATEGORY_REQUIRED,
    CATEGORY_NOT_EXPENSE,
    CATEGORY_HAS_BUDGET,
    LIMIT_REQUIRED,
    LIMIT_INVALID,
    LIMIT_ZERO,
    THRESHOLD_INVALID
}

data class BudgetFormInput(
    val categoryId: String?,
    val limitText: String,
    val thresholdPercent: Int
)

sealed interface BudgetFormResult {
    data class Valid(val categoryId: String, val limit: Money, val thresholdPercent: Int) : BudgetFormResult
    data class Invalid(val errors: Set<BudgetFormError>) : BudgetFormResult
}

object BudgetFormValidator {

    /**
     * Budgets are for expense categories only, and each category has one budget (ongoing or paused).
     * [categoriesWithBudget] holds the categories that already have one.
     */
    fun validate(
        input: BudgetFormInput,
        categories: Map<String, Category>,
        categoriesWithBudget: Set<String>,
        isEditing: Boolean
    ): BudgetFormResult {
        val errors = mutableSetOf<BudgetFormError>()

        val category = input.categoryId?.let { categories[it] }
        if (category == null) {
            errors += BudgetFormError.CATEGORY_REQUIRED
        } else if (category.type != CategoryType.EXPENSE) {
            errors += BudgetFormError.CATEGORY_NOT_EXPENSE
        } else if (!isEditing && category.id in categoriesWithBudget) {
            errors += BudgetFormError.CATEGORY_HAS_BUDGET
        }

        val text = input.limitText.trim()
        var limit: Money? = null
        if (text.isEmpty()) {
            errors += BudgetFormError.LIMIT_REQUIRED
        } else {
            val parsed = MoneyParser.parse(text, DEFAULT_CURRENCY_CODE)
            if (parsed == null) {
                errors += BudgetFormError.LIMIT_INVALID
            } else if (!parsed.isPositive) {
                errors += BudgetFormError.LIMIT_ZERO
            } else {
                limit = parsed
            }
        }

        if (input.thresholdPercent !in 1..100) errors += BudgetFormError.THRESHOLD_INVALID

        if (errors.isNotEmpty()) return BudgetFormResult.Invalid(errors)
        val checkedCategory = category ?: return BudgetFormResult.Invalid(errors)
        val checkedLimit = limit ?: return BudgetFormResult.Invalid(errors)
        return BudgetFormResult.Valid(checkedCategory.id, checkedLimit, input.thresholdPercent)
    }
}
