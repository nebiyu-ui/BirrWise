package et.birrwise.app.ui.accounts

import et.birrwise.app.domain.Account
import et.birrwise.app.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory account repository for tests. Failures can be switched on to test error handling. */
class FakeAccountRepository(initial: List<Account> = emptyList()) : AccountRepository {

    private val state = MutableStateFlow(initial)

    var failReadWith: Exception? = null
    var failCreateWith: Exception? = null
    var failUpdateWith: Exception? = null

    val accounts: List<Account> get() = state.value

    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> =
        state.map { list -> if (includeArchived) list else list.filter { !it.archived } }

    override suspend fun getAccount(id: String): Account? {
        failReadWith?.let { throw it }
        return state.value.firstOrNull { it.id == id }
    }

    override suspend fun createAccount(account: Account) {
        failCreateWith?.let { throw it }
        state.value = state.value + account
    }

    override suspend fun updateAccount(account: Account) {
        failUpdateWith?.let { throw it }
        state.value = state.value.map { if (it.id == account.id) account else it }
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(archived = archived) else it }
    }
}
