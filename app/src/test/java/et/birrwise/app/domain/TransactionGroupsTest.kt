package et.birrwise.app.domain

import et.birrwise.app.domain.TransactionType.EXPENSE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class TransactionGroupsTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")

    private val items = TransactionList.build(
        listOf(
            tx("A", "A1", EXPENSE, 100L, occurredAt = at("2026-11-04T09:00:00Z")),
            tx("B", "A1", EXPENSE, 200L, occurredAt = at("2026-11-04T10:00:00Z")),
            // 21:00 UTC is already the next day in Addis Ababa.
            tx("C", "A1", EXPENSE, 300L, occurredAt = at("2026-11-05T21:00:00Z")),
            tx("D", "A1", EXPENSE, 400L, occurredAt = at("2026-11-03T09:00:00Z"))
        ),
        listOf(account("A1")),
        emptyList()
    )

    private fun describe(rows: List<TransactionListRow>): List<String> = rows.map {
        when (it) {
            is TransactionListRow.DateHeader -> "H:" + it.date
            is TransactionListRow.Entry -> "E:" + it.item.transaction.id
        }
    }

    @Test
    fun newestFirstHasADayHeadingBeforeEachNewDay() {
        val rows = TransactionGroups.rows(items, TransactionSort.DATE_NEWEST, addis)
        assertEquals(
            listOf("H:2026-11-06", "E:C", "H:2026-11-04", "E:B", "E:A", "H:2026-11-03", "E:D"),
            describe(rows)
        )
    }

    @Test
    fun oldestFirstAlsoGroupsByDay() {
        val ordered = TransactionFilters.apply(
            items,
            TransactionFilter(sort = TransactionSort.DATE_OLDEST),
            addis
        )
        val rows = TransactionGroups.rows(ordered, TransactionSort.DATE_OLDEST, addis)
        assertEquals(
            listOf("H:2026-11-03", "E:D", "H:2026-11-04", "E:A", "E:B", "H:2026-11-06", "E:C"),
            describe(rows)
        )
    }

    @Test
    fun amountOrderHasNoHeadings() {
        val ordered = TransactionFilters.apply(
            items,
            TransactionFilter(sort = TransactionSort.AMOUNT_LARGEST),
            addis
        )
        val rows = TransactionGroups.rows(ordered, TransactionSort.AMOUNT_LARGEST, addis)
        assertEquals(listOf("E:D", "E:C", "E:B", "E:A"), describe(rows))
    }

    @Test
    fun anEmptyListHasNoRows() {
        assertTrue(TransactionGroups.rows(emptyList(), TransactionSort.DATE_NEWEST, addis).isEmpty())
    }
}
