package et.birrwise.app.ui.transactions

import et.birrwise.app.domain.Category
import et.birrwise.app.domain.CategoryType
import et.birrwise.app.domain.TransactionFilter
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
import java.time.ZoneId

class TransactionsUiStateTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")

    private val accounts = listOf(account("A1").copy(name = "Main"), account("A2").copy(name = "cash"))
    private val categories = listOf(
        Category(id = "c1", name = "Food", type = CategoryType.EXPENSE),
        Category(id = "c2", name = "bonus", type = CategoryType.INCOME)
    )
    private val snapshot = TransactionsSnapshot(
        listOf(
            tx("t1", "A1", EXPENSE, 100L, occurredAt = at("2026-11-04T09:00:00Z"), categoryId = "c1"),
            tx("t2", "A2", INCOME, 200L, occurredAt = at("2026-11-03T09:00:00Z"), categoryId = "c2"),
            tx("t3", "A1", EXPENSE, 300L, occurredAt = at("2026-11-04T10:00:00Z"))
        ),
        unreadableCount = 1
    )

    private fun state(
        filter: TransactionFilter = TransactionFilter(),
        accountList: List<et.birrwise.app.domain.Account> = accounts
    ) = transactionsUiState(accountList, categories, snapshot, filter, addis) as TransactionsUiState.Loaded

    @Test
    fun withoutAFilterEverythingIsListedWithDayHeadings() {
        val loaded = state()
        assertEquals(3, loaded.matchCount)
        assertEquals(3, loaded.totalCount)
        // Two day headings plus three entries.
        assertEquals(5, loaded.rows.size)
        assertEquals(1, loaded.unreadableCount)
        assertTrue(loaded.hasActiveAccounts)
    }

    @Test
    fun aFilterReducesTheMatchCountButNotTheTotal() {
        val filter = TransactionFilter(accountId = "A2")
        val loaded = state(filter)
        assertEquals(1, loaded.matchCount)
        assertEquals(3, loaded.totalCount)
        // One heading and one entry.
        assertEquals(2, loaded.rows.size)
        assertEquals(filter, loaded.filter)
    }

    @Test
    fun menuOptionsAreSortedByNameIgnoringCase() {
        val loaded = state()
        assertEquals(listOf("cash", "Main"), loaded.accountOptions.map { it.second })
        assertEquals(listOf("bonus", "Food"), loaded.categoryOptions.map { it.second })
    }

    @Test
    fun onlyArchivedAccountsMeansNoActiveAccount() {
        val loaded = state(accountList = listOf(accounts[0].copy(archived = true)))
        assertFalse(loaded.hasActiveAccounts)
    }
}
