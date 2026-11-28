package et.birrwise.app.data.backup

/**
 * Checks that a backup is consistent before anything is changed on the phone: ids are unique and
 * every row that points to another row points to one that is in the backup.
 */
object BackupValidator {

    fun validate(data: BackupData) {
        val accountIds = unique(data.accounts.map { it.id })
        val categoryIds = unique(data.categories.map { it.id })
        val personIds = unique(data.people.map { it.id })
        val batchIds = unique(data.importBatches.map { it.id })
        val transactionIds = unique(data.transactions.map { it.id })
        unique(data.budgets.map { it.id })
        unique(data.categoryRules.map { it.id })
        unique(data.savingsGoals.map { it.id })

        data.categories.forEach { require(it.parentCategoryId == null || it.parentCategoryId in categoryIds) }
        data.importBatches.forEach { require(it.accountId in accountIds) }
        data.transactions.forEach {
            require(it.accountId in accountIds)
            require(it.categoryId == null || it.categoryId in categoryIds)
            require(it.personId == null || it.personId in personIds)
            require(it.importBatchId == null || it.importBatchId in batchIds)
            require(it.reversesTransactionId == null || it.reversesTransactionId in transactionIds)
        }
        data.budgets.forEach { require(it.categoryId in categoryIds) }
        data.categoryRules.forEach {
            require(it.categoryId in categoryIds)
            require(it.accountId == null || it.accountId in accountIds)
        }
        data.savingsGoals.forEach { require(it.accountId == null || it.accountId in accountIds) }
    }

    /** Throws if an id appears twice. Returns the ids as a set. */
    private fun unique(ids: List<String>): Set<String> {
        val set = ids.toSet()
        require(set.size == ids.size) { "Duplicate ids" }
        return set
    }
}
