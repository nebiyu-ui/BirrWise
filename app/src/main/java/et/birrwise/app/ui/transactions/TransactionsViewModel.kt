package et.birrwise.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import et.birrwise.app.AppContainer
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.TransactionList
import et.birrwise.app.domain.TransactionListItem
import et.birrwise.app.domain.repository.AccountRepository
import et.birrwise.app.domain.repository.CategoryRepository
import et.birrwise.app.domain.repository.TransactionRepository
import et.birrwise.app.domain.repository.TransactionsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface TransactionsUiState {
    data object Loading : TransactionsUiState
    data object Error : TransactionsUiState
    data class Loaded(
        val items: List<TransactionListItem>,
        val hasActiveAccounts: Boolean,
        val unreadableCount: Int
    ) : TransactionsUiState
}

internal fun transactionsUiState(
    accounts: List<Account>,
    categories: List<Category>,
    snapshot: TransactionsSnapshot
): TransactionsUiState = TransactionsUiState.Loaded(
    items = TransactionList.build(snapshot.transactions, accounts, categories),
    hasActiveAccounts = accounts.any { !it.archived },
    unreadableCount = snapshot.unreadableCount
)

class TransactionsViewModel(
    accounts: AccountRepository,
    categories: CategoryRepository,
    transactions: TransactionRepository
) : ViewModel() {

    private val upstream: Flow<TransactionsUiState> = combine(
        accounts.observeAccounts(true),
        categories.observeCategories(),
        transactions.observeTransactions()
    ) { accountList, categoryList, snapshot ->
        transactionsUiState(accountList, categoryList, snapshot)
    }

    val state: StateFlow<TransactionsUiState> = upstream
        .catch { emit(TransactionsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState.Loading)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TransactionsViewModel(
                        container.accountRepository,
                        container.categoryRepository,
                        container.transactionRepository
                    ) as T
            }
    }
}
