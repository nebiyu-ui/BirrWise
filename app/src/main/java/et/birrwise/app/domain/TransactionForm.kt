package et.birrwise.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The kinds of entry a person can make by hand. A transfer creates two linked transactions. */
enum class EntryKind {
    INCOME,
    EXPENSE,
    FEE,
    REFUND,
    TRANSFER;

    /** The transaction type for a single transaction, or null for a transfer. */
    fun toTransactionType(): TransactionType? = when (this) {
        INCOME -> TransactionType.INCOME
        EXPENSE -> TransactionType.EXPENSE
        FEE -> TransactionType.FEE
        REFUND -> TransactionType.REFUND
        TRANSFER -> null
    }

    companion object {
        /** The entry kind that can edit a stored transaction of this type, or null if it cannot be edited. */
        fun forType(type: TransactionType): EntryKind? = when (type) {
            TransactionType.INCOME -> INCOME
            TransactionType.EXPENSE -> EXPENSE
            TransactionType.FEE -> FEE
            TransactionType.REFUND -> REFUND
            TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT, TransactionType.REVERSAL -> null
        }
    }
}

enum class TransactionFormError {
    ACCOUNT_REQUIRED,
    TO_ACCOUNT_REQUIRED,
    SAME_ACCOUNT,
    CURRENCY_MISMATCH,
    AMOUNT_REQUIRED,
    AMOUNT_INVALID,
    AMOUNT_ZERO,
    FEE_INVALID,
    CATEGORY_MISMATCH,
    DESCRIPTION_TOO_LONG,
    COUNTERPARTY_TOO_LONG,
    NOTES_TOO_LONG
}

/** What the person typed into the transaction form. */
data class TransactionFormInput(
    val kind: EntryKind,
    val accountId: String?,
    /** Only used for transfers. */
    val toAccountId: String?,
    val amountText: String,
    /** Only used for transfers. Empty means no fee. */
    val feeText: String,
    val date: LocalDate,
    /** Ignored for transfers. */
    val categoryId: String?,
    val description: String,
    val counterparty: String,
    val notes: String,
    val needsReview: Boolean
)

sealed interface TransactionFormResult {
    data class Single(val transaction: Transaction) : TransactionFormResult
    data class Transfer(
        val outLeg: Transaction,
        val inLeg: Transaction,
        val fee: Transaction?
    ) : TransactionFormResult
    data class Invalid(val errors: Set<TransactionFormError>) : TransactionFormResult
}

object TransactionFormValidator {

    const val MAX_DESCRIPTION_LENGTH = 100
    const val MAX_COUNTERPARTY_LENGTH = 80
    const val MAX_NOTES_LENGTH = 500

    const val TRANSFERS_CATEGORY_ID = "sys_transfers"
    const val BANK_FEES_CATEGORY_ID = "sys_bank_fees"
    const val TRANSFER_FEE_DESCRIPTION = "Transfer fee"

    /**
     * Picks the exact moment for an entry made on [date]. An edit that keeps its day keeps its
     * time. Today's entries use the current time, and entries for other days use noon, so they do
     * not land on the edge of the opening balance date.
     */
    fun resolveOccurredAt(date: LocalDate, existing: Instant?, now: Instant, zone: ZoneId): Instant {
        if (existing != null && existing.atZone(zone).toLocalDate() == date) return existing
        val today = now.atZone(zone).toLocalDate()
        return if (date == today) now else date.atTime(LocalTime.NOON).atZone(zone).toInstant()
    }

    /** True when a transaction at [occurredAt] would not change [account]'s tracked balance. */
    fun isBeforeOpeningBalance(account: Account, occurredAt: Instant): Boolean {
        val openingDate = account.openingBalanceDate ?: return false
        return occurredAt < openingDate
    }

