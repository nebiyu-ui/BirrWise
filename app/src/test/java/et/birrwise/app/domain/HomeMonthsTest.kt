package et.birrwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class HomeMonthsTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")
    private val november = YearMonth.of(2026, 11)

    @Test
    fun previousGoesBackAcrossYears() {
        assertEquals(YearMonth.of(2026, 10), HomeMonths.previous(november))
        assertEquals(YearMonth.of(2025, 12), HomeMonths.previous(YearMonth.of(2026, 1)))
    }

    @Test
    fun theFutureIsNotOffered() {
        assertTrue(HomeMonths.canGoNext(YearMonth.of(2026, 10), november))
        assertFalse(HomeMonths.canGoNext(november, november))
        assertFalse(HomeMonths.canGoNext(YearMonth.of(2026, 12), november))
        assertEquals(november, HomeMonths.next(YearMonth.of(2026, 10), november))
        assertEquals(november, HomeMonths.next(november, november))
    }

    @Test
    fun currentMonthFollowsTheClockInItsZone() {
        val midday = Clock.fixed(Instant.parse("2026-11-07T09:00:00Z"), addis)
        assertEquals(november, HomeMonths.current(midday))
        // 21:30 UTC on 31 October is already November in Addis Ababa.
        val lateOctoberUtc = Clock.fixed(Instant.parse("2026-10-31T21:30:00Z"), addis)
        assertEquals(november, HomeMonths.current(lateOctoberUtc))
    }
}
