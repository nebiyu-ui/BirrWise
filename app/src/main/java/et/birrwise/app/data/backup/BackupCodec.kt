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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Turns a [BackupData] into text and back. Money is stored as whole minor units (cents), times as
 * UTC epoch milliseconds, and enums by name. Reading is strict: anything unexpected is rejected.
 */
object BackupCodec {

    const val FORMAT_NAME = "birrwise-backup"

    fun encode(data: BackupData): String {
        val root = JSONObject()
        root.put("format", FORMAT_NAME)
        root.put("formatVersion", data.formatVersion)
        root.put("createdAt", data.createdAt)
        root.put("appVersion", data.appVersion)
        root.put("accounts", array(data.accounts) { accountJson(it) })
        root.put("categories", array(data.categories) { categoryJson(it) })
        root.put("people", array(data.people) { personJson(it) })
        root.put("importBatches", array(data.importBatches) { batchJson(it) })
        root.put("transactions", array(data.transactions) { transactionJson(it) })
        root.put("budgets", array(data.budgets) { budgetJson(it) })
        root.put("categoryRules", array(data.categoryRules) { ruleJson(it) })
        root.put("savingsGoals", array(data.savingsGoals) { goalJson(it) })
        return root.toString()
    }

    /** Reads and checks a backup. Throws [BackupException] if it is not a usable BirrWise backup. */
    fun decode(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException(BackupFailure.NOT_A_BACKUP)
        }
        if (root.optString("format") != FORMAT_NAME) throw BackupException(BackupFailure.NOT_A_BACKUP)

