package et.birrwise.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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

// Money is always stored as whole minor units (cents). Times are epoch milliseconds (UTC).
// See docs/DATABASE.md for what happens to related rows when something is deleted.

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val provider: String?,
    val accountType: AccountType,
    val currencyCode: String,
    val openingBalanceMinorUnits: Long,
    val openingBalanceDate: Long?,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val notes: String?
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentCategoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["parentCategoryId"])]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val localizedNameKey: String?,
    val categoryType: CategoryType,
    val parentCategoryId: String?,
    val iconId: String?,
    val colorId: String?,
    val isSystemCategory: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val notes: String?,
    val isHidden: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "import_batches",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["accountId"])]
)
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val originalFilename: String?,
    val sourceType: SourceType,
    val accountId: String,
    val importedAt: Long,
    val recordsDetected: Int,
    val recordsImported: Int,
    val recordsSkipped: Int,
    val recordsNeedingReview: Int,
    val status: ImportStatus,
    val validationSummary: String?
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["importBatchId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["reversesTransactionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["accountId", "occurredAt"]),
        Index(value = ["occurredAt"]),
        Index(value = ["categoryId"]),
        Index(value = ["personId"]),
        Index(value = ["importBatchId"]),
        Index(value = ["transferGroupId"]),
        Index(value = ["reversesTransactionId"]),
        Index(value = ["accountId", "externalReference"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val transactionType: TransactionType,
    val amountMinorUnits: Long,
    val currencyCode: String,
    val occurredAt: Long,
    val description: String,
    val merchantName: String?,
    val counterparty: String?,
    val personId: String?,
    val categoryId: String?,
    val sourceType: SourceType,
    val externalReference: String?,
    val importBatchId: String?,
    val transferGroupId: String?,
    val reversesTransactionId: String?,
    val status: TransactionStatus,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val reviewedAt: Long?,
    val isExcludedFromReports: Boolean
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val limitMinorUnits: Long,
    val currencyCode: String,
    val periodType: BudgetPeriodType,
    val periodStart: Long,
    val periodEnd: Long?,
    val alertThresholdPercent: Int,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "category_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"]), Index(value = ["accountId"])]
)
data class CategoryRuleEntity(
    @PrimaryKey val id: String,
    val matchField: RuleMatchField,
    val matchType: RuleMatchType,
    val pattern: String,
    val categoryId: String,
    val accountId: String?,
    val provider: String?,
    val priority: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "savings_goals",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["accountId"])]
)
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetAmountMinorUnits: Long,
    val currencyCode: String,
    val targetDate: Long?,
    val accountId: String?,
    val trackingMethod: GoalTrackingMethod,
    val manualProgressMinorUnits: Long,
    val createdAt: Long,
    val updatedAt: Long
)
