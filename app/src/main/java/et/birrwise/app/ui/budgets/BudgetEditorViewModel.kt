package et.birrwise.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import et.birrwise.app.AppContainer
import et.birrwise.app.domain.Budget
import et.birrwise.app.domain.BudgetChange
import et.birrwise.app.domain.BudgetFormError
import et.birrwise.app.domain.BudgetFormInput
import et.birrwise.app.domain.BudgetFormResult
import et.birrwise.app.domain.BudgetFormValidator
import et.birrwise.app.domain.BudgetRules
import et.birrwise.app.domain.BudgetStatus
import et.birrwise.app.domain.Category
import et.birrwise.app.domain.repository.BudgetRepository
import et.birrwise.app.domain.repository.CategoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.YearMonth
import java.util.UUID

sealed interface BudgetEditorLoadState {
    data object Loading : BudgetEditorLoadState
    data object NotFound : BudgetEditorLoadState
    data object Failed : BudgetEditorLoadState
    data class Ready(
        val allCategories: List<Category>,
        /** Categories that already have an ongoing or paused budget. */
        val categoriesWithBudget: Set<String>,
        /** Null when a new budget is being created. */
        val existing: Budget?
    ) : BudgetEditorLoadState
}

sealed interface BudgetEditorSaveState {
    data object Idle : BudgetEditorSaveState
    data object Saving : BudgetEditorSaveState
    data object Saved : BudgetEditorSaveState
    data object Failed : BudgetEditorSaveState
    data class Invalid(val errors: Set<BudgetFormError>) : BudgetEditorSaveState
}

/**
 * Loads what the budget form needs and saves a new budget or new terms for an ongoing one.
 * [load] and [save] are suspend functions that the screen calls.
 */
class BudgetEditorViewModel(
    private val categoryId: String?,
    private val budgets: BudgetRepository,
    private val categories: CategoryRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _load = MutableStateFlow<BudgetEditorLoadState>(BudgetEditorLoadState.Loading)
    val load: StateFlow<BudgetEditorLoadState> = _load.asStateFlow()

    private val _save = MutableStateFlow<BudgetEditorSaveState>(BudgetEditorSaveState.Idle)
    val save: StateFlow<BudgetEditorSaveState> = _save.asStateFlow()

    suspend fun load() {
        if (_load.value is BudgetEditorLoadState.Ready) return
        _load.value = try {
            loadState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BudgetEditorLoadState.Failed
        }
    }

    private suspend fun loadState(): BudgetEditorLoadState {
        categories.ensureDefaultCategories()
        val allCategories = categories.observeCategories().first()
        val everyBudget = budgets.observeBudgets().first()
        val withBudget = everyBudget
            .filter { it.status != BudgetStatus.ENDED }
            .map { it.categoryId }
            .toSet()

        if (categoryId == null) {
            return BudgetEditorLoadState.Ready(allCategories, withBudget, null)
        }
        val current = BudgetRules.current(everyBudget.filter { it.categoryId == categoryId })
        if (current == null || current.status != BudgetStatus.ONGOING) return BudgetEditorLoadState.NotFound
        return BudgetEditorLoadState.Ready(allCategories, withBudget, current)
    }

    suspend fun save(input: BudgetFormInput) {
        val current = _save.value
        if (current is BudgetEditorSaveState.Saving || current is BudgetEditorSaveState.Saved) return
        _save.value = BudgetEditorSaveState.Saving
        _save.value = try {
            doSave(input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BudgetEditorSaveState.Failed
        }
    }

    private suspend fun doSave(input: BudgetFormInput): BudgetEditorSaveState {
        val ready = _load.value as? BudgetEditorLoadState.Ready ?: return BudgetEditorSaveState.Failed
        val existing = ready.existing
        val result = BudgetFormValidator.validate(
            input = input,
            categories = ready.allCategories.associateBy { it.id },
            categoriesWithBudget = ready.categoriesWithBudget,
            isEditing = existing != null
        )
        return when (result) {
            is BudgetFormResult.Invalid -> BudgetEditorSaveState.Invalid(result.errors)
            is BudgetFormResult.Valid -> {
                val month = YearMonth.now(clock)
                val changes: List<BudgetChange> = if (existing == null) {
                    listOf(
                        BudgetChange.Insert(
                            BudgetRules.create(
                                newId(), result.categoryId, result.limit,
                                result.thresholdPercent, month, clock.zone
                            )
                        )
                    )
                } else {
                    BudgetRules.changeTerms(
                        existing, result.limit, result.thresholdPercent,
                        newId(), month, clock.zone
                    )
                }
                budgets.apply(changes)
                BudgetEditorSaveState.Saved
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, categoryId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BudgetEditorViewModel(
                        categoryId,
                        container.budgetRepository,
                        container.categoryRepository
                    ) as T
            }
    }
}
