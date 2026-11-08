package et.birrwise.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import et.birrwise.app.AppContainer
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.AccountSummaries
import et.birrwise.app.domain.AccountSummary
import et.birrwise.app.domain.Money
import et.birrwise.app.domain.repository.AccountRepository
import et.birrwise.app.domain.repository.TransactionRepository
import et.birrwise.app.domain.repository.TransactionsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface AccountsUiState {
    data object Loading : AccountsUiState
    data object Error : AccountsUiState
    data class Loaded(
        val active: List<AccountSummary>,
        val archived: List<AccountSummary>,
        val totals: List<Money>,
        val unreadableTransactions: Int
    ) : AccountsUiState
}

/** Builds what the accounts list shows. Kept separate from the view model so it can be tested. */
internal fun accountsUiState(accounts: List<Account>, snapshot: TransactionsSnapshot): AccountsUiState {
    val summaries = AccountSummaries.build(accounts, snapshot.transactions)
    return AccountsUiState.Loaded(
        active = summaries.filter { !it.account.archived },
        archived = summaries.filter { it.account.archived },
        totals = AccountSummaries.totalsByCurrency(summaries),
        unreadableTransactions = snapshot.unreadableCount
    )
}

class AccountsViewModel(
    accounts: AccountRepository,
    transactions: TransactionRepository
) : ViewModel() {

    private val upstream: Flow<AccountsUiState> =
        combine(accounts.observeAccounts(true), transactions.observeTransactions()) { list, snapshot ->
            accountsUiState(list, snapshot)
        }

    val state: StateFlow<AccountsUiState> = upstream
        .catch { emit(AccountsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState.Loading)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AccountsViewModel(container.accountRepository, container.transactionRepository) as T
            }
    }
}
