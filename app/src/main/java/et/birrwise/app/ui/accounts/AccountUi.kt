package et.birrwise.app.ui.accounts

import android.content.Context
import et.birrwise.app.BirrWiseApplication
import et.birrwise.app.AppContainer
import et.birrwise.app.R
import et.birrwise.app.domain.AccountFormError
import et.birrwise.app.domain.AccountType
import et.birrwise.app.domain.Money
import et.birrwise.app.domain.MoneyFormatter

internal fun AccountType.labelRes(): Int = when (this) {
    AccountType.BANK -> R.string.account_type_bank
    AccountType.MOBILE_WALLET -> R.string.account_type_mobile_wallet
    AccountType.CASH -> R.string.account_type_cash
    AccountType.SAVINGS -> R.string.account_type_savings
    AccountType.OTHER -> R.string.account_type_other
}

internal fun AccountFormError.messageRes(): Int = when (this) {
    AccountFormError.NAME_REQUIRED -> R.string.error_name_required
    AccountFormError.NAME_TOO_LONG -> R.string.error_name_too_long
    AccountFormError.NAME_TAKEN -> R.string.error_name_taken
    AccountFormError.PROVIDER_TOO_LONG -> R.string.error_provider_too_long
    AccountFormError.NOTES_TOO_LONG -> R.string.error_notes_too_long
    AccountFormError.AMOUNT_INVALID -> R.string.error_amount_invalid
}

internal fun Context.appContainer(): AppContainer =
    (applicationContext as BirrWiseApplication).container

/** A number that can be edited and parsed again, such as 1250.50 (no grouping commas). */
internal fun editableAmount(money: Money): String =
    MoneyFormatter.format(money, includeCurrency = false).replace(",", "")
