package et.birrwise.app.domain

import et.birrwise.app.domain.TransactionType.EXPENSE
import et.birrwise.app.domain.TransactionType.INCOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionListTest {

    @Test
    fun newestFirstWithAccountAndCategoryNames() {
        val accounts = listOf(account("A1").copy(name = "Main"), account("A2").copy(name = "Cash"))
        val categories = listOf(Category(id = "c1", name = "Food", type = CategoryType.EXPENSE))
        val transactions = listOf(
            tx("old", "A1", EXPENSE, 100L, occurredAt = at("2026-11-01T10:00:00Z"), categoryId = null),
            tx("new", "A2", INCOME, 200L, occurredAt = at("2026-11-05T10:00:00Z")),
            tx("mid", "A1", EXPENSE, 300L, occurredAt = at("2026-11-03T10:00:00Z"), categoryId = "c1")
        )

        val items = TransactionList.build(transactions, accounts, categories)

        assertEquals(listOf("new", "mid", "old"), items.map { it.transaction.id })
        assertEquals("Cash", items[0].accountName)
        assertEquals("Main", items[1].accountName)
        assertEquals("Food", items[1].categoryName)
        assertNull(items[0].categoryName)
    }

    @Test
    fun transactionsAtTheSameMomentKeepAStableOrder() {
        val moment = at("2026-11-05T10:00:00Z")
        val transactions = listOf(
            tx("b", "A1", EXPENSE, 100L, occurredAt = moment),
            tx("a", "A1", EXPENSE, 100L, occurredAt = moment)
        )
        val ids = TransactionList.build(transactions, listOf(account("A1")), emptyList()).map { it.transaction.id }
        assertEquals(listOf("a", "b"), ids)
    }

    @Test
    fun unknownAccountOrCategoryGivesEmptyNames() {
        val items = TransactionList.build(
            listOf(tx("t", "gone", EXPENSE, 100L, categoryId = "missing")),
            emptyList(),
            emptyList()
        )
        assertEquals("", items.single().accountName)
        assertNull(items.single().categoryName)
    }
}
