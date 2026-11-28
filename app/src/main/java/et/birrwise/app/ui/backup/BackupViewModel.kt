package et.birrwise.app.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import et.birrwise.app.AppContainer
import et.birrwise.app.data.backup.BackupData
import et.birrwise.app.data.backup.BackupFailure
import et.birrwise.app.data.backup.BackupService
import et.birrwise.app.data.backup.BackupSummary
import et.birrwise.app.data.backup.InspectResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class BackupMessage {
    EXPORT_DONE,
    EXPORT_FAILED,
    RESTORE_DONE,
    RESTORE_FAILED,
    NOT_A_BACKUP,
    UNSUPPORTED_VERSION,
    CORRUPT,
    UNREADABLE
}

/** A checked backup waiting for the person to confirm that it replaces what is in the app. */
data class RestorePreview(
    val data: BackupData,
    val summary: BackupSummary,
    val current: BackupSummary
)

data class BackupUiState(
    val lastBackupAt: Long? = null,
    val busy: Boolean = false,
    val message: BackupMessage? = null,
    /** What was just saved, when [message] is [BackupMessage.EXPORT_DONE]. */
    val exportedSummary: BackupSummary? = null,
    /** True while the person has to type the passphrase of an encrypted backup. */
    val askingPassphrase: Boolean = false,
    val wrongPassphrase: Boolean = false,
    val preview: RestorePreview? = null
)

/**
 * Drives the backup screen. Its functions are suspend functions that the screen calls, so this
 * class does no background work of its own.
 */
class BackupViewModel(private val service: BackupService) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState(lastBackupAt = service.lastBackupAt()))
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** The encrypted file that is waiting for its passphrase. */
    private var pendingLocation: String? = null

    suspend fun export(location: String, passphrase: CharArray?) {
        _state.update { it.copy(busy = true, message = null, exportedSummary = null) }
        val summary: BackupSummary? = try {
            service.export(location, passphrase)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _state.update {
            if (summary != null) {
                it.copy(
                    busy = false,
                    message = BackupMessage.EXPORT_DONE,
                    exportedSummary = summary,
                    lastBackupAt = service.lastBackupAt()
                )
            } else {
                it.copy(busy = false, message = BackupMessage.EXPORT_FAILED)
            }
        }
    }

    /** Starts a restore: reads and checks the file, asking for a passphrase if it needs one. */
    suspend fun openBackup(location: String) {
        _state.update { it.copy(busy = true, message = null, exportedSummary = null) }
        handle(location, service.inspect(location, null))
    }

    suspend fun submitPassphrase(passphrase: CharArray) {
        val location = pendingLocation ?: return
        _state.update { it.copy(busy = true, wrongPassphrase = false) }
        handle(location, service.inspect(location, passphrase))
    }

    private fun handle(location: String, result: InspectResult) {
        when (result) {
            is InspectResult.NeedsPassphrase -> {
                pendingLocation = location
                _state.update { it.copy(busy = false, askingPassphrase = true, wrongPassphrase = false) }
            }
            is InspectResult.WrongPassphrase -> {
                pendingLocation = location
                _state.update { it.copy(busy = false, askingPassphrase = true, wrongPassphrase = true) }
            }
            is InspectResult.Unreadable -> fail(BackupMessage.UNREADABLE)
            is InspectResult.Invalid -> fail(
                when (result.failure) {
                    BackupFailure.NOT_A_BACKUP -> BackupMessage.NOT_A_BACKUP
                    BackupFailure.UNSUPPORTED_VERSION -> BackupMessage.UNSUPPORTED_VERSION
                    BackupFailure.CORRUPT -> BackupMessage.CORRUPT
                }
            )
            is InspectResult.Ready -> {
                pendingLocation = null
                _state.update {
                    it.copy(
                        busy = false,
                        askingPassphrase = false,
                        wrongPassphrase = false,
                        preview = RestorePreview(result.data, result.summary, result.current)
                    )
                }
            }
        }
    }

    private fun fail(message: BackupMessage) {
        pendingLocation = null
        _state.update {
            it.copy(busy = false, askingPassphrase = false, wrongPassphrase = false, message = message)
        }
    }

    fun cancelRestore() {
        pendingLocation = null
        _state.update { it.copy(askingPassphrase = false, wrongPassphrase = false, preview = null) }
    }

    /** Replaces everything in the app with the previewed backup. */
    suspend fun confirmRestore() {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(busy = true, preview = null) }
        val done = try {
            service.restore(preview.data)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        _state.update {
            it.copy(
                busy = false,
                message = if (done) BackupMessage.RESTORE_DONE else BackupMessage.RESTORE_FAILED
            )
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null, exportedSummary = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BackupViewModel(container.backupService) as T
            }
    }
}
