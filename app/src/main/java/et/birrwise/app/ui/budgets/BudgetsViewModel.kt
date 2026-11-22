package et.birrwise.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import et.birrwise.app.AppContainer
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.Budget
import et.birrwise.app.domain.BudgetOverview
import et.birrwise.app.domain.BudgetOverviewBuilder
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.HomeMonths
import et.birrwise.app.domain.repository.AccountRepository
import et.birrwise.app.domain.repository.BudgetRepository
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
import java.time.ZoneId

sealed interface BudgetsUiState {
    data object Loading : BudgetsUiState
    data object Error : BudgetsUiState
    data class Loaded(
        val overview: BudgetOverview,
        val canGoNext: Boolean
    ) : BudgetsUiState
}

internal fun budgetsUiState(
    budgets: List<Budget>,
    categories: List<Category>,
    accounts: List<Account>,
    snapshot: TransactionsSnapshot,
    month: YearMonth,
    currentMonth: YearMonth,
    zone: ZoneId
): BudgetsUiState = BudgetsUiState.Loaded(
    overview = BudgetOverviewBuilder.build(budgets, categories, accounts, snapshot, month, zone),
    canGoNext = HomeMonths.canGoNext(month, currentMonth)
)

class BudgetsViewModel(
    budgets: BudgetRepository,
    categories: CategoryRepository,
    accounts: AccountRepository,
    transactions: TransactionRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(HomeMonths.current(clock))

    private val upstream: Flow<BudgetsUiState> = combine(
        budgets.observeBudgets(),
        categories.observeCategories(),
        accounts.observeAccounts(true),
        transactions.observeTransactions(),
        selectedMonth
    ) { budgetList, categoryList, accountList, snapshot, month ->
        budgetsUiState(
            budgetList, categoryList, accountList, snapshot,
            month, HomeMonths.current(clock), clock.zone
        )
    }

    val state: StateFlow<BudgetsUiState> = upstream
        .catch { emit(BudgetsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState.Loading)

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
                    BudgetsViewModel(
                        container.budgetRepository,
                        container.categoryRepository,
                        container.accountRepository,
                        container.transactionRepository
                    ) as T
            }
    }
}
