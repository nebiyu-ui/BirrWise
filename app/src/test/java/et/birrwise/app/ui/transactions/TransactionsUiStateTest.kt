package et.birrwise.app.ui.transactions

import et.birrwise.app.domain.Category
import et.birrwise.app.domain.CategoryType
import et.birrwise.app.domain.TransactionType.EXPENSE
import et.birrwise.app.domain.TransactionType.INCOME
import et.birrwise.app.domain.account
import et.birrwise.app.domain.at
import et.birrwise.app.domain.repository.TransactionsSnapshot
import et.birrwise.app.domain.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionsUiStateTest {

    @Test
    fun listsNewestFirstWithNames() {
        val state = transactionsUiState(
            accounts = listOf(account("A1").copy(name = "Main")),
            categories = listOf(Category(id = "c1", name = "Food", type = CategoryType.EXPENSE)),
            snapshot = TransactionsSnapshot(
                listOf(
                    tx("old", "A1", EXPENSE, 100L, occurredAt = at("2026-11-01T10:00:00Z"), categoryId = "c1"),
                    tx("new", "A1", INCOME, 200L, occurredAt = at("2026-11-05T10:00:00Z"))
                ),
                unreadableCount = 1
            )
        ) as TransactionsUiState.Loaded

        assertEquals(listOf("new", "old"), state.items.map { it.transaction.id })
        assertEquals("Main", state.items[0].accountName)
        assertEquals("Food", state.items[1].categoryName)
        assertEquals(1, state.unreadableCount)
        assertTrue(state.hasActiveAccounts)
    }

    @Test
    fun withOnlyArchivedAccountsThereIsNoActiveAccount() {
        val state = transactionsUiState(
            accounts = listOf(account("A1").copy(archived = true)),
            categories = emptyList(),
            snapshot = TransactionsSnapshot(emptyList(), 0)
        ) as TransactionsUiState.Loaded
        assertFalse(state.hasActiveAccounts)
        assertTrue(state.items.isEmpty())
    }
}
