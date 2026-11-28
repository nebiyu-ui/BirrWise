package et.birrwise.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Reads and writes files chosen with the system file picker, so no storage permission is needed. */
class ContentResolverFileStore(private val resolver: ContentResolver) : BackupFileStore {

    override suspend fun write(location: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val stream = resolver.openOutputStream(Uri.parse(location), "wt")
                ?: throw IOException("Could not open the file for writing")
            stream.use { it.write(bytes) }
        }
    }

    override suspend fun read(location: String): ByteArray =
        withContext(Dispatchers.IO) {
            val stream = resolver.openInputStream(Uri.parse(location))
                ?: throw IOException("Could not open the file")
            stream.use { it.readBytes() }
        }
}

class SharedPreferencesStatusStore(context: Context) : BackupStatusStore {

    private val preferences =
        context.applicationContext.getSharedPreferences("birrwise_backup", Context.MODE_PRIVATE)

    override fun lastBackupAt(): Long? =
        preferences.getLong(KEY_LAST_BACKUP, NONE).takeIf { it != NONE }

    override fun setLastBackupAt(millis: Long) {
        preferences.edit().putLong(KEY_LAST_BACKUP, millis).apply()
    }

    private companion object {
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val NONE = -1L
    }
}
