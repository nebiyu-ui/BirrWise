package et.birrwise.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import et.birrwise.app.AppContainer
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.TransactionFilter
import et.birrwise.app.domain.TransactionFilters
import et.birrwise.app.domain.TransactionGroups
import et.birrwise.app.domain.TransactionList
import et.birrwise.app.domain.TransactionListRow
import et.birrwise.app.domain.repository.AccountRepository
import et.birrwise.app.domain.repository.CategoryRepository
import et.birrwise.app.domain.repository.TransactionRepository
import et.birrwise.app.domain.repository.TransactionsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.ZoneId

sealed interface TransactionsUiState {
    data object Loading : TransactionsUiState
    data object Error : TransactionsUiState
    data class Loaded(
        /** The entries that match the filter, with day headings when ordered by date. */
        val rows: List<TransactionListRow>,
        val matchCount: Int,
        /** All entries, before filtering. */
        val totalCount: Int,
        val filter: TransactionFilter,
        val hasActiveAccounts: Boolean,
        val unreadableCount: Int,
        /** (id, name) pairs for the filter menus. */
        val accountOptions: List<Pair<String, String>>,
        val categoryOptions: List<Pair<String, String>>
    ) : TransactionsUiState
}

internal fun transactionsUiState(
    accounts: List<Account>,
    categories: List<Category>,
    snapshot: TransactionsSnapshot,
    filter: TransactionFilter,
    zone: ZoneId
): TransactionsUiState {
    val all = TransactionList.build(snapshot.transactions, accounts, categories)
    val matching = TransactionFilters.apply(all, filter, zone)
    return TransactionsUiState.Loaded(
        rows = TransactionGroups.rows(matching, filter.sort, zone),
        matchCount = matching.size,
        totalCount = all.size,
        filter = filter,
        hasActiveAccounts = accounts.any { !it.archived },
        unreadableCount = snapshot.unreadableCount,
        accountOptions = accounts.sortedBy { it.name.lowercase() }.map { it.id to it.name },
        categoryOptions = categories.sortedBy { it.name.lowercase() }.map { it.id to it.name }
    )
}

class TransactionsViewModel(
    accounts: AccountRepository,
    categories: CategoryRepository,
    transactions: TransactionRepository,
    private val filters: MutableStateFlow<TransactionFilter>,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val upstream: Flow<TransactionsUiState> = combine(
        accounts.observeAccounts(true),
        categories.observeCategories(),
        transactions.observeTransactions(),
        filters
    ) { accountList, categoryList, snapshot, filter ->
        transactionsUiState(accountList, categoryList, snapshot, filter, clock.zone)
    }

    val state: StateFlow<TransactionsUiState> = upstream
        .catch { emit(TransactionsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState.Loading)

    /** The search text when the screen opens. After that the screen keeps its own text. */
    fun currentQuery(): String = filters.value.query

    fun updateFilter(change: (TransactionFilter) -> TransactionFilter) {
        filters.update(change)
    }

    fun clearFilters() {
        filters.value = TransactionFilter()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TransactionsViewModel(
                        container.accountRepository,
                        container.categoryRepository,
                        container.transactionRepository,
                        container.transactionFilter
                    ) as T
            }
    }
}
