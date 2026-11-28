package et.birrwise.app.data.backup

import et.birrwise.app.data.db.AccountEntity
import et.birrwise.app.data.db.BudgetEntity
import et.birrwise.app.data.db.CategoryEntity
import et.birrwise.app.data.db.CategoryRuleEntity
import et.birrwise.app.data.db.ImportBatchEntity
import et.birrwise.app.data.db.PersonEntity
import et.birrwise.app.data.db.SavingsGoalEntity
import et.birrwise.app.data.db.TransactionEntity

/** Everything BirrWise stores, as one value. A backup file holds exactly this. */
data class BackupData(
    val formatVersion: Int,
    /** When the backup was made, in UTC epoch milliseconds. */
    val createdAt: Long,
    val appVersion: String,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val people: List<PersonEntity>,
    val importBatches: List<ImportBatchEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val categoryRules: List<CategoryRuleEntity>,
    val savingsGoals: List<SavingsGoalEntity>
) {
    fun summary(): BackupSummary = BackupSummary(
        createdAt = createdAt,
        appVersion = appVersion,
        accounts = accounts.size,
        transactions = transactions.size,
        categories = categories.size,
        budgets = budgets.size,
        people = people.size
    )

    companion object {
        const val CURRENT_FORMAT_VERSION = 1

        fun empty(createdAt: Long, appVersion: String) = BackupData(
            CURRENT_FORMAT_VERSION, createdAt, appVersion,
            emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList()
        )
    }
}

/** What a person sees about a backup before restoring it. */
data class BackupSummary(
    val createdAt: Long,
    val appVersion: String,
    val accounts: Int,
    val transactions: Int,
    val categories: Int,
    val budgets: Int,
    val people: Int
)

enum class BackupFailure {
    /** The file is not a BirrWise backup. */
    NOT_A_BACKUP,

    /** The backup was made by a newer version of the app. */
    UNSUPPORTED_VERSION,

    /** It is a backup, but damaged or inconsistent. */
    CORRUPT
}

class BackupException(val failure: BackupFailure) : Exception(failure.name)

/** The passphrase was wrong, or the encrypted file was changed after it was made. */
class WrongPassphraseException : Exception()
