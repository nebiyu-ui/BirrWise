package et.birrwise.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A span of time that includes [start] and excludes [endExclusive]. */
data class ReportingPeriod(val start: Instant, val endExclusive: Instant) {

    init {
        require(start < endExclusive) { "A reporting period must end after it starts" }
    }

    operator fun contains(instant: Instant): Boolean = instant >= start && instant < endExclusive
}

/**
 * Builds calendar periods in the user's time zone. A day, week, month, or year starts at local
 * midnight and ends at the next local midnight, so the same moment can fall in different months
 * in different time zones. Ethiopia uses Africa/Addis_Ababa (UTC+3, no daylight saving).
 */
object ReportingPeriods {

    fun day(date: LocalDate, zone: ZoneId): ReportingPeriod =
        between(date, date.plusDays(1), zone)

    fun week(
        dateInWeek: LocalDate,
        zone: ZoneId,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
    ): ReportingPeriod {
        val start = dateInWeek.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return between(start, start.plusWeeks(1), zone)
    }

    fun month(month: YearMonth, zone: ZoneId): ReportingPeriod =
        between(month.atDay(1), month.plusMonths(1).atDay(1), zone)

    fun year(year: Int, zone: ZoneId): ReportingPeriod =
        between(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1), zone)

    private fun between(startDate: LocalDate, endDate: LocalDate, zone: ZoneId): ReportingPeriod =
        ReportingPeriod(
            startDate.atStartOfDay(zone).toInstant(),
            endDate.atStartOfDay(zone).toInstant()
        )
}
