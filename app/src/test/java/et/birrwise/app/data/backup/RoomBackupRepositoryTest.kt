package et.birrwise.app.data.backup

import android.database.sqlite.SQLiteException
import et.birrwise.app.data.DatabaseTestBase
import et.birrwise.app.data.db.AccountEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomBackupRepositoryTest : DatabaseTestBase() {

    private val repository by lazy { RoomBackupRepository(db) }
    private val sample = BackupSamples.full()

    private suspend fun snapshot() = repository.snapshot(sample.createdAt, sample.appVersion)

    private fun assertSameContent(expected: BackupData, actual: BackupData) {
        assertEquals(expected.accounts.sortedBy { it.id }, actual.accounts.sortedBy { it.id })
        assertEquals(expected.categories.sortedBy { it.id }, actual.categories.sortedBy { it.id })
        assertEquals(expected.people.sortedBy { it.id }, actual.people.sortedBy { it.id })
        assertEquals(expected.importBatches.sortedBy { it.id }, actual.importBatches.sortedBy { it.id })
        assertEquals(expected.transactions.sortedBy { it.id }, actual.transactions.sortedBy { it.id })
        assertEquals(expected.budgets.sortedBy { it.id }, actual.budgets.sortedBy { it.id })
        assertEquals(expected.categoryRules.sortedBy { it.id }, actual.categoryRules.sortedBy { it.id })
        assertEquals(expected.savingsGoals.sortedBy { it.id }, actual.savingsGoals.sortedBy { it.id })
    }

    @Test
    fun aBackupRestoresIntoAnEmptyApp() = runBlocking<Unit> {
        repository.replaceAll(sample)
        assertSameContent(sample, snapshot())
    }

    @Test
    fun restoringReplacesWhateverWasThereBefore() = runBlocking<Unit> {
        repository.replaceAll(sample)
        db.accountDao().insert(
            AccountEntity(
                id = "extra", name = "Extra", provider = null,
                accountType = et.birrwise.app.domain.AccountType.OTHER, currencyCode = "ETB",
                openingBalanceMinorUnits = 0L, openingBalanceDate = null, archived = false,
                createdAt = 1L, updatedAt = 1L, notes = null
            )
        )
        repository.replaceAll(sample)
        assertSameContent(sample, snapshot())
    }

    @Test
    fun restoringAnEmptyBackupClearsEveryTable() = runBlocking<Unit> {
        repository.replaceAll(sample)
        repository.replaceAll(BackupData.empty(1L, "x"))
        val snap = snapshot()
        assertTrue(snap.accounts.isEmpty())
        assertTrue(snap.categories.isEmpty())
        assertTrue(snap.people.isEmpty())
        assertTrue(snap.importBatches.isEmpty())
        assertTrue(snap.transactions.isEmpty())
        assertTrue(snap.budgets.isEmpty())
        assertTrue(snap.categoryRules.isEmpty())
        assertTrue(snap.savingsGoals.isEmpty())
    }

    @Test
    fun rowsThatPointToEachOtherCanBeInAnyOrderInTheFile() = runBlocking<Unit> {
        val scrambled = sample.copy(
            categories = sample.categories.reversed(),
            transactions = sample.transactions.reversed()
        )
        repository.replaceAll(scrambled)
        assertSameContent(sample, snapshot())
    }

    @Test
    fun aBrokenBackupChangesNothing() = runBlocking<Unit> {
        repository.replaceAll(sample)
        val broken = sample.copy(
            accounts = emptyList(),
            transactions = sample.transactions.take(1),
            importBatches = emptyList(),
            budgets = emptyList(),
            categoryRules = emptyList(),
            savingsGoals = emptyList()
        )
        assertThrows(SQLiteException::class.java) {
            runBlocking { repository.replaceAll(broken) }
        }
        assertSameContent(sample, snapshot())
    }

    @Test
    fun aLoopOfCategoriesIsRefusedAndChangesNothing() = runBlocking<Unit> {
        repository.replaceAll(sample)
        val loop = sample.copy(
            categories = listOf(
                sample.categories[0].copy(parentCategoryId = "c-child"),
                sample.categories[1].copy(parentCategoryId = "c-parent")
            ),
            transactions = emptyList(),
            budgets = emptyList(),
            categoryRules = emptyList(),
            importBatches = emptyList()
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.replaceAll(loop) }
        }
        assertSameContent(sample, snapshot())
    }
}
