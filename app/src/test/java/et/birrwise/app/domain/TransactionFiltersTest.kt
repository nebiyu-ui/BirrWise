package et.birrwise.app.domain

import et.birrwise.app.domain.TransactionStatus.PENDING_REVIEW
import et.birrwise.app.domain.TransactionType.EXPENSE
import et.birrwise.app.domain.TransactionType.FEE
import et.birrwise.app.domain.TransactionType.INCOME
import et.birrwise.app.domain.TransactionType.REFUND
import et.birrwise.app.domain.TransactionType.REVERSAL
import et.birrwise.app.domain.TransactionType.TRANSFER_IN
import et.birrwise.app.domain.TransactionType.TRANSFER_OUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TransactionFiltersTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")

    private val accounts = listOf(account("A1").copy(name = "Main"), account("A2").copy(name = "Cash"))
    private val categories = listOf(
        Category(id = "food", name = "Food", type = CategoryType.EXPENSE),
        Category(id = "pay", name = "Pay", type = CategoryType.INCOME)
    )

    private val entries = listOf(
        tx("e1", "A1", EXPENSE, 25_000L, occurredAt = at("2026-11-04T09:00:00Z"), categoryId = "food")
            .copy(description = "Lunch at Cafe", counterparty = "Sara"),
        tx("i1", "A1", INCOME, 100_000L, occurredAt = at("2026-11-03T09:00:00Z"), categoryId = "pay")
            .copy(description = "Salary", notes = "November pay"),
        tx("f1", "A2", FEE, 500L, occurredAt = at("2026-11-05T09:00:00Z"))
            .copy(merchantName = "CBE Bank"),
        tx("r1", "A2", REFUND, 4_000L, occurredAt = at("2026-11-06T09:00:00Z"), categoryId = "food"),
        tx("out", "A1", TRANSFER_OUT, 30_000L, occurredAt = at("2026-11-07T09:00:00Z"), transferGroupId = "g"),
        tx("in", "A2", TRANSFER_IN, 30_000L, occurredAt = at("2026-11-07T09:00:00Z"), transferGroupId = "g"),
        tx("p1", "A2", EXPENSE, 7_000L, occurredAt = at("2026-11-02T09:00:00Z"), status = PENDING_REVIEW),
        tx("v1", "A1", REVERSAL, 1_000L, occurredAt = at("2026-11-08T09:00:00Z"), reversesTransactionId = "e1")
    )

    private val items = TransactionList.build(entries, accounts, categories)

    private fun ids(filter: TransactionFilter): List<String> =
        TransactionFilters.apply(items, filter, addis).map { it.transaction.id }

    @Test
    fun noFilterKeepsEverythingNewestFirst() {
        assertEquals(
            listOf("v1", "in", "out", "r1", "f1", "e1", "i1", "p1"),
            ids(TransactionFilter())
        )
    }

    @Test
    fun searchLooksInDescriptionPersonMerchantAndNotesIgnoringCase() {
        assertEquals(listOf("e1"), ids(TransactionFilter(query = "lunch")))
        assertEquals(listOf("e1"), ids(TransactionFilter(query = "  SARA ")))
        assertEquals(listOf("f1"), ids(TransactionFilter(query = "cbe")))
        assertEquals(listOf("i1"), ids(TransactionFilter(query = "november")))
        assertEquals(emptyList<String>(), ids(TransactionFilter(query = "nothing like this")))
    }

    @Test
    fun aBlankSearchMatchesEverything() {
        assertEquals(8, ids(TransactionFilter(query = "   ")).size)
    }

    @Test
    fun filtersByAccount() {
        assertEquals(listOf("v1", "out", "e1", "i1"), ids(TransactionFilter(accountId = "A1")))
    }

    @Test
    fun filtersByCategoryAndUncategorized() {
        assertEquals(listOf("r1", "e1"), ids(TransactionFilter(categoryId = "food")))
        assertEquals(listOf("i1"), ids(TransactionFilter(categoryId = "pay")))
        assertEquals(
            listOf("v1", "in", "out", "f1", "p1"),
            ids(TransactionFilter(categoryId = TransactionFilter.UNCATEGORIZED))
        )
    }

    @Test
    fun filtersByKindAndATransferMatchesBothSides() {
        assertEquals(listOf("e1", "p1"), ids(TransactionFilter(kind = EntryKind.EXPENSE)))
        assertEquals(listOf("i1"), ids(TransactionFilter(kind = EntryKind.INCOME)))
        assertEquals(listOf("f1"), ids(TransactionFilter(kind = EntryKind.FEE)))
        assertEquals(listOf("r1"), ids(TransactionFilter(kind = EntryKind.REFUND)))
        assertEquals(listOf("in", "out"), ids(TransactionFilter(kind = EntryKind.TRANSFER)))
    }

    @Test
    fun needsReviewOnlyShowsPendingEntries() {
        assertEquals(listOf("p1"), ids(TransactionFilter(reviewOnly = true)))
    }

    @Test
    fun dateRangeIncludesBothEndDaysInLocalTime() {
        val range = TransactionFilter(
            fromDate = LocalDate.of(2026, 11, 3),
            toDate = LocalDate.of(2026, 11, 5)
        )
        assertEquals(listOf("f1", "e1", "i1"), ids(range))
    }

    @Test
    fun theDayBoundaryFollowsTheTimeZone() {
        // 21:00 UTC on 5 November is already 6 November in Addis Ababa.
        val lateEntry = TransactionList.build(
            listOf(tx("late", "A1", EXPENSE, 100L, occurredAt = at("2026-11-05T21:00:00Z"))),
            accounts,
            categories
        )
        val on5th = TransactionFilter(fromDate = LocalDate.of(2026, 11, 5), toDate = LocalDate.of(2026, 11, 5))
        val on6th = TransactionFilter(fromDate = LocalDate.of(2026, 11, 6), toDate = LocalDate.of(2026, 11, 6))
        assertTrue(TransactionFilters.apply(lateEntry, on5th, addis).isEmpty())
        assertEquals(1, TransactionFilters.apply(lateEntry, on6th, addis).size)
    }

    @Test
    fun anOpenEndedRangeWorks() {
        assertEquals(
            listOf("v1", "in", "out"),
            ids(TransactionFilter(fromDate = LocalDate.of(2026, 11, 7)))
        )
        assertEquals(
            listOf("i1", "p1"),
            ids(TransactionFilter(toDate = LocalDate.of(2026, 11, 3)))
        )
    }

    @Test
    fun filtersCombine() {
        assertEquals(
            listOf("e1"),
            ids(TransactionFilter(accountId = "A1", kind = EntryKind.EXPENSE, query = "lunch"))
        )
        assertEquals(
            emptyList<String>(),
            ids(TransactionFilter(accountId = "A2", categoryId = "pay"))
        )
    }

    @Test
    fun oldestFirstOrdersByDateAscending() {
        assertEquals(
            listOf("p1", "i1", "e1", "f1", "r1", "in", "out", "v1"),
            ids(TransactionFilter(sort = TransactionSort.DATE_OLDEST))
        )
    }

    @Test
    fun amountOrdersBreakTiesByNewestThenId() {
        assertEquals(
            listOf("i1", "in", "out", "e1", "p1", "r1", "v1", "f1"),
            ids(TransactionFilter(sort = TransactionSort.AMOUNT_LARGEST))
        )
        assertEquals(
            listOf("f1", "v1", "r1", "p1", "e1", "in", "out", "i1"),
            ids(TransactionFilter(sort = TransactionSort.AMOUNT_SMALLEST))
        )
    }

    @Test
    fun activeFilterCountIgnoresTheSortOrder() {
        assertEquals(0, TransactionFilter(sort = TransactionSort.AMOUNT_LARGEST).activeCount)
        assertFalse(TransactionFilter(query = "  ").isActive)
        val many = TransactionFilter(
            query = "x",
            accountId = "A1",
            categoryId = "food",
            kind = EntryKind.FEE,
            fromDate = LocalDate.of(2026, 1, 1),
            toDate = LocalDate.of(2026, 2, 1),
            reviewOnly = true
        )
        assertEquals(7, many.activeCount)
        assertTrue(many.isActive)
    }
}
