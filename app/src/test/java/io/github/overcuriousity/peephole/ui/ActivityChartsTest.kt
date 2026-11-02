package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.Bucket
import io.github.overcuriousity.peephole.net.CalendarDay
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dossier sends only hours and days with requests; the charts fill the rest. */
class ActivityChartsTest {
    private val end = Instant.parse("2026-10-20T09:30:00Z")
    private val zeros = listOf(0L, 0L, 0L, 0L, 0L)

    @Test
    fun weekFillsMissingHours() {
        val bars = weekBars(
            listOf(
                Bucket("2026-10-20T07:00:00Z", 1, listOf(1, 0, 0, 0, 0)),
                Bucket("2026-10-20T09:00:00Z", 2, listOf(0, 0, 0, 2, 0)),
            ),
            end,
        )

        assertEquals(168, bars.size)
        assertEquals(listOf(0L, 0L, 0L, 2L, 0L), bars[167])
        assertEquals(zeros, bars[166])
        assertEquals(listOf(1L, 0L, 0L, 0L, 0L), bars[165])
    }

    @Test
    fun weekDropsOldAndMalformed() {
        val bars = weekBars(
            listOf(Bucket("2026-10-12T09:00:00Z", 5, listOf(5, 0, 0, 0, 0)), Bucket("x", 3, listOf(3, 0, 0, 0, 0))),
            end,
        )

        assertTrue(bars.all { it == zeros })
    }

    @Test
    fun weekWithoutSeverityCountsAsSeverityZero() {
        assertEquals(listOf(4L, 0L, 0L, 0L, 0L), weekBars(listOf(Bucket("2026-10-20T09:00:00Z", 4)), end)[167])
    }

    @Test
    fun weekOldestHourIsKept() {
        // 167 hours before the current hour is the first bar.
        assertEquals(listOf(1L, 0L, 0L, 0L, 0L), weekBars(listOf(Bucket("2026-10-13T10:00:00Z", 1, listOf(1, 0, 0, 0, 0))), end)[0])
    }

    @Test
    fun calendarPlacesTodayLast() {
        val today = LocalDate.parse("2026-10-21") // a Wednesday
        val grid = calendarGrid(listOf(CalendarDay("2026-10-19", 4, 3)), today)

        assertEquals(26, grid.size)
        assertTrue(grid.all { it.size == 7 })
        assertEquals(today, grid[25][2]!!.date)
        assertNull(grid[25][3])
        assertEquals(DayCell(LocalDate.parse("2026-10-19"), 4, 3), grid[25][0])
        assertEquals(DayCell(LocalDate.parse("2026-10-20"), 0, null), grid[25][1])
        assertEquals(LocalDate.parse("2026-04-27"), grid[0][0]!!.date)
    }

    @Test
    fun calendarDropsOutOfRangeAndMalformed() {
        val today = LocalDate.parse("2026-10-21")
        val grid = calendarGrid(listOf(CalendarDay("2025-09-16", 9, 4), CalendarDay("not a day", 9, 4)), today)

        assertTrue(grid.flatten().filterNotNull().all { it.count == 0L })
    }
}
