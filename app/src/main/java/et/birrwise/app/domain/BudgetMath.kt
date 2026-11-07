package et.birrwise.app.domain

import java.math.BigInteger

data class BudgetProgress(
    val limit: Money,
    val spent: Money,
    val remaining: Money,
    val overspent: Money,
    /** Whole percent of the limit used, rounded down. */
    val percentUsed: Int,
    val isOverBudget: Boolean,
    /** True when spending has reached the alert threshold, compared exactly (no rounding). */
    val thresholdReached: Boolean
)

object BudgetMath {

    /**
     * Net spending below zero (refunds larger than spending) counts as zero spent.
     * Example: limit 5,000, spent 4,000 gives 80 percent used and 1,000 remaining.
     */
    fun progress(limit: Money, spent: Money, alertThresholdPercent: Int = 80): BudgetProgress {
        require(limit.isPositive) { "A budget limit must be greater than zero" }
        require(limit.currencyCode == spent.currencyCode) { "Budget and spending currencies differ" }
        require(alertThresholdPercent in 1..100) { "Alert threshold must be between 1 and 100" }

        val zero = Money.zero(limit.currencyCode)
        val effectiveSpent = if (spent.isNegative) zero else spent
        val difference = limit - effectiveSpent
        val remaining = if (difference.isPositive) difference else zero
        val overspent = if (difference.isNegative) -difference else zero

        val hundred = BigInteger.valueOf(100L)
        val limitUnits = BigInteger.valueOf(limit.minorUnits)
        val spentTimes100 = BigInteger.valueOf(effectiveSpent.minorUnits).multiply(hundred)
        val percentUsed = spentTimes100
            .divide(limitUnits)
            .min(BigInteger.valueOf(Int.MAX_VALUE.toLong()))
            .toInt()
        val thresholdReached =
            spentTimes100 >= limitUnits.multiply(BigInteger.valueOf(alertThresholdPercent.toLong()))

        return BudgetProgress(
            limit = limit,
            spent = effectiveSpent,
            remaining = remaining,
            overspent = overspent,
            percentUsed = percentUsed,
            isOverBudget = difference.isNegative,
            thresholdReached = thresholdReached
        )
    }
}
