package et.birrwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class ReportingPeriodsTest {

    private val addis: ZoneId = ZoneId.of("Africa/Addis_Ababa")

    @Test
    fun monthStartsAndEndsAtLocalMidnight() {
        val october = ReportingPeriods.month(YearMonth.of(2026, 10), addis)
        assertEquals(at("2026-09-30T21:00:00Z"), october.start)
        assertEquals(at("2026-10-31T21:00:00Z"), october.endExclusive)
    }

    @Test
    fun startIsIncludedAndEndIsExcluded() {
        val october = ReportingPeriods.month(YearMonth.of(2026, 10), addis)
        assertTrue(at("2026-09-30T21:00:00Z") in october)
        assertTrue(at("2026-10-31T20:59:59Z") in october)
        assertFalse(at("2026-10-31T21:00:00Z") in october)
        assertFalse(at("2026-09-30T20:59:59Z") in october)
    }

    @Test
    fun sameMomentCanBelongToDifferentMonthsInDifferentTimeZones() {
        val moment = at("2026-10-31T21:30:00Z")
        val octoberUtc = ReportingPeriods.month(YearMonth.of(2026, 10), ZoneOffset.UTC)
        val octoberAddis = ReportingPeriods.month(YearMonth.of(2026, 10), addis)
        val novemberAddis = ReportingPeriods.month(YearMonth.of(2026, 11), addis)
        assertTrue(moment in octoberUtc)
        assertFalse(moment in octoberAddis)
        assertTrue(moment in novemberAddis)
    }

    @Test
    fun leapYearFebruaryHasTwentyNineDays() {
        val february = ReportingPeriods.month(YearMonth.of(2028, 2), addis)
        assertEquals(29L, Duration.between(february.start, february.endExclusive).toDays())
    }

    @Test
    fun dayPeriod() {
        val day = ReportingPeriods.day(LocalDate.of(2026, 11, 3), addis)
        assertEquals(at("2026-11-02T21:00:00Z"), day.start)
        assertEquals(at("2026-11-03T21:00:00Z"), day.endExclusive)
    }

    @Test
    fun weekStartsOnMondayByDefault() {
        // 4 November 2026 is a Wednesday, so its week starts on Monday 2 November.
        val week = ReportingPeriods.week(LocalDate.of(2026, 11, 4), addis)
        assertEquals(at("2026-11-01T21:00:00Z"), week.start)
        assertEquals(at("2026-11-08T21:00:00Z"), week.endExclusive)
    }

    @Test
    fun weekCanStartOnSunday() {
        val week = ReportingPeriods.week(LocalDate.of(2026, 11, 4), addis, DayOfWeek.SUNDAY)
        assertEquals(at("2026-10-31T21:00:00Z"), week.start)
        assertEquals(at("2026-11-07T21:00:00Z"), week.endExclusive)
    }

    @Test
    fun yearPeriod() {
        val year = ReportingPeriods.year(2026, addis)
        assertEquals(at("2025-12-31T21:00:00Z"), year.start)
        assertEquals(at("2026-12-31T21:00:00Z"), year.endExclusive)
    }

    @Test
    fun periodMustEndAfterItStarts() {
        val moment = at("2026-01-01T00:00:00Z")
        assertThrows(IllegalArgumentException::class.java) { ReportingPeriod(moment, moment) }
    }
}
