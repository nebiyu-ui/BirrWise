package et.birrwise.app.ui.budgets

import et.birrwise.app.domain.Budget
import et.birrwise.app.domain.BudgetFormError
import et.birrwise.app.domain.BudgetFormInput
import et.birrwise.app.domain.BudgetRules
import et.birrwise.app.domain.BudgetStatus
import et.birrwise.app.domain.etb
import et.birrwise.app.ui.transactions.FakeCategoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class BudgetEditorViewModelTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-07T09:00:00Z"), addis)
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)

    private fun viewModel(
        repository: FakeBudgetRepository,
        categoryId: String? = null
    ) = BudgetEditorViewModel(
        categoryId = categoryId,
        budgets = repository,
        categories = FakeCategoryRepository(),
        newId = { "new-id" },
        clock = clock
    )

    private fun input(category: String? = "sys_food_groceries", limit: String = "5000", threshold: Int = 80) =
        BudgetFormInput(category, limit, threshold)

    private fun ongoing(category: String = "sys_food_groceries", start: YearMonth = october) = Budget(
        "b1", category, etb(5000), 80, BudgetRules.monthStart(start, addis), null, true
    )

    @Test
    fun creatingLoadsAllCategoriesAndNoBudgetsYet() = runBlocking<Unit> {
        val vm = viewModel(FakeBudgetRepository())
        vm.load()
        val ready = vm.load.value as BudgetEditorLoadState.Ready
        assertEquals(15, ready.allCategories.size)
        assertTrue(ready.categoriesWithBudget.isEmpty())
        assertEquals(null, ready.existing)
    }

    @Test
    fun savingANewBudgetStartsItThisMonth() = runBlocking<Unit> {
        val repository = FakeBudgetRepository()
        val vm = viewModel(repository)
        vm.load()
        vm.save(input(limit = "1,250.50", threshold = 90))

        assertEquals(BudgetEditorSaveState.Saved, vm.save.value)
        val saved = repository.budgets.single()
        assertEquals("sys_food_groceries", saved.categoryId)
        assertEquals(etb(1250, 50), saved.limit)
        assertEquals(90, saved.alertThresholdPercent)
        assertEquals(BudgetRules.monthStart(november, addis), saved.periodStart)
        assertEquals(BudgetStatus.ONGOING, saved.status)
    }

    @Test
    fun aCategoryWithABudgetCannotGetASecondOne() = runBlocking<Unit> {
        val repository = FakeBudgetRepository(listOf(ongoing()))
        val vm = viewModel(repository)
        vm.load()
        vm.save(input())
        assertEquals(
            BudgetEditorSaveState.Invalid(setOf(BudgetFormError.CATEGORY_HAS_BUDGET)),
            vm.save.value
        )
        assertEquals(1, repository.budgets.size)
    }

    @Test
    fun aBadFormSavesNothingAndCanBeFixed() = runBlocking<Unit> {
        val repository = FakeBudgetRepository()
        val vm = viewModel(repository)
        vm.load()

        vm.save(input(limit = "abc"))
        assertEquals(
            BudgetEditorSaveState.Invalid(setOf(BudgetFormError.LIMIT_INVALID)),
            vm.save.value
        )
        assertTrue(repository.budgets.isEmpty())

        vm.save(input(limit = "800"))
        assertEquals(BudgetEditorSaveState.Saved, vm.save.value)
    }

    @Test
    fun editingAnOlderBudgetKeepsTheOldLimitForEarlierMonths() = runBlocking<Unit> {
        val repository = FakeBudgetRepository(listOf(ongoing()))
        val vm = viewModel(repository, categoryId = "sys_food_groceries")
        vm.load()
        vm.save(input(limit = "6000", threshold = 70))

        assertEquals(BudgetEditorSaveState.Saved, vm.save.value)
        assertEquals(2, repository.budgets.size)
        val old = repository.budgets.first { it.id == "b1" }
        assertEquals(etb(5000), old.limit)
        assertEquals(BudgetStatus.ENDED, old.status)
        val current = BudgetRules.current(repository.budgets)!!
        assertEquals(etb(6000), current.limit)
        assertEquals(70, current.alertThresholdPercent)
    }

    @Test
    fun savingUnchangedTermsChangesNothing() = runBlocking<Unit> {
        val repository = FakeBudgetRepository(listOf(ongoing()))
        val vm = viewModel(repository, categoryId = "sys_food_groceries")
        vm.load()
        vm.save(input())
        assertEquals(BudgetEditorSaveState.Saved, vm.save.value)
        assertEquals(listOf(ongoing()), repository.budgets)
    }

    @Test
    fun aPausedOrMissingBudgetCannotBeEdited() = runBlocking<Unit> {
        val paused = ongoing().copy(active = false, periodEnd = BudgetRules.monthStart(november, addis))
        val pausedVm = viewModel(FakeBudgetRepository(listOf(paused)), categoryId = "sys_food_groceries")
        pausedVm.load()
        assertEquals(BudgetEditorLoadState.NotFound, pausedVm.load.value)

        val missingVm = viewModel(FakeBudgetRepository(), categoryId = "sys_fuel")
        missingVm.load()
        assertEquals(BudgetEditorLoadState.NotFound, missingVm.load.value)
    }

    @Test
    fun aWriteFailureMeansFailed() = runBlocking<Unit> {
        val repository = FakeBudgetRepository()
        repository.failWritesWith = RuntimeException("disk full")
        val vm = viewModel(repository)
        vm.load()
        vm.save(input())
        assertEquals(BudgetEditorSaveState.Failed, vm.save.value)
    }
}
