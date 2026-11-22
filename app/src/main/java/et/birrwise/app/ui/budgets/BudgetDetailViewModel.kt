package et.birrwise.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import et.birrwise.app.AppContainer
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.Budget
import et.birrwise.app.domain.BudgetHistory
import et.birrwise.app.domain.BudgetMonth
import et.birrwise.app.domain.BudgetRules
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.repository.AccountRepository
import et.birrwise.app.domain.repository.BudgetRepository
import et.birrwise.app.domain.repository.CategoryRepository
import et.birrwise.app.domain.repository.TransactionRepository
import et.birrwise.app.domain.repository.TransactionsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

sealed interface BudgetDetailUiState {
    data object Loading : BudgetDetailUiState
    data object NotFound : BudgetDetailUiState
    data object Error : BudgetDetailUiState
    data class Loaded(
        val categoryName: String,
        /** The ongoing budget, or the paused one. */
        val budget: Budget,
        /** Newest first. */
        val history: List<BudgetMonth>
    ) : BudgetDetailUiState
}

internal fun budgetDetailUiState(
    categoryId: String,
    budgets: List<Budget>,
    categories: List<Category>,
    accounts: List<Account>,
    snapshot: TransactionsSnapshot,
    currentMonth: YearMonth,
    zone: ZoneId
): BudgetDetailUiState {
    val versions = budgets.filter { it.categoryId == categoryId }
    val current = BudgetRules.current(versions) ?: return BudgetDetailUiState.NotFound
    return BudgetDetailUiState.Loaded(
        categoryName = categories.firstOrNull { it.id == categoryId }?.name.orEmpty(),
        budget = current,
        history = BudgetHistory.build(versions, accounts, snapshot.transactions, currentMonth, zone)
    )
}

class BudgetDetailViewModel(
    private val categoryId: String,
    budgets: BudgetRepository,
    categories: CategoryRepository,
    accounts: AccountRepository,
    transactions: TransactionRepository,
    newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val upstream: Flow<BudgetDetailUiState> = combine(
        budgets.observeBudgets(),
        categories.observeCategories(),
        accounts.observeAccounts(true),
        transactions.observeTransactions()
    ) { budgetList, categoryList, accountList, snapshot ->
        budgetDetailUiState(
            categoryId, budgetList, categoryList, accountList, snapshot,
            YearMonth.now(clock), clock.zone
        )
    }

    val state: StateFlow<BudgetDetailUiState> = upstream
        .catch { emit(BudgetDetailUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetDetailUiState.Loading)

    private val lifecycle = BudgetLifecycle(budgets, newId, clock)

    /** Each of these returns true when the change was made. */
    suspend fun pause(): Boolean = lifecycle.pause(categoryId)

    suspend fun resume(): Boolean = lifecycle.resume(categoryId)

    /** Deletes the budget and its history for this category. */
    suspend fun delete(): Boolean = lifecycle.delete(categoryId)

    companion object {
        fun factory(container: AppContainer, categoryId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BudgetDetailViewModel(
                        categoryId,
                        container.budgetRepository,
                        container.categoryRepository,
                        container.accountRepository,
                        container.transactionRepository
                    ) as T
            }
    }
}
