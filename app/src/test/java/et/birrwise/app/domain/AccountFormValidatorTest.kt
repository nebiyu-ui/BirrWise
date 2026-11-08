package et.birrwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AccountFormValidatorTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")

    private fun input(
        name: String = "Main account",
        provider: String = "",
        type: AccountType = AccountType.BANK,
        currency: String = ETB,
        amount: String = "",
        date: LocalDate? = LocalDate.of(2026, 11, 7),
        notes: String = ""
    ) = AccountFormInput(name, provider, type, currency, amount, date, notes)

    private fun validate(
        input: AccountFormInput,
        others: List<String> = emptyList(),
        archived: Boolean = false
    ): AccountFormResult = AccountFormValidator.validate("A1", input, others, addis, archived)

    private fun validAccount(input: AccountFormInput, others: List<String> = emptyList()): Account {
        val result = validate(input, others)
        assertTrue("Expected a valid account but got $result", result is AccountFormResult.Valid)
        return (result as AccountFormResult.Valid).account
    }

    private fun errorsOf(input: AccountFormInput, others: List<String> = emptyList()): Set<AccountFormError> {
        val result = validate(input, others)
        assertTrue("Expected errors but got $result", result is AccountFormResult.Invalid)
        return (result as AccountFormResult.Invalid).errors
    }

    @Test
    fun minimalInputBuildsAnAccount() {
        val account = validAccount(input())
        assertEquals("A1", account.id)
        assertEquals("Main account", account.name)
        assertEquals(etb(0), account.openingBalance)
        // 7 November in Addis Ababa starts at 21:00 UTC the evening before.
        assertEquals(at("2026-11-06T21:00:00Z"), account.openingBalanceDate)
        assertNull(account.provider)
        assertNull(account.notes)
        assertEquals(AccountType.BANK, account.type)
        assertEquals(false, account.archived)
    }

    @Test
    fun textIsTrimmedAndKept() {
        val account = validAccount(
            input(
                name = "  Dashen Savings  ",
                provider = " Dashen Bank ",
                type = AccountType.SAVINGS,
                notes = " for rent "
            )
        )
        assertEquals("Dashen Savings", account.name)
        assertEquals("Dashen Bank", account.provider)
        assertEquals(AccountType.SAVINGS, account.type)
        assertEquals("for rent", account.notes)
    }

    @Test
    fun openingBalanceIsParsedExactly() {
        assertEquals(Money(125_050L, ETB), validAccount(input(amount = "1,250.50")).openingBalance)
        assertEquals(Money(125_050L, ETB), validAccount(input(amount = "1250.5")).openingBalance)
        assertEquals(Money(500L, "JPY"), validAccount(input(currency = "JPY", amount = "500")).openingBalance)
    }

    @Test
    fun emptyOpeningBalanceMeansZero() {
        assertEquals(etb(0), validAccount(input(amount = "")).openingBalance)
        assertEquals(etb(0), validAccount(input(amount = "   ")).openingBalance)
    }

    @Test
    fun badAmountsAreRejected() {
        for (bad in listOf("abc", "-5", "1.234", "12,50", "1e3", "5.")) {
            assertEquals(
                "amount '$bad'",
                setOf(AccountFormError.AMOUNT_INVALID),
                errorsOf(input(amount = bad))
            )
        }
    }

    @Test
    fun noOpeningDateKeepsItEmpty() {
        assertNull(validAccount(input(date = null)).openingBalanceDate)
    }

    @Test
    fun nameIsRequiredAndLimited() {
        assertEquals(setOf(AccountFormError.NAME_REQUIRED), errorsOf(input(name = "")))
        assertEquals(setOf(AccountFormError.NAME_REQUIRED), errorsOf(input(name = "   ")))
        assertEquals(
            setOf(AccountFormError.NAME_TOO_LONG),
            errorsOf(input(name = "x".repeat(AccountFormValidator.MAX_NAME_LENGTH + 1)))
        )
        validAccount(input(name = "x".repeat(AccountFormValidator.MAX_NAME_LENGTH)))
    }

    @Test
    fun nameMustNotRepeatAnotherAccountIgnoringCaseAndSpaces() {
        assertEquals(
            setOf(AccountFormError.NAME_TAKEN),
            errorsOf(input(name = "Main account"), others = listOf(" MAIN ACCOUNT "))
        )
        validAccount(input(name = "Main account"), others = listOf("Savings"))
    }

    @Test
    fun providerAndNotesAreLimited() {
        assertEquals(
            setOf(AccountFormError.PROVIDER_TOO_LONG),
            errorsOf(input(provider = "p".repeat(AccountFormValidator.MAX_PROVIDER_LENGTH + 1)))
        )
        assertEquals(
            setOf(AccountFormError.NOTES_TOO_LONG),
            errorsOf(input(notes = "n".repeat(AccountFormValidator.MAX_NOTES_LENGTH + 1)))
        )
    }

    @Test
    fun severalProblemsAreReportedTogether() {
        assertEquals(
            setOf(AccountFormError.NAME_REQUIRED, AccountFormError.AMOUNT_INVALID),
            errorsOf(input(name = "", amount = "x"))
        )
    }

    @Test
    fun archivedFlagIsCarriedOver() {
        val result = validate(input(), archived = true) as AccountFormResult.Valid
        assertEquals(true, result.account.archived)
    }
}
