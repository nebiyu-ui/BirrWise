package et.birrwise.app.ui.home

import et.birrwise.app.domain.TransactionType.INCOME
import et.birrwise.app.domain.account
import et.birrwise.app.domain.at
import et.birrwise.app.domain.etb
import et.birrwise.app.domain.repository.TransactionsSnapshot
import et.birrwise.app.domain.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class HomeUiStateTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-07T09:00:00Z"), addis)
    private val november = YearMonth.of(2026, 11)

    private val snapshot = TransactionsSnapshot(
        listOf(tx("i1", "A1", INCOME, 100_000L, occurredAt = at("2026-11-03T09:00:00Z"))),
        unreadableCount = 0
    )

    private fun state(month: YearMonth) = homeUiState(
        accounts = listOf(account("A1", openingBirr = 100)),
        categories = emptyList(),
        snapshot = snapshot,
        month = month,
        currentMonth = november,
        clock = clock
    ) as HomeUiState.Loaded

    @Test
    fun currentMonthShowsItsTotalsAndCannotGoForward() {
        val loaded = state(november)
        assertEquals(etb(1000), loaded.overview.summary.income)
        assertFalse(loaded.canGoNext)
    }

    @Test
    fun anEarlierMonthCanGoForward() {
        val loaded = state(YearMonth.of(2026, 10))
        assertEquals(etb(0), loaded.overview.summary.income)
        assertTrue(loaded.canGoNext)
    }
}
