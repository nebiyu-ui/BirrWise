package et.birrwise.app.ui.backup

import et.birrwise.app.data.backup.BackupCodec
import et.birrwise.app.data.backup.BackupData
import et.birrwise.app.data.backup.BackupSamples
import et.birrwise.app.data.backup.BackupService
import et.birrwise.app.data.backup.FakeBackupFileStore
import et.birrwise.app.data.backup.FakeBackupRepository
import et.birrwise.app.data.backup.FakeStatusStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupViewModelTest {

    private val now = Instant.parse("2026-11-24T09:00:00Z")
    private val sample = BackupSamples.full()
    private val place = "content://files/backup"
    private val passphrase = "correct horse battery".toCharArray()

    private val repository = FakeBackupRepository(sample)
    private val files = FakeBackupFileStore()
    private val status = FakeStatusStore()
    private val viewModel = BackupViewModel(
        BackupService(repository, files, status, "0.9.0", Clock.fixed(now, ZoneOffset.UTC))
    )

    @Test
    fun theScreenStartsWithTheLastBackupTime() {
        status.setLastBackupAt(123L)
        val fresh = BackupViewModel(
            BackupService(repository, files, status, "0.9.0", Clock.fixed(now, ZoneOffset.UTC))
        )
        assertEquals(123L, fresh.state.value.lastBackupAt)
        assertNull(viewModel.state.value.lastBackupAt)
    }

    @Test
    fun aSuccessfulBackupShowsWhatWasSavedAndWhen() = runBlocking<Unit> {
        viewModel.export(place, null)
        val state = viewModel.state.value
        assertEquals(BackupMessage.EXPORT_DONE, state.message)
        assertEquals(4, state.exportedSummary?.transactions)
        assertEquals(now.toEpochMilli(), state.lastBackupAt)
        assertFalse(state.busy)
    }

    @Test
    fun aFailedBackupSaysSoAndKeepsTheOldTime() = runBlocking<Unit> {
        files.failWrites = true
        viewModel.export(place, null)
        val state = viewModel.state.value
        assertEquals(BackupMessage.EXPORT_FAILED, state.message)
        assertNull(state.lastBackupAt)
        assertFalse(state.busy)
    }

    @Test
    fun openingAPlainBackupShowsAPreviewBeforeAnythingChanges() = runBlocking<Unit> {
        viewModel.export(place, null)
        repository.data = BackupData.empty(1L, "x")

        viewModel.openBackup(place)

        val preview = viewModel.state.value.preview
        assertNotNull(preview)
        assertEquals(2, preview!!.summary.accounts)
        assertEquals(0, preview.current.accounts)
        // Nothing has been replaced yet.
        assertTrue(repository.data.accounts.isEmpty())
    }

    @Test
    fun anEncryptedBackupAsksForThePassphraseThenShowsThePreview() = runBlocking<Unit> {
        viewModel.export(place, passphrase)

        viewModel.openBackup(place)
        assertTrue(viewModel.state.value.askingPassphrase)
        assertNull(viewModel.state.value.preview)

        viewModel.submitPassphrase("wrong passphrase".toCharArray())
        assertTrue(viewModel.state.value.askingPassphrase)
        assertTrue(viewModel.state.value.wrongPassphrase)

        viewModel.submitPassphrase(passphrase)
        assertFalse(viewModel.state.value.askingPassphrase)
        assertNotNull(viewModel.state.value.preview)
    }

    @Test
    fun cancellingForgetsThePreview() = runBlocking<Unit> {
        viewModel.export(place, null)
        viewModel.openBackup(place)
        viewModel.cancelRestore()
        assertNull(viewModel.state.value.preview)
        assertFalse(viewModel.state.value.askingPassphrase)
    }

    @Test
    fun confirmingReplacesTheDataAndSaysSo() = runBlocking<Unit> {
        viewModel.export(place, null)
        repository.data = BackupData.empty(1L, "x")
        viewModel.openBackup(place)

        viewModel.confirmRestore()

        assertEquals(sample.transactions, repository.data.transactions)
        assertEquals(BackupMessage.RESTORE_DONE, viewModel.state.value.message)
        assertNull(viewModel.state.value.preview)
    }

    @Test
    fun aFailedRestoreSaysSo() = runBlocking<Unit> {
        viewModel.export(place, null)
        viewModel.openBackup(place)
        repository.failReplace = RuntimeException("disk full")

        viewModel.confirmRestore()

        assertEquals(BackupMessage.RESTORE_FAILED, viewModel.state.value.message)
    }

    @Test
    fun confirmingWithoutAPreviewDoesNothing() = runBlocking<Unit> {
        viewModel.confirmRestore()
        assertNull(viewModel.state.value.message)
        assertEquals(sample, repository.data)
    }

    @Test
    fun badFilesProduceClearMessages() = runBlocking<Unit> {
        files.files[place] = "notes".toByteArray()
        viewModel.openBackup(place)
        assertEquals(BackupMessage.NOT_A_BACKUP, viewModel.state.value.message)

        val newer = JSONObject(BackupCodec.encode(sample)).put("formatVersion", 99)
        files.files[place] = newer.toString().toByteArray()
        viewModel.openBackup(place)
        assertEquals(BackupMessage.UNSUPPORTED_VERSION, viewModel.state.value.message)

        val damaged = JSONObject(BackupCodec.encode(sample))
        damaged.getJSONArray("transactions").getJSONObject(0).put("accountId", "nope")
        files.files[place] = damaged.toString().toByteArray()
        viewModel.openBackup(place)
        assertEquals(BackupMessage.CORRUPT, viewModel.state.value.message)

        files.failReads = true
        viewModel.openBackup(place)
        assertEquals(BackupMessage.UNREADABLE, viewModel.state.value.message)
        assertFalse(viewModel.state.value.busy)
    }

    @Test
    fun dismissingClearsTheMessage() = runBlocking<Unit> {
        viewModel.export(place, null)
        viewModel.dismissMessage()
        assertNull(viewModel.state.value.message)
        assertNull(viewModel.state.value.exportedSummary)
    }
}