        val data = try {
            val version = root.getInt("formatVersion")
            if (version < 1 || version > BackupData.CURRENT_FORMAT_VERSION) {
                throw BackupException(BackupFailure.UNSUPPORTED_VERSION)
            }
            BackupData(
                formatVersion = version,
                createdAt = root.getLong("createdAt"),
                appVersion = root.optString("appVersion"),
                accounts = objects(root, "accounts").map { accountFrom(it) },
                categories = objects(root, "categories").map { categoryFrom(it) },
                people = objects(root, "people").map { personFrom(it) },
                importBatches = objects(root, "importBatches").map { batchFrom(it) },
                transactions = objects(root, "transactions").map { transactionFrom(it) },
                budgets = objects(root, "budgets").map { budgetFrom(it) },
                categoryRules = objects(root, "categoryRules").map { ruleFrom(it) },
                savingsGoals = objects(root, "savingsGoals").map { goalFrom(it) }
            )
        } catch (e: BackupException) {
            throw e
        } catch (e: JSONException) {
            throw BackupException(BackupFailure.CORRUPT)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupFailure.CORRUPT)
        }

        try {
            BackupValidator.validate(data)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupFailure.CORRUPT)
        }
        return data
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun <T> array(items: List<T>, toJson: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        items.forEach { array.put(toJson(it)) }
        return array
    }

    private fun objects(root: JSONObject, key: String): List<JSONObject> {
        val array = root.getJSONArray(key)
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun JSONObject.putOrNull(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.longOrNull(key: String): Long? =
        if (isNull(key)) null else getLong(key)

    // ---- accounts ------------------------------------------------------------------------

    private fun accountJson(e: AccountEntity) = JSONObject()
        .put("id", e.id)
        .put("name", e.name)
        .putOrNull("provider", e.provider)
        .put("accountType", e.accountType.name)
        .put("currencyCode", e.currencyCode)
        .put("openingBalanceMinorUnits", e.openingBalanceMinorUnits)
        .putOrNull("openingBalanceDate", e.openingBalanceDate)
        .put("archived", e.archived)
        .put("createdAt", e.createdAt)
        .put("updatedAt", e.updatedAt)
        .putOrNull("notes", e.notes)

    private fun accountFrom(o: JSONObject) = AccountEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        provider = o.stringOrNull("provider"),
        accountType = enumValueOf<AccountType>(o.getString("accountType")),
        currencyCode = o.getString("currencyCode"),
        openingBalanceMinorUnits = o.getLong("openingBalanceMinorUnits"),
        openingBalanceDate = o.longOrNull("openingBalanceDate"),
        archived = o.getBoolean("archived"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        notes = o.stringOrNull("notes")
    )

    // ---- categories ----------------------------------------------------------------------

    private fun categoryJson(e: CategoryEntity) = JSONObject()
        .put("id", e.id)
        .put("name", e.name)
        .putOrNull("localizedNameKey", e.localizedNameKey)
        .put("categoryType", e.categoryType.name)
        .putOrNull("parentCategoryId", e.parentCategoryId)
        .putOrNull("iconId", e.iconId)
        .putOrNull("colorId", e.colorId)
        .put("isSystemCategory", e.isSystemCategory)
        .put("createdAt", e.createdAt)
        .put("updatedAt", e.updatedAt)

    private fun categoryFrom(o: JSONObject) = CategoryEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        localizedNameKey = o.stringOrNull("localizedNameKey"),
        categoryType = enumValueOf<CategoryType>(o.getString("categoryType")),
        parentCategoryId = o.stringOrNull("parentCategoryId"),
        iconId = o.stringOrNull("iconId"),
        colorId = o.stringOrNull("colorId"),
        isSystemCategory = o.getBoolean("isSystemCategory"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt")
    )

    // ---- people --------------------------------------------------------------------------

    private fun personJson(e: PersonEntity) = JSONObject()
        .put("id", e.id)
        .put("displayName", e.displayName)
        .putOrNull("notes", e.notes)
        .put("isHidden", e.isHidden)
        .put("createdAt", e.createdAt)
        .put("updatedAt", e.updatedAt)

    private fun personFrom(o: JSONObject) = PersonEntity(
        id = o.getString("id"),
        displayName = o.getString("displayName"),
        notes = o.stringOrNull("notes"),
        isHidden = o.getBoolean("isHidden"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt")
    )

    // ---- import batches ------------------------------------------------------------------

    private fun batchJson(e: ImportBatchEntity) = JSONObject()
        .put("id", e.id)
        .putOrNull("originalFilename", e.originalFilename)
        .put("sourceType", e.sourceType.name)
        .put("accountId", e.accountId)
        .put("importedAt", e.importedAt)
        .put("recordsDetected", e.recordsDetected)
        .put("recordsImported", e.recordsImported)
        .put("recordsSkipped", e.recordsSkipped)
        .put("recordsNeedingReview", e.recordsNeedingReview)
        .put("status", e.status.name)
        .putOrNull("validationSummary", e.validationSummary)

    private fun batchFrom(o: JSONObject) = ImportBatchEntity(
        id = o.getString("id"),
        originalFilename = o.stringOrNull("originalFilename"),
        sourceType = enumValueOf<SourceType>(o.getString("sourceType")),
        accountId = o.getString("accountId"),
        importedAt = o.getLong("importedAt"),
        recordsDetected = o.getInt("recordsDetected"),
        recordsImported = o.getInt("recordsImported"),
        recordsSkipped = o.getInt("recordsSkipped"),
        recordsNeedingReview = o.getInt("recordsNeedingReview"),
        status = enumValueOf<ImportStatus>(o.getString("status")),
        validationSummary = o.stringOrNull("validationSummary")
    )

    // ---- transactions --------------------------------------------------------------------

    private fun transactionJson(e: TransactionEntity) = JSONObject()
        .put("id", e.id)
        .put("accountId", e.accountId)
        .put("transactionType", e.transactionType.name)
        .put("amountMinorUnits", e.amountMinorUnits)
        .put("currencyCode", e.currencyCode)
        .put("occurredAt", e.occurredAt)
        .put("description", e.description)
        .putOrNull("merchantName", e.merchantName)
        .putOrNull("counterparty", e.counterparty)
        .putOrNull("personId", e.personId)
        .putOrNull("categoryId", e.categoryId)
        .put("sourceType", e.sourceType.name)
        .putOrNull("externalReference", e.externalReference)
        .putOrNull("importBatchId", e.importBatchId)
        .putOrNull("transferGroupId", e.transferGroupId)
        .putOrNull("reversesTransactionId", e.reversesTransactionId)
        .put("status", e.status.name)
        .putOrNull("notes", e.notes)
        .put("createdAt", e.createdAt)
        .put("updatedAt", e.updatedAt)
        .putOrNull("reviewedAt", e.reviewedAt)
        .put("isExcludedFromReports", e.isExcludedFromReports)

    private fun transactionFrom(o: JSONObject) = TransactionEntity(
        id = o.getString("id"),
        accountId = o.getString("accountId"),
        transactionType = enumValueOf<TransactionType>(o.getString("transactionType")),
        amountMinorUnits = o.getLong("amountMinorUnits"),
        currencyCode = o.getString("currencyCode"),
        occurredAt = o.getLong("occurredAt"),
        description = o.getString("description"),
        merchantName = o.stringOrNull("merchantName"),
        counterparty = o.stringOrNull("counterparty"),
        personId = o.stringOrNull("personId"),
        categoryId = o.stringOrNull("categoryId"),
        sourceType = enumValueOf<SourceType>(o.getString("sourceType")),
        externalReference = o.stringOrNull("externalReference"),
        importBatchId = o.stringOrNull("importBatchId"),
        transferGroupId = o.stringOrNull("transferGroupId"),
        reversesTransactionId = o.stringOrNull("reversesTransactionId"),
        status = enumValueOf<TransactionStatus>(o.getString("status")),
        notes = o.stringOrNull("notes"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        reviewedAt = o.longOrNull("reviewedAt"),
        isExcludedFromReports = o.getBoolean("isExcludedFromReports")
    )

    // ---- budgets -------------------------------------------------------------------------

    private fun budgetJson(e: BudgetEntity) = JSONObject()
        .put("id", e.id)
        .put("categoryId", e.categoryId)
        .put("limitMinorUnits", e.limitMinorUnits)
        .put("currencyCode", e.currencyCode)
        .put("periodType", e.periodType.name)
        .put("periodStart", e.periodStart)
        .putOrNull("periodEnd", e.periodEnd)
        .put("alertThresholdPercent", e.alertThresholdPercent)
        .put("active", e.active)
        .put("createdAt", e.createdAt)
        .put("updatedAt", e.updatedAt)

    private fun budgetFrom(o: JSONObject) = BudgetEntity(
        id = o.getString("id"),
        categoryId = o.getString("categoryId"),
        limitMinorUnits = o.getLong("limitMinorUnits"),
        currencyCode = o.getString("currencyCode"),
        periodType = enumValueOf<BudgetPeriodType>(o.getString("periodType")),
        periodStart = o.getLong("periodStart"),
        periodEnd = o.longOrNull("periodEnd"),
        alertThresholdPercent = o.getInt("alertThresholdPercent"),
        active = o.getBoolean("active"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt")
    )

    // ---- category rules ------------------------------------------------------------------

    private fun ruleJson(e: CategoryRuleEntity) = JSONObject()
        .put("id", e.id)
        .put("matchField", e.matchField.name)
        .put("matchType", e.matchType.name)
        .put("pattern", e.pattern)
        .put("categoryId", e.categoryId)
        .putOrNull("accountId", e.accountId)
        .putOrNull("provider", e.provider)
        .put("priority", e.priority)
        .put("enabled", e.enabled)
        .put("createdAt", e.createdAt)
        .put("updatedAt", e.updatedAt)

    private fun ruleFrom(o: JSONObject) = CategoryRuleEntity(
        id = o.getString("id"),
        matchField = enumValueOf<RuleMatchField>(o.getString("matchField")),
        matchType = enumValueOf<RuleMatchType>(o.getString("matchType")),
        pattern = o.getString("pattern"),
        categoryId = o.getString("categoryId"),
        accountId = o.stringOrNull("accountId"),
        provider = o.stringOrNull("provider"),
        priority = o.getInt("priority"),
        enabled = o.getBoolean("enabled"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt")
    )

    // ---- savings goals -------------------------------------------------------------------

    private fun goalJson(e: SavingsGoalEntity) = JSONObject()
        .put("id", e.id)
        .put("name", e.name)
        .put("targetAmountMinorUnits", e.targetAmountMinorUnits)
        .put("currencyCode", e.currencyCode)
        .putOrNull("targetDate", e.targetDate)
        .putOrNull("accountId", e.accountId)
        .put("trackingMethod", e.trackingMethod.name)
        .put("manualProgressMinorUnits", e.manualProgressMinorUnits)
        .put("createdAt", e.createdAt)
        .put("updatedAt", e.updatedAt)

    private fun goalFrom(o: JSONObject) = SavingsGoalEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        targetAmountMinorUnits = o.getLong("targetAmountMinorUnits"),
        currencyCode = o.getString("currencyCode"),
        targetDate = o.longOrNull("targetDate"),
        accountId = o.stringOrNull("accountId"),
        trackingMethod = enumValueOf<GoalTrackingMethod>(o.getString("trackingMethod")),
        manualProgressMinorUnits = o.getLong("manualProgressMinorUnits"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt")
    )
}
