package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.Bucket
import io.github.overcuriousity.peephole.net.CalendarDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Hours in the week chart: the server's last 7 days. */
const val WEEK_HOURS = 168

/**
 * The week chart's bars, oldest first: one per hour of the [WEEK_HOURS]
 * hours that end with [end]'s hour, each five per-severity counts. The
 * dossier sends only hours with requests; the rest are zero. A bucket
 * outside the window or with an unreadable `ts` is dropped; one without
 * `by_severity` counts as severity 0.
 */
fun weekBars(buckets: List<Bucket>, end: Instant): List<List<Long>> {
    val last = end.truncatedTo(ChronoUnit.HOURS)
    val first = last.minus((WEEK_HOURS - 1).toLong(), ChronoUnit.HOURS)
    val bars = List(WEEK_HOURS) { LongArray(5) }
    for (b in buckets) {
        val at = runCatching { Instant.parse(b.ts) }.getOrNull()?.truncatedTo(ChronoUnit.HOURS) ?: continue
        val i = ChronoUnit.HOURS.between(first, at)
        if (i !in 0 until WEEK_HOURS) continue
        val bar = bars[i.toInt()]
        if (b.bySeverity.size == 5) b.bySeverity.forEachIndexed { s, n -> bar[s] += n } else bar[0] += b.count
    }
    return bars.map { it.toList() }
}

/** One calendar day; [maxSeverity] is null on a day without requests. */
data class DayCell(val date: LocalDate, val count: Long, val maxSeverity: Int?)

/**
 * The calendar's grid: [weeks] columns oldest first, seven rows Monday to
 * Sunday. The last column is [today]'s week; its days after today are null.
 * Days before the first cell, after today, or unreadable are dropped.
 */
fun calendarGrid(days: List<CalendarDay>, today: LocalDate, weeks: Int = 26): List<List<DayCell?>> {
    val byDate = days.mapNotNull { d -> runCatching { LocalDate.parse(d.day) }.getOrNull()?.let { it to d } }.toMap()
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
    return List(weeks) { w ->
        List(7) { d ->
            val date = start.plusDays(w * 7L + d)
            if (date.isAfter(today)) {
                null
            } else {
                val day = byDate[date]
                DayCell(date, day?.count ?: 0, day?.maxSeverity)
            }
        }
    }
}
