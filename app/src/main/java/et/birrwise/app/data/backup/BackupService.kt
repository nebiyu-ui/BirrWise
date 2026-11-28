package et.birrwise.app.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock

/** Reads and writes the backup file the person chose. [location] is the file's address. */
interface BackupFileStore {
    suspend fun write(location: String, bytes: ByteArray)
    suspend fun read(location: String): ByteArray
}

/** Remembers when the last backup was made. */
interface BackupStatusStore {
    fun lastBackupAt(): Long?
    fun setLastBackupAt(millis: Long)
}

sealed interface InspectResult {
    data object NeedsPassphrase : InspectResult
    data object WrongPassphrase : InspectResult
    data object Unreadable : InspectResult
    data class Invalid(val failure: BackupFailure) : InspectResult
    data class Ready(
        val data: BackupData,
        val summary: BackupSummary,
        /** What is in the app right now, which a restore would replace. */
        val current: BackupSummary
    ) : InspectResult
}

class BackupService(
    private val repository: BackupRepository,
    private val files: BackupFileStore,
    private val status: BackupStatusStore,
    private val appVersion: String,
    private val clock: Clock = Clock.systemUTC()
) {

    fun lastBackupAt(): Long? = status.lastBackupAt()

    /**
     * Saves a backup to [location], protected by [passphrase] when one is given. The file is read
     * back and compared before success is reported, so a backup that was not written properly is
     * never presented as done. Throws if anything goes wrong.
     */
    suspend fun export(location: String, passphrase: CharArray?): BackupSummary {
        val data = repository.snapshot(clock.millis(), appVersion)
        val bytes = withContext(Dispatchers.Default) {
            val plain = BackupCodec.encode(data).toByteArray(Charsets.UTF_8)
            if (passphrase != null) BackupCrypto.encrypt(plain, passphrase) else plain
        }
        files.write(location, bytes)
        val written = files.read(location)
        if (!written.contentEquals(bytes)) throw IOException("The saved file does not match the backup")
        status.setLastBackupAt(data.createdAt)
        return data.summary()
    }

    /** Opens a backup file and checks it without changing anything in the app. */
    suspend fun inspect(location: String, passphrase: CharArray?): InspectResult {
        val bytes = try {
            files.read(location)
        } catch (e: IOException) {
            return InspectResult.Unreadable
        } catch (e: SecurityException) {
            return InspectResult.Unreadable
        }
        return try {
            val text = withContext(Dispatchers.Default) {
                val plain = if (BackupCrypto.isEncrypted(bytes)) {
                    if (passphrase == null) return@withContext null
                    BackupCrypto.decrypt(bytes, passphrase)
                } else {
                    bytes
                }
                String(plain, Charsets.UTF_8)
            } ?: return InspectResult.NeedsPassphrase
            val data = BackupCodec.decode(text)
            val current = repository.snapshot(clock.millis(), appVersion).summary()
            InspectResult.Ready(data, data.summary(), current)
        } catch (e: WrongPassphraseException) {
            InspectResult.WrongPassphrase
        } catch (e: BackupException) {
            InspectResult.Invalid(e.failure)
        }
    }

    /** Replaces everything in the app with the checked backup. */
    suspend fun restore(data: BackupData) {
        repository.replaceAll(data)
    }
}
