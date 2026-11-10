package et.birrwise.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import et.birrwise.app.AppContainer
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.AccountSummaries
import et.birrwise.app.domain.AccountSummary
import et.birrwise.app.domain.TransactionList
import et.birrwise.app.domain.TransactionListItem
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

/** How many of an account's latest transactions the detail screen lists. */
internal const val RECENT_TRANSACTIONS_LIMIT = 50

sealed interface AccountDetailUiState {
    data object Loading : AccountDetailUiState
    data object NotFound : AccountDetailUiState
    data object Error : AccountDetailUiState
    data class Loaded(
        val summary: AccountSummary,
        val unreadableTransactions: Int,
        /** The account's latest transactions, newest first. */
        val recent: List<TransactionListItem>,
        /** How many transactions the account has in all. */
        val totalTransactions: Int
    ) : AccountDetailUiState
}

internal fun accountDetailUiState(
    accountId: String,
    accounts: List<Account>,
    snapshot: TransactionsSnapshot
): AccountDetailUiState {
    val account = accounts.firstOrNull { it.id == accountId } ?: return AccountDetailUiState.NotFound
    val summary = AccountSummaries.build(listOf(account), snapshot.transactions).first()
    val own = snapshot.transactions.filter { it.accountId == accountId }
    val items = TransactionList.build(own, listOf(account), emptyList())
    return AccountDetailUiState.Loaded(
        summary = summary,
        unreadableTransactions = snapshot.unreadableCount,
        recent = items.take(RECENT_TRANSACTIONS_LIMIT),
        totalTransactions = items.size
    )
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
