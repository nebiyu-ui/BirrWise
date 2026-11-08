package et.birrwise.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import et.birrwise.app.AppContainer
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.AccountSummaries
import et.birrwise.app.domain.AccountSummary
import et.birrwise.app.domain.repository.AccountRepository
import et.birrwise.app.domain.repository.TransactionRepository
import et.birrwise.app.domain.repository.TransactionsSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface AccountDetailUiState {
    data object Loading : AccountDetailUiState
    data object NotFound : AccountDetailUiState
    data object Error : AccountDetailUiState
    data class Loaded(
        val summary: AccountSummary,
        val unreadableTransactions: Int
    ) : AccountDetailUiState
}

internal fun accountDetailUiState(
    accountId: String,
    accounts: List<Account>,
    snapshot: TransactionsSnapshot
): AccountDetailUiState {
    val account = accounts.firstOrNull { it.id == accountId } ?: return AccountDetailUiState.NotFound
    val summary = AccountSummaries.build(listOf(account), snapshot.transactions).first()
    return AccountDetailUiState.Loaded(summary, snapshot.unreadableCount)
}

class AccountDetailViewModel(
    private val accountId: String,
    private val accounts: AccountRepository,
    transactions: TransactionRepository
) : ViewModel() {

    private val upstream: Flow<AccountDetailUiState> =
        combine(accounts.observeAccounts(true), transactions.observeTransactions()) { list, snapshot ->
            accountDetailUiState(accountId, list, snapshot)
        }

    val state: StateFlow<AccountDetailUiState> = upstream
        .catch { emit(AccountDetailUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUiState.Loading)

    /** Returns true when the change was saved. */
    suspend fun setArchived(archived: Boolean): Boolean =
        try {
            accounts.setArchived(accountId, archived)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    companion object {
        fun factory(container: AppContainer, accountId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AccountDetailViewModel(
                        accountId,
                        container.accountRepository,
                        container.transactionRepository
                    ) as T
            }
    }
}
