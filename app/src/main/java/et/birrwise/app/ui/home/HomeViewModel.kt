package et.birrwise.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import et.birrwise.app.AppContainer
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.HomeMonths
import et.birrwise.app.domain.HomeOverview
import et.birrwise.app.domain.HomeOverviewBuilder
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
import java.time.Clock
import java.time.YearMonth

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Error : HomeUiState
    data class Loaded(
        val overview: HomeOverview,
        val canGoNext: Boolean
    ) : HomeUiState
}

internal fun homeUiState(
    accounts: List<Account>,
    categories: List<Category>,
    snapshot: TransactionsSnapshot,
    month: YearMonth,
    currentMonth: YearMonth,
    clock: Clock
): HomeUiState = HomeUiState.Loaded(
    overview = HomeOverviewBuilder.build(accounts, categories, snapshot, month, clock.zone),
    canGoNext = HomeMonths.canGoNext(month, currentMonth)
)

class HomeViewModel(
    accounts: AccountRepository,
    categories: CategoryRepository,
    transactions: TransactionRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(HomeMonths.current(clock))

    private val upstream: Flow<HomeUiState> = combine(
        accounts.observeAccounts(true),
        categories.observeCategories(),
        transactions.observeTransactions(),
        selectedMonth
    ) { accountList, categoryList, snapshot, month ->
        homeUiState(accountList, categoryList, snapshot, month, HomeMonths.current(clock), clock)
    }

    val state: StateFlow<HomeUiState> = upstream
        .catch { emit(HomeUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun previousMonth() {
        selectedMonth.value = HomeMonths.previous(selectedMonth.value)
    }

    fun nextMonth() {
        selectedMonth.value = HomeMonths.next(selectedMonth.value, HomeMonths.current(clock))
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(
                        container.accountRepository,
                        container.categoryRepository,
                        container.transactionRepository
                    ) as T
            }
    }
}
