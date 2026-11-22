package et.birrwise.app.ui.budgets

import et.birrwise.app.R
import et.birrwise.app.domain.BudgetFormError

internal fun BudgetFormError.messageRes(): Int = when (this) {
    BudgetFormError.CATEGORY_REQUIRED -> R.string.error_budget_category_required
    BudgetFormError.CATEGORY_NOT_EXPENSE -> R.string.error_budget_category_not_expense
    BudgetFormError.CATEGORY_HAS_BUDGET -> R.string.error_budget_category_has_budget
    BudgetFormError.LIMIT_REQUIRED -> R.string.error_budget_limit_required
    BudgetFormError.LIMIT_INVALID -> R.string.error_amount_invalid
    BudgetFormError.LIMIT_ZERO -> R.string.error_amount_zero
    BudgetFormError.THRESHOLD_INVALID -> R.string.error_budget_threshold_invalid
}
