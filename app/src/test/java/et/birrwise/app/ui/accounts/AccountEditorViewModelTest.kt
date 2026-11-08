package et.birrwise.app.ui.accounts

import et.birrwise.app.domain.Account
import et.birrwise.app.domain.AccountFormError
import et.birrwise.app.domain.AccountFormInput
import et.birrwise.app.domain.AccountType
import et.birrwise.app.domain.ETB
import et.birrwise.app.domain.Money
import et.birrwise.app.domain.account
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AccountEditorViewModelTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")

    private fun input(name: String = "Main", amount: String = "100") = AccountFormInput(
        name = name,
        provider = "",
        type = AccountType.BANK,
        currencyCode = ETB,
        openingBalanceText = amount,
        openingDate = LocalDate.of(2026, 11, 7),
        notes = ""
    )

    private fun viewModel(repository: FakeAccountRepository, accountId: String? = null) =
        AccountEditorViewModel(accountId, repository, newId = { "new-1" }, zone = addis)

    @Test
    fun newAccountLoadsAsReadyWithNothingExisting() = runBlocking<Unit> {
        val vm = viewModel(FakeAccountRepository())
        vm.load()
        assertEquals(EditorLoadState.Ready(null), vm.load.value)
    }

    @Test
    fun existingAccountLoadsAndUnknownOneIsNotFound() = runBlocking<Unit> {
        val existing = account("A1", openingBirr = 5)
        val repository = FakeAccountRepository(listOf(existing))

        val found = viewModel(repository, "A1")
        found.load()
        assertEquals(EditorLoadState.Ready(existing), found.load.value)

        val missing = viewModel(repository, "nope")
        missing.load()
        assertEquals(EditorLoadState.NotFound, missing.load.value)
    }

    @Test
    fun readFailureMeansFailedNotACrash() = runBlocking<Unit> {
        val repository = FakeAccountRepository(listOf(account("A1")))
        repository.failReadWith = RuntimeException("disk problem")
        val vm = viewModel(repository, "A1")
        vm.load()
        assertEquals(EditorLoadState.Failed, vm.load.value)
    }

    @Test
    fun savingANewAccountCreatesIt() = runBlocking<Unit> {
        val repository = FakeAccountRepository()
        val vm = viewModel(repository)
        vm.load()
        vm.save(input())

        assertEquals(EditorSaveState.Saved, vm.save.value)
        val saved = repository.getAccount("new-1")!!
        assertEquals("Main", saved.name)
        assertEquals(Money(10_000L, ETB), saved.openingBalance)
    }

    @Test
    fun duplicateNameIsRejectedAndNothingIsSaved() = runBlocking<Unit> {
        val repository = FakeAccountRepository(listOf(account("A0").copy(name = "Main")))
        val vm = viewModel(repository)
        vm.load()
        vm.save(input(name = "main"))

        assertEquals(
            EditorSaveState.Invalid(setOf(AccountFormError.NAME_TAKEN)),
            vm.save.value
        )
        assertEquals(1, repository.accounts.size)
    }

    @Test
    fun aRejectedFormCanBeFixedAndSaved() = runBlocking<Unit> {
        val repository = FakeAccountRepository()
        val vm = viewModel(repository)
        vm.load()

        vm.save(input(name = ""))
        assertEquals(
            EditorSaveState.Invalid(setOf(AccountFormError.NAME_REQUIRED)),
            vm.save.value
        )

        vm.save(input(name = "Cash wallet"))
        assertEquals(EditorSaveState.Saved, vm.save.value)
        assertEquals(1, repository.accounts.size)
    }

    @Test
    fun editingKeepsTheOwnNameAndTheArchivedFlag() = runBlocking<Unit> {
        val existing: Account = account("A1", openingBirr = 5).copy(name = "Main", archived = true)
        val repository = FakeAccountRepository(listOf(existing))
        val vm = viewModel(repository, "A1")
        vm.load()
        vm.save(input(name = "Main", amount = "100"))

        assertEquals(EditorSaveState.Saved, vm.save.value)
        val updated = repository.getAccount("A1")!!
        assertEquals(true, updated.archived)
        assertEquals(Money(10_000L, ETB), updated.openingBalance)
        assertEquals(1, repository.accounts.size)
    }

    @Test
    fun savingTwiceDoesNotCreateTwoAccounts() = runBlocking<Unit> {
        val repository = FakeAccountRepository()
        val vm = viewModel(repository)
        vm.load()
        vm.save(input())
        vm.save(input(name = "Another"))
        assertEquals(1, repository.accounts.size)
    }

    @Test
    fun updateRefusedForStateReasonsMeansCurrencyLocked() = runBlocking<Unit> {
        val repository = FakeAccountRepository(listOf(account("A1")))
        repository.failUpdateWith = IllegalStateException("has transactions")
        val vm = viewModel(repository, "A1")
        vm.load()
        vm.save(input(name = "Renamed"))
        assertEquals(EditorSaveState.CurrencyLocked, vm.save.value)
    }

    @Test
    fun otherFailuresMeanFailed() = runBlocking<Unit> {
        val repository = FakeAccountRepository()
        repository.failCreateWith = RuntimeException("disk full")
        val vm = viewModel(repository)
        vm.load()
        vm.save(input())
        assertEquals(EditorSaveState.Failed, vm.save.value)
    }
}
