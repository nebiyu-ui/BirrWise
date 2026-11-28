package et.birrwise.app.data.backup

import androidx.room.withTransaction
import et.birrwise.app.data.db.BirrWiseDatabase

interface BackupRepository {
    /** Reads every table. */
    suspend fun snapshot(createdAt: Long, appVersion: String): BackupData

    /** Replaces everything in the app with [data], all at once or not at all. */
    suspend fun replaceAll(data: BackupData)
}

class RoomBackupRepository(private val database: BirrWiseDatabase) : BackupRepository {

    override suspend fun snapshot(createdAt: Long, appVersion: String): BackupData =
        database.withTransaction {
            BackupData(
                formatVersion = BackupData.CURRENT_FORMAT_VERSION,
                createdAt = createdAt,
                appVersion = appVersion,
                accounts = database.accountDao().getAll(),
                categories = database.categoryDao().getAll(),
                people = database.personDao().getAll(),
                importBatches = database.importBatchDao().getAll(),
                transactions = database.transactionDao().getAll(),
                budgets = database.budgetDao().getAll(),
                categoryRules = database.categoryRuleDao().getAll(),
                savingsGoals = database.savingsGoalDao().getAll()
            )
        }

    override suspend fun replaceAll(data: BackupData) {
        // The order matters: rows that others point to are deleted last and inserted first.
        database.withTransaction {
            database.categoryRuleDao().deleteAll()
            database.savingsGoalDao().deleteAll()
            database.budgetDao().deleteAll()
            database.transactionDao().clearReversalLinks()
            database.transactionDao().deleteAll()
            database.importBatchDao().deleteAll()
            database.personDao().deleteAll()
            database.categoryDao().clearParents()
            database.categoryDao().deleteAll()
            database.accountDao().deleteAll()

            data.accounts.forEach { database.accountDao().insert(it) }
            inDependencyOrder(data.categories, { it.id }, { it.parentCategoryId })
                .forEach { database.categoryDao().insert(it) }
            data.people.forEach { database.personDao().insert(it) }
            data.importBatches.forEach { database.importBatchDao().insert(it) }
            inDependencyOrder(data.transactions, { it.id }, { it.reversesTransactionId })
                .forEach { database.transactionDao().insert(it) }
            data.budgets.forEach { database.budgetDao().insert(it) }
            data.categoryRules.forEach { database.categoryRuleDao().insert(it) }
            data.savingsGoals.forEach { database.savingsGoalDao().insert(it) }
        }
    }

    companion object {
        /**
         * Puts rows that point to another row of the same table after the row they point to.
         * Throws if a row points to a missing row or the pointers form a loop.
         */
        internal fun <T> inDependencyOrder(
            items: List<T>,
            id: (T) -> String,
            parent: (T) -> String?
        ): List<T> {
            val placed = mutableSetOf<String>()
            val result = mutableListOf<T>()
            var remaining = items
            while (remaining.isNotEmpty()) {
                val (ready, waiting) = remaining.partition {
                    val p = parent(it)
                    p == null || p in placed
                }
                if (ready.isEmpty()) {
                    throw IllegalArgumentException("A row points to a missing row or the rows form a loop")
                }
                ready.forEach { placed += id(it) }
                result += ready
                remaining = waiting
            }
            return result
        }
    }
}
