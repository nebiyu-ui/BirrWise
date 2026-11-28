package et.birrwise.app.data.backup

import org.junit.Assert.assertThrows
import org.junit.Test

class BackupValidatorTest {

    private val sample = BackupSamples.full()

    private fun assertRejected(data: BackupData) {
        assertThrows(IllegalArgumentException::class.java) { BackupValidator.validate(data) }
    }

    @Test
    fun aConsistentBackupPasses() {
        BackupValidator.validate(sample)
        BackupValidator.validate(BackupData.empty(1L, "x"))
    }

    @Test
    fun duplicateIdsAreRejected() {
        assertRejected(sample.copy(accounts = sample.accounts + sample.accounts.first()))
        assertRejected(sample.copy(transactions = sample.transactions + sample.transactions.first()))
        assertRejected(sample.copy(budgets = sample.budgets + sample.budgets.first()))
    }

    @Test
    fun transactionsMustPointToRowsThatExist() {
        val first = sample.transactions.first()
        assertRejected(sample.copy(transactions = listOf(first.copy(accountId = "nope"))))
        assertRejected(sample.copy(transactions = listOf(first.copy(categoryId = "nope"))))
        assertRejected(sample.copy(transactions = listOf(first.copy(personId = "nope"))))
        assertRejected(sample.copy(transactions = listOf(first.copy(importBatchId = "nope"))))
        assertRejected(sample.copy(transactions = listOf(first.copy(reversesTransactionId = "nope"))))
    }

    @Test
    fun otherTablesMustPointToRowsThatExist() {
        assertRejected(sample.copy(importBatches = listOf(sample.importBatches.first().copy(accountId = "nope"))))
        assertRejected(sample.copy(budgets = listOf(sample.budgets.first().copy(categoryId = "nope"))))
        assertRejected(sample.copy(categoryRules = listOf(sample.categoryRules.first().copy(categoryId = "nope"))))
        assertRejected(sample.copy(categoryRules = listOf(sample.categoryRules.first().copy(accountId = "nope"))))
        assertRejected(sample.copy(savingsGoals = listOf(sample.savingsGoals.first().copy(accountId = "nope"))))
        assertRejected(sample.copy(categories = listOf(sample.categories.last().copy(parentCategoryId = "nope"))))
    }
}
