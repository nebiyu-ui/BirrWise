package et.birrwise.app.domain

import java.time.LocalDate
import java.time.ZoneId

enum class AccountFormError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    NAME_TAKEN,
    PROVIDER_TOO_LONG,
    NOTES_TOO_LONG,
    AMOUNT_INVALID
}

/** What the person typed into the account form. */
data class AccountFormInput(
    val name: String,
    val provider: String,
    val type: AccountType,
    val currencyCode: String,
    val openingBalanceText: String,
    /** The day the opening balance is for. Null means every transaction counts, whatever its date. */
    val openingDate: LocalDate?,
    val notes: String
)

sealed interface AccountFormResult {
    data class Valid(val account: Account) : AccountFormResult
    data class Invalid(val errors: Set<AccountFormError>) : AccountFormResult
}

object AccountFormValidator {

    const val MAX_NAME_LENGTH = 60
    const val MAX_PROVIDER_LENGTH = 60
    const val MAX_NOTES_LENGTH = 500

    /**
     * Checks the form and builds the account. An empty opening balance means zero. The opening date
     * becomes the start of that day in [zone], so transactions on that day are counted.
     * [otherAccountNames] are the names of the person's other accounts, which must not be reused.
     */
    fun validate(
        id: String,
        input: AccountFormInput,
        otherAccountNames: Collection<String>,
        zone: ZoneId,
        archived: Boolean = false
    ): AccountFormResult {
        val errors = mutableSetOf<AccountFormError>()

        val name = input.name.trim()
        when {
            name.isEmpty() -> errors += AccountFormError.NAME_REQUIRED
            name.length > MAX_NAME_LENGTH -> errors += AccountFormError.NAME_TOO_LONG
            otherAccountNames.any { it.trim().equals(name, ignoreCase = true) } ->
                errors += AccountFormError.NAME_TAKEN
        }

        val provider = input.provider.trim()
        if (provider.length > MAX_PROVIDER_LENGTH) errors += AccountFormError.PROVIDER_TOO_LONG

        val notes = input.notes.trim()
        if (notes.length > MAX_NOTES_LENGTH) errors += AccountFormError.NOTES_TOO_LONG

        val amountText = input.openingBalanceText.trim()
        val openingBalance: Money? =
            if (amountText.isEmpty()) {
                Money.zero(input.currencyCode)
            } else {
                MoneyParser.parse(amountText, input.currencyCode)
            }
        if (openingBalance == null) errors += AccountFormError.AMOUNT_INVALID

        if (errors.isNotEmpty() || openingBalance == null) return AccountFormResult.Invalid(errors)

        return AccountFormResult.Valid(
            Account(
                id = id,
                name = name,
                currencyCode = input.currencyCode,
                openingBalance = openingBalance,
                openingBalanceDate = input.openingDate?.atStartOfDay(zone)?.toInstant(),
                provider = provider.ifEmpty { null },
                type = input.type,
                archived = archived,
                notes = notes.ifEmpty { null }
            )
        )
    }
}
