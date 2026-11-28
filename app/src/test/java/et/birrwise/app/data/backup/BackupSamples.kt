package et.birrwise.app.data.backup

import et.birrwise.app.data.db.AccountEntity
import et.birrwise.app.data.db.BudgetEntity
import et.birrwise.app.data.db.CategoryEntity
import et.birrwise.app.data.db.CategoryRuleEntity
import et.birrwise.app.data.db.ImportBatchEntity
import et.birrwise.app.data.db.PersonEntity
import et.birrwise.app.data.db.SavingsGoalEntity
import et.birrwise.app.data.db.TransactionEntity
import et.birrwise.app.domain.AccountType
import et.birrwise.app.domain.BudgetPeriodType
import et.birrwise.app.domain.CategoryType
import et.birrwise.app.domain.GoalTrackingMethod
import et.birrwise.app.domain.ImportStatus
import et.birrwise.app.domain.RuleMatchField
import et.birrwise.app.domain.RuleMatchType
import et.birrwise.app.domain.SourceType
import et.birrwise.app.domain.TransactionStatus
import et.birrwise.app.domain.TransactionType

/** A backup that uses every table, with optional fields both filled in and empty. */
internal object BackupSamples {

    fun full(createdAt: Long = 1_700_000_000_000L, appVersion: String = "0.9.0"): BackupData = BackupData(
        formatVersion = BackupData.CURRENT_FORMAT_VERSION,
        createdAt = createdAt,
        appVersion = appVersion,
        accounts = listOf(
            AccountEntity(
                id = "A1", name = "Main", provider = null, accountType = AccountType.BANK,
                currencyCode = "ETB", openingBalanceMinorUnits = 150_050L,
                openingBalanceDate = 1_700_000_000_000L, archived = false,
                createdAt = 10L, updatedAt = 20L, notes = null
            ),
            AccountEntity(
                id = "A2", name = "Cash \u1230\u120b\u121d", provider = "Wallet", accountType = AccountType.CASH,
                currencyCode = "ETB", openingBalanceMinorUnits = -500L,
                openingBalanceDate = null, archived = true,
                createdAt = 11L, updatedAt = 21L, notes = "kept \"safe\"\nin a drawer"
            )
        ),
        categories = listOf(
            CategoryEntity(
                id = "c-parent", name = "Parent", localizedNameKey = null,
                categoryType = CategoryType.EXPENSE, parentCategoryId = null,
                iconId = null, colorId = null, isSystemCategory = false, createdAt = 1L, updatedAt = 2L
            ),
            CategoryEntity(
                id = "c-child", name = "Child", localizedNameKey = "key",
                categoryType = CategoryType.EXPENSE, parentCategoryId = "c-parent",
                iconId = "icon", colorId = "green", isSystemCategory = true, createdAt = 3L, updatedAt = 4L
            )
        ),
        people = listOf(
            PersonEntity(
                id = "P1", displayName = "Abebe", notes = "friend", isHidden = true,
                createdAt = 5L, updatedAt = 6L
            )
        ),
        importBatches = listOf(
            ImportBatchEntity(
                id = "B1", originalFilename = "statement.csv", sourceType = SourceType.CSV,
                accountId = "A1", importedAt = 7L, recordsDetected = 4, recordsImported = 3,
                recordsSkipped = 1, recordsNeedingReview = 2, status = ImportStatus.COMPLETED,
                validationSummary = null
            )
        ),
        transactions = listOf(
            transaction(
                "t1", "A1", TransactionType.EXPENSE, 9_000_000_000_000_000L,
                categoryId = "c-child", personId = "P1", importBatchId = "B1",
                sourceType = SourceType.CSV, externalReference = "FT1",
                status = TransactionStatus.PENDING_REVIEW, reviewedAt = 5L, notes = "line 1\nline 2"
            ),
            transaction("t2", "A1", TransactionType.REVERSAL, 100L, reversesTransactionId = "t1"),
            transaction("t3", "A1", TransactionType.TRANSFER_OUT, 30_000L, transferGroupId = "g"),
            transaction("t4", "A2", TransactionType.TRANSFER_IN, 30_000L, transferGroupId = "g")
        ),
        budgets = listOf(
            BudgetEntity(
                id = "bud1", categoryId = "c-child", limitMinorUnits = 500_000L, currencyCode = "ETB",
                periodType = BudgetPeriodType.MONTHLY, periodStart = 1_000L, periodEnd = null,
                alertThresholdPercent = 80, active = true, createdAt = 1L, updatedAt = 2L
            ),
            BudgetEntity(
                id = "bud2", categoryId = "c-parent", limitMinorUnits = 100_000L, currencyCode = "ETB",
                periodType = BudgetPeriodType.MONTHLY, periodStart = 1_000L, periodEnd = 2_000L,
                alertThresholdPercent = 60, active = false, createdAt = 3L, updatedAt = 4L
            )
        ),
        categoryRules = listOf(
            CategoryRuleEntity(
                id = "r1", matchField = RuleMatchField.ANY, matchType = RuleMatchType.CONTAINS,
                pattern = "cafe", categoryId = "c-child", accountId = null, provider = "CBE",
                priority = 3, enabled = true, createdAt = 1L, updatedAt = 2L
            )
        ),
        savingsGoals = listOf(
            SavingsGoalEntity(
                id = "g1", name = "Laptop", targetAmountMinorUnits = 5_000_000L, currencyCode = "ETB",
                targetDate = 3_000L, accountId = "A1", trackingMethod = GoalTrackingMethod.ACCOUNT_BALANCE,
                manualProgressMinorUnits = 0L, createdAt = 1L, updatedAt = 2L
            ),
            SavingsGoalEntity(
                id = "g2", name = "Trip", targetAmountMinorUnits = 1_000_000L, currencyCode = "ETB",
                targetDate = null, accountId = null, trackingMethod = GoalTrackingMethod.MANUAL,
                manualProgressMinorUnits = 7_000L, createdAt = 3L, updatedAt = 4L
            )
        )
    )

    private fun transaction(
        id: String,
        accountId: String,
        type: TransactionType,
        amount: Long,
        categoryId: String? = null,
        personId: String? = null,
        importBatchId: String? = null,
        sourceType: SourceType = SourceType.MANUAL,
        externalReference: String? = null,
        transferGroupId: String? = null,
        reversesTransactionId: String? = null,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        reviewedAt: Long? = null,
        notes: String? = null
    ) = TransactionEntity(
        id = id, accountId = accountId, transactionType = type, amountMinorUnits = amount,
        currencyCode = "ETB", occurredAt = 1_700_000_000_000L, description = "d-$id",
        merchantName = null, counterparty = null, personId = personId, categoryId = categoryId,
        sourceType = sourceType, externalReference = externalReference, importBatchId = importBatchId,
        transferGroupId = transferGroupId, reversesTransactionId = reversesTransactionId,
        status = status, notes = notes, createdAt = 8L, updatedAt = 9L, reviewedAt = reviewedAt,
        isExcludedFromReports = false
    )
}