    /**
     * Checks the form and builds what should be saved. Editing keeps everything the form does not
     * show (source, reference, import batch, and so on). Existing transactions cannot become
     * transfers, and transfers are not edited here.
     */
    fun validate(
        input: TransactionFormInput,
        accounts: Map<String, Account>,
        categories: Map<String, Category>,
        newId: () -> String,
        now: Instant,
        zone: ZoneId,
        existing: Transaction? = null
    ): TransactionFormResult {
        require(existing == null || input.kind != EntryKind.TRANSFER) {
            "An existing transaction cannot become a transfer"
        }
        val isTransfer = input.kind == EntryKind.TRANSFER
        val errors = mutableSetOf<TransactionFormError>()

        val account = input.accountId?.let { accounts[it] }
        if (account == null) errors += TransactionFormError.ACCOUNT_REQUIRED

        val toAccount = if (isTransfer) input.toAccountId?.let { accounts[it] } else null
        if (isTransfer) {
            if (toAccount == null) {
                errors += TransactionFormError.TO_ACCOUNT_REQUIRED
            } else if (account != null && toAccount.id == account.id) {
                errors += TransactionFormError.SAME_ACCOUNT
            } else if (account != null && toAccount.currencyCode != account.currencyCode) {
                errors += TransactionFormError.CURRENCY_MISMATCH
            }
        }

        val currency = account?.currencyCode ?: DEFAULT_CURRENCY_CODE

        val amountText = input.amountText.trim()
        var amount: Money? = null
        if (amountText.isEmpty()) {
            errors += TransactionFormError.AMOUNT_REQUIRED
        } else {
            val parsed = MoneyParser.parse(amountText, currency)
            if (parsed == null) {
                errors += TransactionFormError.AMOUNT_INVALID
            } else if (!parsed.isPositive) {
                errors += TransactionFormError.AMOUNT_ZERO
            } else {
                amount = parsed
            }
        }

        var fee: Money? = null
        if (isTransfer) {
            val feeText = input.feeText.trim()
            if (feeText.isNotEmpty()) {
                val parsedFee = MoneyParser.parse(feeText, currency)
                if (parsedFee == null) {
                    errors += TransactionFormError.FEE_INVALID
                } else if (parsedFee.isPositive) {
                    fee = parsedFee
                }
            }
        }

        val category = input.categoryId?.let { categories[it] }
        if (!isTransfer && input.categoryId != null) {
            val expected = if (input.kind == EntryKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
            if (category == null || category.type != expected) errors += TransactionFormError.CATEGORY_MISMATCH
        }

        val description = input.description.trim()
        if (description.length > MAX_DESCRIPTION_LENGTH) errors += TransactionFormError.DESCRIPTION_TOO_LONG
        val counterparty = input.counterparty.trim()
        if (counterparty.length > MAX_COUNTERPARTY_LENGTH) errors += TransactionFormError.COUNTERPARTY_TOO_LONG
        val notes = input.notes.trim()
        if (notes.length > MAX_NOTES_LENGTH) errors += TransactionFormError.NOTES_TOO_LONG

        if (errors.isNotEmpty()) return TransactionFormResult.Invalid(errors)
        val fromAccount = account ?: return TransactionFormResult.Invalid(errors)
        val money = amount ?: return TransactionFormResult.Invalid(errors)

        val occurredAt = resolveOccurredAt(input.date, existing?.occurredAt, now, zone)
        val status = if (input.needsReview) TransactionStatus.PENDING_REVIEW else TransactionStatus.CONFIRMED

        if (isTransfer) {
            val toAcc = toAccount ?: return TransactionFormResult.Invalid(errors)
            val groupId = newId()
            val outLeg = Transaction(
                id = newId(),
                accountId = fromAccount.id,
                type = TransactionType.TRANSFER_OUT,
                amount = money,
                occurredAt = occurredAt,
                categoryId = TRANSFERS_CATEGORY_ID.takeIf { categories.containsKey(it) },
                status = status,
                transferGroupId = groupId,
                description = description,
                counterparty = counterparty.ifEmpty { null },
                sourceType = SourceType.MANUAL,
                notes = notes.ifEmpty { null }
            )
            val inLeg = outLeg.copy(
                id = newId(),
                accountId = toAcc.id,
                type = TransactionType.TRANSFER_IN
            )
            val feeTransaction = fee?.let {
                Transaction(
                    id = newId(),
                    accountId = fromAccount.id,
                    type = TransactionType.FEE,
                    amount = it,
                    occurredAt = occurredAt,
                    categoryId = BANK_FEES_CATEGORY_ID.takeIf { id -> categories.containsKey(id) },
                    status = status,
                    description = TRANSFER_FEE_DESCRIPTION,
                    sourceType = SourceType.MANUAL
                )
            }
            return TransactionFormResult.Transfer(outLeg, inLeg, feeTransaction)
        }

        val type = input.kind.toTransactionType()
            ?: return TransactionFormResult.Invalid(errors)
        val reviewedAt = when {
            input.needsReview -> null
            existing != null && existing.status == TransactionStatus.PENDING_REVIEW -> now
            else -> existing?.reviewedAt
        }
        val base = existing ?: Transaction(
            id = newId(),
            accountId = fromAccount.id,
            type = type,
            amount = money,
            occurredAt = occurredAt,
            sourceType = SourceType.MANUAL
        )
        return TransactionFormResult.Single(
            base.copy(
                accountId = fromAccount.id,
                type = type,
                amount = money,
                occurredAt = occurredAt,
                categoryId = category?.id,
                status = status,
                description = description,
                counterparty = counterparty.ifEmpty { null },
                notes = notes.ifEmpty { null },
                reviewedAt = reviewedAt
            )
        )
    }
}
