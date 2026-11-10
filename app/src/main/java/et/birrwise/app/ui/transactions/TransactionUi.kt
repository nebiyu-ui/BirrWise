package et.birrwise.app.ui.transactions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import et.birrwise.app.R
import et.birrwise.app.domain.EntryKind
import et.birrwise.app.domain.TransactionFormError
import et.birrwise.app.domain.TransactionType

internal fun TransactionType.labelRes(): Int = when (this) {
    TransactionType.INCOME -> R.string.type_income
    TransactionType.EXPENSE -> R.string.type_expense
    TransactionType.FEE -> R.string.type_fee
    TransactionType.REFUND -> R.string.type_refund
    TransactionType.TRANSFER_IN -> R.string.type_transfer_in
    TransactionType.TRANSFER_OUT -> R.string.type_transfer_out
    TransactionType.REVERSAL -> R.string.type_reversal
}

internal fun TransactionType.icon(): ImageVector = when (this) {
    TransactionType.INCOME -> Icons.Filled.ArrowDownward
    TransactionType.EXPENSE -> Icons.Filled.ArrowUpward
    TransactionType.FEE -> Icons.Filled.MoneyOff
    TransactionType.REFUND -> Icons.Filled.Replay
    TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT -> Icons.Filled.SwapHoriz
    TransactionType.REVERSAL -> Icons.Filled.Refresh
}

/** The sign shown before an amount: money coming in is "+", money going out is "−". */
internal fun TransactionType.signSymbol(): String = when (this) {
    TransactionType.INCOME, TransactionType.REFUND, TransactionType.TRANSFER_IN -> "+"
    TransactionType.EXPENSE, TransactionType.FEE, TransactionType.TRANSFER_OUT -> "\u2212"
    TransactionType.REVERSAL -> ""
}

internal fun EntryKind.labelRes(): Int = when (this) {
    EntryKind.INCOME -> R.string.type_income
    EntryKind.EXPENSE -> R.string.type_expense
    EntryKind.FEE -> R.string.type_fee
    EntryKind.REFUND -> R.string.type_refund
    EntryKind.TRANSFER -> R.string.type_transfer
}

internal fun TransactionFormError.messageRes(): Int = when (this) {
    TransactionFormError.ACCOUNT_REQUIRED -> R.string.error_account_required
    TransactionFormError.TO_ACCOUNT_REQUIRED -> R.string.error_to_account_required
    TransactionFormError.SAME_ACCOUNT -> R.string.error_same_account
    TransactionFormError.CURRENCY_MISMATCH -> R.string.error_currency_mismatch
    TransactionFormError.AMOUNT_REQUIRED -> R.string.error_amount_required
    TransactionFormError.AMOUNT_INVALID -> R.string.error_amount_invalid
    TransactionFormError.AMOUNT_ZERO -> R.string.error_amount_zero
    TransactionFormError.FEE_INVALID -> R.string.error_fee_invalid
    TransactionFormError.CATEGORY_MISMATCH -> R.string.error_category_mismatch
    TransactionFormError.DESCRIPTION_TOO_LONG -> R.string.error_description_too_long
    TransactionFormError.COUNTERPARTY_TOO_LONG -> R.string.error_counterparty_too_long
    TransactionFormError.NOTES_TOO_LONG -> R.string.error_notes_too_long
}
