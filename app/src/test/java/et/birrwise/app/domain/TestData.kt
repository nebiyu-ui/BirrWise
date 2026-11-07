package et.birrwise.app.domain

import java.time.Instant

internal const val ETB = "ETB"

/** Whole birr plus optional cents, as Money. */
internal fun etb(birr: Long, cents: Long = 0L): Money = Money(birr * 100L + cents, ETB)

internal fun at(iso: String): Instant = Instant.parse(iso)

internal fun account(
    id: String,
    openingBirr: Long = 0L,
    openingDate: Instant? = null,
    currency: String = ETB
): Account = Account(id, "Account $id", currency, Money(openingBirr * 100L, currency), openingDate)

internal fun tx(
    id: String,
    accountId: String,
    type: TransactionType,
    amountMinor: Long,
    occurredAt: Instant = Instant.parse("2026-01-15T10:00:00Z"),
    categoryId: String? = null,
    status: TransactionStatus = TransactionStatus.CONFIRMED,
    transferGroupId: String? = null,
    reversesTransactionId: String? = null,
    excluded: Boolean = false,
    currency: String = ETB
): Transaction = Transaction(
    id = id,
    accountId = accountId,
    type = type,
    amount = Money(amountMinor, currency),
    occurredAt = occurredAt,
    categoryId = categoryId,
    status = status,
    transferGroupId = transferGroupId,
    reversesTransactionId = reversesTransactionId,
    isExcludedFromReports = excluded
)
