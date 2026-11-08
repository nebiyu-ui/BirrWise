package et.birrwise.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import et.birrwise.app.AppContainer
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.AccountFormError
import et.birrwise.app.domain.AccountFormInput
import et.birrwise.app.domain.AccountFormResult
import et.birrwise.app.domain.AccountFormValidator
import et.birrwise.app.domain.repository.AccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.UUID

sealed interface EditorLoadState {
    data object Loading : EditorLoadState
    data object NotFound : EditorLoadState
    data object Failed : EditorLoadState
    /** [existing] is null when a new account is being created. */
    data class Ready(val existing: Account?) : EditorLoadState
}

sealed interface EditorSaveState {
    data object Idle : EditorSaveState
    data object Saving : EditorSaveState
    data object Saved : EditorSaveState
    data object Failed : EditorSaveState
    data object CurrencyLocked : EditorSaveState
    data class Invalid(val errors: Set<AccountFormError>) : EditorSaveState
}

/**
 * Loads and saves one account for the form. [load] and [save] are suspend functions that the screen
 * calls, so this class has no background work of its own and is easy to test.
 */
class AccountEditorViewModel(
    private val accountId: String?,
    private val accounts: AccountRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _load = MutableStateFlow<EditorLoadState>(EditorLoadState.Loading)
    val load: StateFlow<EditorLoadState> = _load.asStateFlow()

    private val _save = MutableStateFlow<EditorSaveState>(EditorSaveState.Idle)
    val save: StateFlow<EditorSaveState> = _save.asStateFlow()

    suspend fun load() {
        if (_load.value is EditorLoadState.Ready) return
        if (accountId == null) {
            _load.value = EditorLoadState.Ready(null)
            return
        }
        _load.value = try {
            val existing = accounts.getAccount(accountId)
            if (existing == null) EditorLoadState.NotFound else EditorLoadState.Ready(existing)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EditorLoadState.Failed
        }
    }

    suspend fun save(input: AccountFormInput) {
        val current = _save.value
        if (current is EditorSaveState.Saving || current is EditorSaveState.Saved) return
        _save.value = EditorSaveState.Saving
        _save.value = try {
            val existing = (_load.value as? EditorLoadState.Ready)?.existing
            val otherNames = accounts.observeAccounts(true).first()
                .filter { it.id != existing?.id }
                .map { it.name }
            val result = AccountFormValidator.validate(
                id = existing?.id ?: newId(),
                input = input,
                otherAccountNames = otherNames,
                zone = zone,
                archived = existing?.archived ?: false
            )
            when (result) {
                is AccountFormResult.Invalid -> EditorSaveState.Invalid(result.errors)
                is AccountFormResult.Valid -> {
                    if (existing == null) {
                        accounts.createAccount(result.account)
                    } else {
                        accounts.updateAccount(result.account)
                    }
                    EditorSaveState.Saved
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            EditorSaveState.CurrencyLocked
        } catch (e: Exception) {
            EditorSaveState.Failed
        }
    }

    companion object {
        fun factory(container: AppContainer, accountId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AccountEditorViewModel(accountId, container.accountRepository) as T
            }
    }
}
