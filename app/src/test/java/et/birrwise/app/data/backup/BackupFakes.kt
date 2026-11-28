package et.birrwise.app.data.backup

import java.io.IOException

/** An in-memory stand-in for the app's data. */
internal class FakeBackupRepository(var data: BackupData) : BackupRepository {

    var failReplace: Exception? = null

    override suspend fun snapshot(createdAt: Long, appVersion: String): BackupData =
        data.copy(createdAt = createdAt, appVersion = appVersion)

    override suspend fun replaceAll(data: BackupData) {
        failReplace?.let { throw it }
        this.data = data
    }
}

/** Files kept in memory. Failures and damage can be switched on. */
internal class FakeBackupFileStore : BackupFileStore {

    val files = mutableMapOf<String, ByteArray>()
    var failWrites = false
    var failReads = false
    var corruptWrites = false

    override suspend fun write(location: String, bytes: ByteArray) {
        if (failWrites) throw IOException("Disk full")
        files[location] = if (corruptWrites) bytes.copyOf(bytes.size / 2) else bytes
    }

    override suspend fun read(location: String): ByteArray {
        if (failReads) throw IOException("Cannot open")
        return files[location] ?: throw IOException("No such file")
    }
}

internal class FakeStatusStore : BackupStatusStore {
    private var last: Long? = null
    override fun lastBackupAt(): Long? = last
    override fun setLastBackupAt(millis: Long) {
        last = millis
    }
}
