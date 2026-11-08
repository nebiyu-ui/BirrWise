package et.birrwise.app.ui.accounts

import et.birrwise.app.domain.TransactionType.INCOME
import et.birrwise.app.domain.account
import et.birrwise.app.domain.etb
import et.birrwise.app.domain.repository.TransactionsSnapshot
import et.birrwise.app.domain.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsUiStateTest {

    @Test
    fun listSplitsActiveAndArchivedAndTotalsOnlyActive() {
        val state = accountsUiState(
            accounts = listOf(
                account("A1", openingBirr = 100),
                account("A2", openingBirr = 50).copy(archived = true)
            ),
            snapshot = TransactionsSnapshot(
                listOf(tx("t1", "A1", INCOME, 5_000L)),
                unreadableCount = 2
            )
        ) as AccountsUiState.Loaded

        assertEquals(listOf("A1"), state.active.map { it.account.id })
        assertEquals(listOf("A2"), state.archived.map { it.account.id })
        assertEquals(listOf(etb(150)), state.totals)
        assertEquals(2, state.unreadableTransactions)
    }

    @Test
    fun detailShowsTheAccountOrSaysItIsMissing() {
        val accounts = listOf(account("A1", openingBirr = 10))
        val snapshot = TransactionsSnapshot(emptyList(), unreadableCount = 0)

        val found = accountDetailUiState("A1", accounts, snapshot)
        assertTrue(found is AccountDetailUiState.Loaded)
        assertEquals(etb(10), (found as AccountDetailUiState.Loaded).summary.balance)

        assertEquals(AccountDetailUiState.NotFound, accountDetailUiState("nope", accounts, snapshot))
    }
}
