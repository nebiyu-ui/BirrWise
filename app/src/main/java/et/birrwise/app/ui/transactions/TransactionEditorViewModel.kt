package et.birrwise.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import et.birrwise.app.AppContainer
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.Transaction
import et.birrwise.app.domain.TransactionFormError
import et.birrwise.app.domain.TransactionFormInput
import et.birrwise.app.domain.TransactionFormResult
import et.birrwise.app.domain.TransactionFormValidator
import et.birrwise.app.domain.repository.AccountRepository
import et.birrwise.app.domain.repository.CategoryRepository
import et.birrwise.app.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.util.UUID

sealed interface TransactionEditorLoadState {
    data object Loading : TransactionEditorLoadState
    data object NotFound : TransactionEditorLoadState
    data object Failed : TransactionEditorLoadState
    data object NoAccounts : TransactionEditorLoadState
    data class Ready(
        /** Active accounts, plus the account of the transaction being viewed even if archived. */
        val accounts: List<Account>,
        val categories: List<Category>,
        /** Null when a new transaction is being entered. */
        val existing: Transaction?,
        /** Both legs when [existing] belongs to a transfer, otherwise empty. */
        val transferLegs: List<Transaction>,
        val presetAccountId: String?
    ) : TransactionEditorLoadState
}

sealed interface TransactionEditorSaveState {
    data object Idle : TransactionEditorSaveState
    data object Saving : TransactionEditorSaveState
    data object Saved : TransactionEditorSaveState
    data object Failed : TransactionEditorSaveState
    data class Invalid(val errors: Set<TransactionFormError>) : TransactionEditorSaveState
}

/**
 * Loads what the transaction form needs and saves or deletes one transaction. [load], [save], and
 * [delete] are suspend functions that the screen calls, so this class has no background work.
 */
class TransactionEditorViewModel(
    private val transactionId: String?,
    private val presetAccountId: String?,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _load = MutableStateFlow<TransactionEditorLoadState>(TransactionEditorLoadState.Loading)
    val load: StateFlow<TransactionEditorLoadState> = _load.asStateFlow()

    private val _save = MutableStateFlow<TransactionEditorSaveState>(TransactionEditorSaveState.Idle)
    val save: StateFlow<TransactionEditorSaveState> = _save.asStateFlow()

    suspend fun load() {
        if (_load.value is TransactionEditorLoadState.Ready) return
        _load.value = try {
            loadState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TransactionEditorLoadState.Failed
        }
    }

    private suspend fun loadState(): TransactionEditorLoadState {
        categories.ensureDefaultCategories()
        val existing = transactionId?.let { transactions.getTransaction(it) }
        if (transactionId != null && existing == null) return TransactionEditorLoadState.NotFound

        val usable = accounts.observeAccounts(true).first()
            .filter { !it.archived || it.id == existing?.accountId }
        if (usable.isEmpty()) return TransactionEditorLoadState.NoAccounts

        val group = existing?.transferGroupId
        val legs = if (group != null) {
            transactions.observeTransactions().first().transactions.filter { it.transferGroupId == group }
        } else {
            emptyList()
        }
        return TransactionEditorLoadState.Ready(
            accounts = usable,
            categories = categories.observeCategories().first(),
            existing = existing,
            transferLegs = legs,
            presetAccountId = presetAccountId
        )
    }

    suspend fun save(input: TransactionFormInput) {
        val current = _save.value
        if (current is TransactionEditorSaveState.Saving || current is TransactionEditorSaveState.Saved) return
        _save.value = TransactionEditorSaveState.Saving
        _save.value = try {
            doSave(input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TransactionEditorSaveState.Failed
        }
    }

    private suspend fun doSave(input: TransactionFormInput): TransactionEditorSaveState {
        val ready = _load.value as? TransactionEditorLoadState.Ready
            ?: return TransactionEditorSaveState.Failed
        val result = TransactionFormValidator.validate(
            input = input,
            accounts = ready.accounts.associateBy { it.id },
            categories = ready.categories.associateBy { it.id },
            newId = newId,
            now = clock.instant(),
            zone = clock.zone,
            existing = ready.existing
        )
        return when (result) {
            is TransactionFormResult.Invalid -> TransactionEditorSaveState.Invalid(result.errors)
            is TransactionFormResult.Single -> {
                if (ready.existing == null) {
                    transactions.addTransaction(result.transaction)
                } else {
                    transactions.updateTransaction(result.transaction)
                }
                TransactionEditorSaveState.Saved
            }
            is TransactionFormResult.Transfer -> {
                transactions.recordTransfer(result.outLeg, result.inLeg, result.fee)
                TransactionEditorSaveState.Saved
            }
        }
    }

    /** Deletes the transaction being viewed (both legs of a transfer). Returns true when done. */
    suspend fun delete(): Boolean {
        val id = transactionId ?: return false
        return try {
            transactions.deleteTransaction(id)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            transactionId: String?,
            presetAccountId: String?
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TransactionEditorViewModel(
                        transactionId,
                        presetAccountId,
                        container.accountRepository,
                        container.categoryRepository,
                        container.transactionRepository
                    ) as T
            }
    }
}
