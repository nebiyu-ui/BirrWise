package et.birrwise.app.domain

import java.time.Instant

/**
 * What a transaction is. Amounts are always positive; the type decides the financial effect.
 * A transfer between two accounts is recorded as two linked legs (TRANSFER_OUT and TRANSFER_IN)
 * that share a transferGroupId.
 */
enum class TransactionType {
    INCOME,
    EXPENSE,
    FEE,
    REFUND,
    TRANSFER_IN,
    TRANSFER_OUT,
    REVERSAL
}

enum class TransactionStatus {
    CONFIRMED,
    PENDING_REVIEW
}

/**
 * The opening balance is the account's balance at [openingBalanceDate], before any transaction
 * that happens at or after that moment. Transactions before that moment are already part of the
 * opening balance and are not counted again. If the date is null, every transaction counts.
 */
data class Account(
    val id: String,
    val name: String,
    val currencyCode: String,
    val openingBalance: Money,
    val openingBalanceDate: Instant? = null,
    val provider: String? = null,
    val type: AccountType = AccountType.OTHER,
    val archived: Boolean = false,
    val notes: String? = null
) {
    init {
        require(id.isNotBlank()) { "Account id must not be blank" }
        require(openingBalance.currencyCode == currencyCode) {
            "Opening balance currency ${openingBalance.currencyCode} does not match account currency $currencyCode"
        }
    }
}

data class Transaction(
    val id: String,
    val accountId: String,
    val type: TransactionType,
    val amount: Money,
    val occurredAt: Instant,
    val categoryId: String? = null,
    val status: TransactionStatus = TransactionStatus.CONFIRMED,
    val transferGroupId: String? = null,
    val reversesTransactionId: String? = null,
    val isExcludedFromReports: Boolean = false,
    val description: String = "",
    val merchantName: String? = null,
    val counterparty: String? = null,
    val personId: String? = null,
    val sourceType: SourceType = SourceType.MANUAL,
    val externalReference: String? = null,
    val importBatchId: String? = null,
    val notes: String? = null,
    val reviewedAt: Instant? = null
) {
    init {
        require(id.isNotBlank()) { "Transaction id must not be blank" }
        require(accountId.isNotBlank()) { "Account id must not be blank" }
        require(amount.minorUnits > 0L) { "Transaction amounts must be greater than zero" }
    }
}

data class Category(
    val id: String,
    val name: String,
    val localizedNameKey: String? = null,
    val type: CategoryType,
    val parentId: String? = null,
    val iconId: String? = null,
    val colorId: String? = null,
    val isSystem: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "Category id must not be blank" }
        require(name.isNotBlank()) { "Category name must not be blank" }
    }
}

/** A person (or business) who appears in transactions. */
data class Person(
    val id: String,
    val displayName: String,
    val notes: String? = null,
    val isHidden: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "Person id must not be blank" }
        require(displayName.isNotBlank()) { "Person name must not be blank" }
    }
}
