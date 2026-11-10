package et.birrwise.app.domain

import et.birrwise.app.data.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TransactionFormValidatorTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")

    // 09:00 UTC is 12:00 noon in Addis Ababa.
    private val now: Instant = Instant.parse("2026-11-07T09:00:00Z")
    private val today: LocalDate = LocalDate.of(2026, 11, 7)

    private val a1 = account("A1", openingBirr = 1000)
    private val a2 = account("A2", openingBirr = 500)
    private val accounts: Map<String, Account> = listOf(a1, a2).associateBy { it.id }

    private val categories: Map<String, Category> = DefaultCategories.definitions.associate {
        it.id to Category(
            id = it.id,
            name = it.name,
            localizedNameKey = it.localizationKey,
            type = it.type,
            isSystem = true
        )
    }

    private fun counter(): () -> String {
        var n = 0
        return { "id-${++n}" }
    }

    private fun input(
        kind: EntryKind = EntryKind.EXPENSE,
        accountId: String? = "A1",
        toAccountId: String? = null,
        amount: String = "100",
        fee: String = "",
        date: LocalDate = today,
        categoryId: String? = null,
        description: String = "",
        counterparty: String = "",
        notes: String = "",
        needsReview: Boolean = false
    ) = TransactionFormInput(
        kind, accountId, toAccountId, amount, fee, date, categoryId,
        description, counterparty, notes, needsReview
    )

    private fun validate(
        input: TransactionFormInput,
        existing: Transaction? = null,
        newId: () -> String = counter(),
        accountMap: Map<String, Account> = accounts
    ): TransactionFormResult =
        TransactionFormValidator.validate(input, accountMap, categories, newId, now, addis, existing)

    private fun single(result: TransactionFormResult): Transaction {
        assertTrue("Expected a single transaction but got $result", result is TransactionFormResult.Single)
        return (result as TransactionFormResult.Single).transaction
    }

    private fun transfer(result: TransactionFormResult): TransactionFormResult.Transfer {
        assertTrue("Expected a transfer but got $result", result is TransactionFormResult.Transfer)
        return result as TransactionFormResult.Transfer
    }

    private fun errors(result: TransactionFormResult): Set<TransactionFormError> {
        assertTrue("Expected errors but got $result", result is TransactionFormResult.Invalid)
        return (result as TransactionFormResult.Invalid).errors
    }

    // ---- single entries ------------------------------------------------------------------

    @Test
    fun anExpenseBecomesOneConfirmedTransaction() {
        val saved = single(validate(input(amount = "250.50")))
        assertEquals("id-1", saved.id)
        assertEquals("A1", saved.accountId)
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(Money(25_050L, ETB), saved.amount)
        assertEquals(now, saved.occurredAt)
        assertEquals(TransactionStatus.CONFIRMED, saved.status)
        assertEquals(SourceType.MANUAL, saved.sourceType)
        assertNull(saved.categoryId)
        assertEquals("", saved.description)
        assertNull(saved.reviewedAt)
    }

    @Test
    fun everyKindMapsToItsTransactionType() {
        val expected = mapOf(
            EntryKind.INCOME to TransactionType.INCOME,
            EntryKind.EXPENSE to TransactionType.EXPENSE,
            EntryKind.FEE to TransactionType.FEE,
            EntryKind.REFUND to TransactionType.REFUND
        )
        for ((kind, type) in expected) {
            assertEquals(type, single(validate(input(kind = kind))).type)
        }
    }

    @Test
    fun anotherDayUsesNoonLocalTime() {
        val saved = single(validate(input(date = LocalDate.of(2026, 11, 5))))
        assertEquals(Instant.parse("2026-11-05T09:00:00Z"), saved.occurredAt)
    }

    @Test
    fun textIsTrimmedAndEmptyOptionalFieldsAreNull() {
        val saved = single(
            validate(input(description = " Lunch ", counterparty = " Sara ", notes = "   "))
        )
        assertEquals("Lunch", saved.description)
        assertEquals("Sara", saved.counterparty)
        assertNull(saved.notes)
    }

    @Test
    fun needsReviewMakesItPending() {
        val saved = single(validate(input(needsReview = true)))
        assertEquals(TransactionStatus.PENDING_REVIEW, saved.status)
        assertNull(saved.reviewedAt)
    }

    // ---- validation ----------------------------------------------------------------------

    @Test
    fun amountRules() {
        assertEquals(setOf(TransactionFormError.AMOUNT_REQUIRED), errors(validate(input(amount = ""))))
        assertEquals(setOf(TransactionFormError.AMOUNT_INVALID), errors(validate(input(amount = "abc"))))
        assertEquals(setOf(TransactionFormError.AMOUNT_INVALID), errors(validate(input(amount = "12,50"))))
        assertEquals(setOf(TransactionFormError.AMOUNT_ZERO), errors(validate(input(amount = "0"))))
        assertEquals(setOf(TransactionFormError.AMOUNT_ZERO), errors(validate(input(amount = "0.00"))))
    }

    @Test
    fun accountIsRequiredAndMustExist() {
        assertEquals(setOf(TransactionFormError.ACCOUNT_REQUIRED), errors(validate(input(accountId = null))))
        assertEquals(setOf(TransactionFormError.ACCOUNT_REQUIRED), errors(validate(input(accountId = "nope"))))
    }

    @Test
    fun categoryMustFitTheKind() {
        assertEquals(
            setOf(TransactionFormError.CATEGORY_MISMATCH),
            errors(validate(input(kind = EntryKind.EXPENSE, categoryId = "sys_income")))
        )
        assertEquals(
            setOf(TransactionFormError.CATEGORY_MISMATCH),
            errors(validate(input(kind = EntryKind.INCOME, categoryId = "sys_food_groceries")))
        )
        assertEquals(
            setOf(TransactionFormError.CATEGORY_MISMATCH),
            errors(validate(input(categoryId = "unknown-category")))
        )
        assertEquals("sys_food_groceries", single(validate(input(categoryId = "sys_food_groceries"))).categoryId)
        assertEquals("sys_income", single(validate(input(kind = EntryKind.INCOME, categoryId = "sys_income"))).categoryId)
        assertEquals("sys_bank_fees", single(validate(input(kind = EntryKind.FEE, categoryId = "sys_bank_fees"))).categoryId)
        assertEquals("sys_shopping", single(validate(input(kind = EntryKind.REFUND, categoryId = "sys_shopping"))).categoryId)
    }

    @Test
    fun textFieldsAreLimited() {
        assertEquals(
            setOf(TransactionFormError.DESCRIPTION_TOO_LONG),
            errors(validate(input(description = "d".repeat(TransactionFormValidator.MAX_DESCRIPTION_LENGTH + 1))))
        )
        assertEquals(
            setOf(TransactionFormError.COUNTERPARTY_TOO_LONG),
            errors(validate(input(counterparty = "c".repeat(TransactionFormValidator.MAX_COUNTERPARTY_LENGTH + 1))))
        )
        assertEquals(
            setOf(TransactionFormError.NOTES_TOO_LONG),
            errors(validate(input(notes = "n".repeat(TransactionFormValidator.MAX_NOTES_LENGTH + 1))))
        )
    }

    @Test
    fun severalProblemsAreReportedTogether() {
        assertEquals(
            setOf(TransactionFormError.ACCOUNT_REQUIRED, TransactionFormError.AMOUNT_REQUIRED),
            errors(validate(input(accountId = null, amount = "")))
        )
    }

    // ---- transfers -----------------------------------------------------------------------

    @Test
    fun aTransferBecomesTwoLinkedLegs() {
        val result = transfer(
            validate(
                input(
                    kind = EntryKind.TRANSFER,
                    toAccountId = "A2",
                    amount = "300",
                    description = "Rent share",
                    counterparty = "Sara",
                    notes = "monthly"
                )
            )
        )
        val out = result.outLeg
        val into = result.inLeg
        assertEquals(TransactionType.TRANSFER_OUT, out.type)
        assertEquals("A1", out.accountId)
        assertEquals(TransactionType.TRANSFER_IN, into.type)
        assertEquals("A2", into.accountId)
        assertEquals(Money(30_000L, ETB), out.amount)
        assertEquals(out.amount, into.amount)
        assertEquals(out.occurredAt, into.occurredAt)
        assertEquals("id-1", out.transferGroupId)
        assertEquals(out.transferGroupId, into.transferGroupId)
        assertEquals("id-2", out.id)
        assertEquals("id-3", into.id)
        assertEquals("sys_transfers", out.categoryId)
        assertEquals("sys_transfers", into.categoryId)
        assertEquals("Rent share", into.description)
        assertEquals("Sara", into.counterparty)
        assertEquals("monthly", into.notes)
        assertNull(result.fee)
    }

    @Test
    fun aTransferFeeIsASeparateFeeOnTheSendingAccount() {
        val result = transfer(
            validate(input(kind = EntryKind.TRANSFER, toAccountId = "A2", amount = "300", fee = "10"))
        )
        val fee = result.fee
        assertNotNull(fee)
        assertEquals(TransactionType.FEE, fee!!.type)
        assertEquals("A1", fee.accountId)
        assertEquals(Money(1_000L, ETB), fee.amount)
        assertEquals("sys_bank_fees", fee.categoryId)
        assertEquals(TransactionFormValidator.TRANSFER_FEE_DESCRIPTION, fee.description)
        assertNull(fee.transferGroupId)
        assertEquals("id-4", fee.id)
        assertEquals(result.outLeg.occurredAt, fee.occurredAt)
    }

    @Test
    fun aZeroFeeMeansNoFeeAndAnInvalidFeeIsRejected() {
        val none = transfer(
            validate(input(kind = EntryKind.TRANSFER, toAccountId = "A2", fee = "0"))
        )
        assertNull(none.fee)
        assertEquals(
            setOf(TransactionFormError.FEE_INVALID),
            errors(validate(input(kind = EntryKind.TRANSFER, toAccountId = "A2", fee = "x")))
        )
    }

    @Test
    fun transferAccountRules() {
        assertEquals(
            setOf(TransactionFormError.TO_ACCOUNT_REQUIRED),
            errors(validate(input(kind = EntryKind.TRANSFER, toAccountId = null)))
        )
        assertEquals(
            setOf(TransactionFormError.SAME_ACCOUNT),
            errors(validate(input(kind = EntryKind.TRANSFER, toAccountId = "A1")))
        )
        assertEquals(
            setOf(TransactionFormError.ACCOUNT_REQUIRED),
            errors(validate(input(kind = EntryKind.TRANSFER, accountId = null, toAccountId = "A2")))
        )
    }

    @Test
    fun transferBetweenDifferentCurrenciesIsRejected() {
        val dollars = account("U1", openingBirr = 10, currency = "USD")
        val result = validate(
            input(kind = EntryKind.TRANSFER, toAccountId = "U1"),
            accountMap = accounts + (dollars.id to dollars)
        )
        assertEquals(setOf(TransactionFormError.CURRENCY_MISMATCH), errors(result))
    }

    // ---- editing -------------------------------------------------------------------------

    @Test
    fun editingKeepsWhatTheFormDoesNotShow() {
        val existing = tx("e1", "A1", TransactionType.EXPENSE, 10_000L, occurredAt = at("2026-11-05T09:30:00Z"))
            .copy(
                sourceType = SourceType.CSV,
                externalReference = "FT1",
                merchantName = "Cafe",
                importBatchId = null
            )
        val sameDay = single(
            validate(input(amount = "200", date = LocalDate.of(2026, 11, 5)), existing = existing)
        )
        assertEquals("e1", sameDay.id)
        assertEquals(Money(20_000L, ETB), sameDay.amount)
        assertEquals(at("2026-11-05T09:30:00Z"), sameDay.occurredAt)
        assertEquals(SourceType.CSV, sameDay.sourceType)
        assertEquals("FT1", sameDay.externalReference)
        assertEquals("Cafe", sameDay.merchantName)

        val otherDay = single(
            validate(input(amount = "200", date = LocalDate.of(2026, 11, 3)), existing = existing)
        )
        assertEquals(at("2026-11-03T09:00:00Z"), otherDay.occurredAt)
    }

    @Test
    fun editingCanMoveATransactionToAnotherAccountAndKind() {
        val existing = tx("e1", "A1", TransactionType.EXPENSE, 10_000L)
        val moved = single(
            validate(input(kind = EntryKind.INCOME, accountId = "A2"), existing = existing)
        )
        assertEquals("A2", moved.accountId)
        assertEquals(TransactionType.INCOME, moved.type)
    }

    @Test
    fun confirmingAPendingEntryRecordsWhenItWasReviewed() {
        val pending = tx("e1", "A1", TransactionType.EXPENSE, 10_000L, status = TransactionStatus.PENDING_REVIEW)
        val confirmed = single(validate(input(needsReview = false), existing = pending))
        assertEquals(TransactionStatus.CONFIRMED, confirmed.status)
        assertEquals(now, confirmed.reviewedAt)

        val reviewedBefore = confirmed.copy(reviewedAt = at("2026-10-01T00:00:00Z"))
        val untouched = single(validate(input(needsReview = false), existing = reviewedBefore))
        assertEquals(at("2026-10-01T00:00:00Z"), untouched.reviewedAt)

        val again = single(validate(input(needsReview = true), existing = reviewedBefore))
        assertEquals(TransactionStatus.PENDING_REVIEW, again.status)
        assertNull(again.reviewedAt)
    }

    @Test
    fun anExistingTransactionCannotBecomeATransfer() {
        val existing = tx("e1", "A1", TransactionType.EXPENSE, 10_000L)
        assertThrows(IllegalArgumentException::class.java) {
            validate(input(kind = EntryKind.TRANSFER, toAccountId = "A2"), existing = existing)
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    @Test
    fun resolveOccurredAtKeepsTodaysTimeAndEditsKeepTheirTime() {
        val existing = at("2026-11-05T09:30:00Z")
        assertEquals(
            existing,
            TransactionFormValidator.resolveOccurredAt(LocalDate.of(2026, 11, 5), existing, now, addis)
        )
        assertEquals(
            now,
            TransactionFormValidator.resolveOccurredAt(today, null, now, addis)
        )
        assertEquals(
            at("2026-11-01T09:00:00Z"),
            TransactionFormValidator.resolveOccurredAt(LocalDate.of(2026, 11, 1), existing, now, addis)
        )
    }

    @Test
    fun beforeOpeningBalanceIsDetected() {
        val dated = account("A", openingBirr = 10, openingDate = at("2026-11-01T00:00:00Z"))
        assertTrue(TransactionFormValidator.isBeforeOpeningBalance(dated, at("2026-10-31T23:59:59Z")))
        assertFalse(TransactionFormValidator.isBeforeOpeningBalance(dated, at("2026-11-01T00:00:00Z")))
        assertFalse(TransactionFormValidator.isBeforeOpeningBalance(account("B"), at("2000-01-01T00:00:00Z")))
    }

    @Test
    fun entryKindsMapBothWays() {
        assertEquals(EntryKind.INCOME, EntryKind.forType(TransactionType.INCOME))
        assertEquals(EntryKind.REFUND, EntryKind.forType(TransactionType.REFUND))
        assertNull(EntryKind.forType(TransactionType.TRANSFER_IN))
        assertNull(EntryKind.forType(TransactionType.TRANSFER_OUT))
        assertNull(EntryKind.forType(TransactionType.REVERSAL))
        assertNull(EntryKind.TRANSFER.toTransactionType())
    }
}
