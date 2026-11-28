package et.birrwise.app.data.backup

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupServiceTest {

    private val now = Instant.parse("2026-11-24T09:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sample = BackupSamples.full()
    private val place = "content://files/backup"
    private val passphrase = "correct horse battery".toCharArray()

    private val repository = FakeBackupRepository(sample)
    private val files = FakeBackupFileStore()
    private val status = FakeStatusStore()
    private val service = BackupService(repository, files, status, "0.9.0", clock)

    private fun expected() = sample.copy(createdAt = now.toEpochMilli(), appVersion = "0.9.0")

    // ---- export --------------------------------------------------------------------------

    @Test
    fun aPlainBackupHoldsReadableJsonAndRecordsWhenItWasMade() = runBlocking<Unit> {
        val summary = service.export(place, null)

        val bytes = files.files.getValue(place)
        assertFalse(BackupCrypto.isEncrypted(bytes))
        assertEquals(expected(), BackupCodec.decode(String(bytes, Charsets.UTF_8)))
        assertEquals(4, summary.transactions)
        assertEquals(2, summary.accounts)
        assertEquals(now.toEpochMilli(), status.lastBackupAt())
    }

    @Test
    fun aProtectedBackupIsEncryptedAndOpensOnlyWithThePassphrase() = runBlocking<Unit> {
        service.export(place, passphrase)
        val bytes = files.files.getValue(place)
        assertTrue(BackupCrypto.isEncrypted(bytes))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Laptop"))

        val opened = service.inspect(place, passphrase) as InspectResult.Ready
        assertEquals(expected(), opened.data)
    }

    @Test
    fun aFailedWriteIsNotReportedAsABackup() = runBlocking<Unit> {
        files.failWrites = true
        assertThrows(IOException::class.java) { runBlocking { service.export(place, null) } }
        assertNull(status.lastBackupAt())
    }

    @Test
    fun aFileThatWasNotWrittenCompletelyIsNotReportedAsABackup() = runBlocking<Unit> {
        files.corruptWrites = true
        assertThrows(IOException::class.java) { runBlocking { service.export(place, null) } }
        assertNull(status.lastBackupAt())
    }

    // ---- inspect -------------------------------------------------------------------------

    @Test
    fun inspectingAPlainBackupShowsWhatItHoldsAndWhatIsThereNow() = runBlocking<Unit> {
        service.export(place, null)
        repository.data = BackupData.empty(1L, "x")

        val ready = service.inspect(place, null) as InspectResult.Ready
        assertEquals(2, ready.summary.accounts)
        assertEquals(4, ready.summary.transactions)
        assertEquals(0, ready.current.accounts)
        assertEquals(now.toEpochMilli(), ready.summary.createdAt)
    }

    @Test
    fun anEncryptedBackupAsksForAPassphraseAndRejectsAWrongOne() = runBlocking<Unit> {
        service.export(place, passphrase)
        assertEquals(InspectResult.NeedsPassphrase, service.inspect(place, null))
        assertEquals(InspectResult.WrongPassphrase, service.inspect(place, "wrong passphrase".toCharArray()))
        assertTrue(service.inspect(place, passphrase) is InspectResult.Ready)
    }

    @Test
    fun anEncryptedBackupThatWasChangedIsRejected() = runBlocking<Unit> {
        service.export(place, passphrase)
        val bytes = files.files.getValue(place)
        bytes[bytes.size - 3] = (bytes[bytes.size - 3].toInt() xor 0x10).toByte()
        assertEquals(InspectResult.WrongPassphrase, service.inspect(place, passphrase))
    }

    @Test
    fun aFileThatIsNotABackupIsRefused() = runBlocking<Unit> {
        files.files[place] = "just some notes".toByteArray()
        assertEquals(InspectResult.Invalid(BackupFailure.NOT_A_BACKUP), service.inspect(place, null))
    }

    @Test
    fun aBackupFromANewerVersionIsRefused() = runBlocking<Unit> {
        val root = JSONObject(BackupCodec.encode(sample)).put("formatVersion", 99)
        files.files[place] = root.toString().toByteArray()
        assertEquals(InspectResult.Invalid(BackupFailure.UNSUPPORTED_VERSION), service.inspect(place, null))
    }

    @Test
    fun aDamagedBackupIsRefused() = runBlocking<Unit> {
        val root = JSONObject(BackupCodec.encode(sample))
        root.getJSONArray("transactions").getJSONObject(0).put("accountId", "nope")
        files.files[place] = root.toString().toByteArray()
        assertEquals(InspectResult.Invalid(BackupFailure.CORRUPT), service.inspect(place, null))
    }

    @Test
    fun aFileThatCannotBeOpenedIsReported() = runBlocking<Unit> {
        files.failReads = true
        assertEquals(InspectResult.Unreadable, service.inspect(place, null))
    }

    // ---- restore -------------------------------------------------------------------------

    @Test
    fun restoringReplacesTheDataWithTheCheckedBackup() = runBlocking<Unit> {
        service.export(place, null)
        repository.data = BackupData.empty(1L, "x")

        val ready = service.inspect(place, null) as InspectResult.Ready
        service.restore(ready.data)

        assertEquals(sample.transactions, repository.data.transactions)
        assertEquals(sample.accounts, repository.data.accounts)
    }
}
