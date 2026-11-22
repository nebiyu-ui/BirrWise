package et.birrwise.app.data

import et.birrwise.app.data.db.AccountEntity
import et.birrwise.app.data.db.BudgetEntity
import et.birrwise.app.data.db.CategoryEntity
import et.birrwise.app.data.db.PersonEntity
import et.birrwise.app.data.db.TransactionEntity
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.Budget
import et.birrwise.app.domain.BudgetPeriodType
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.Money
import et.birrwise.app.domain.Person
import et.birrwise.app.domain.Transaction
import java.time.Instant

internal fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    currencyCode = currencyCode,
    openingBalance = Money(openingBalanceMinorUnits, currencyCode),
    openingBalanceDate = openingBalanceDate?.let { Instant.ofEpochMilli(it) },
    provider = provider,
    type = accountType,
    archived = archived,
    notes = notes
)

internal fun Account.toEntity(createdAt: Long, updatedAt: Long): AccountEntity = AccountEntity(
    id = id,
    name = name,
    provider = provider,
    accountType = type,
    currencyCode = currencyCode,
    openingBalanceMinorUnits = openingBalance.minorUnits,
    openingBalanceDate = openingBalanceDate?.toEpochMilli(),
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    notes = notes
)

internal fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    accountId = accountId,
    type = transactionType,
    amount = Money(amountMinorUnits, currencyCode),
    occurredAt = Instant.ofEpochMilli(occurredAt),
    categoryId = categoryId,
    status = status,
    transferGroupId = transferGroupId,
    reversesTransactionId = reversesTransactionId,
    isExcludedFromReports = isExcludedFromReports,
    description = description,
    merchantName = merchantName,
    counterparty = counterparty,
    personId = personId,
    sourceType = sourceType,
    externalReference = externalReference,
    importBatchId = importBatchId,
    notes = notes,
    reviewedAt = reviewedAt?.let { Instant.ofEpochMilli(it) }
)

/** Returns null for a stored row that is not a valid transaction (for example a zero amount). */
internal fun TransactionEntity.toDomainOrNull(): Transaction? =
    try {
        toDomain()
    } catch (e: IllegalArgumentException) {
        null
    }

internal fun Transaction.toEntity(createdAt: Long, updatedAt: Long): TransactionEntity = TransactionEntity(
    id = id,
    accountId = accountId,
    transactionType = type,
    amountMinorUnits = amount.minorUnits,
    currencyCode = amount.currencyCode,
    occurredAt = occurredAt.toEpochMilli(),
    description = description,
    merchantName = merchantName,
    counterparty = counterparty,
    personId = personId,
    categoryId = categoryId,
    sourceType = sourceType,
    externalReference = externalReference,
    importBatchId = importBatchId,
    transferGroupId = transferGroupId,
    reversesTransactionId = reversesTransactionId,
    status = status,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    reviewedAt = reviewedAt?.toEpochMilli(),
    isExcludedFromReports = isExcludedFromReports
)

internal fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    localizedNameKey = localizedNameKey,
    type = categoryType,
    parentId = parentCategoryId,
    iconId = iconId,
    colorId = colorId,
    isSystem = isSystemCategory
)

internal fun Category.toEntity(createdAt: Long, updatedAt: Long): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    localizedNameKey = localizedNameKey,
    categoryType = type,
    parentCategoryId = parentId,
    iconId = iconId,
    colorId = colorId,
    isSystemCategory = isSystem,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun PersonEntity.toDomain(): Person = Person(
    id = id,
    displayName = displayName,
    notes = notes,
    isHidden = isHidden
)

internal fun Person.toEntity(createdAt: Long, updatedAt: Long): PersonEntity = PersonEntity(
    id = id,
    displayName = displayName,
    notes = notes,
    isHidden = isHidden,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = categoryId,
    limit = Money(limitMinorUnits, currencyCode),
    alertThresholdPercent = alertThresholdPercent,
    periodStart = Instant.ofEpochMilli(periodStart),
    periodEnd = periodEnd?.let { Instant.ofEpochMilli(it) },
    active = active
)

/** Returns null for a stored row that is not a valid budget. The app cannot create such rows. */
internal fun BudgetEntity.toDomainOrNull(): Budget? =
    try {
        toDomain()
    } catch (e: IllegalArgumentException) {
        null
    }

internal fun Budget.toEntity(createdAt: Long, updatedAt: Long): BudgetEntity = BudgetEntity(
    id = id,
    categoryId = categoryId,
    limitMinorUnits = limit.minorUnits,
    currencyCode = limit.currencyCode,
    periodType = BudgetPeriodType.MONTHLY,
    periodStart = periodStart.toEpochMilli(),
    periodEnd = periodEnd?.toEpochMilli(),
    alertThresholdPercent = alertThresholdPercent,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt
)
