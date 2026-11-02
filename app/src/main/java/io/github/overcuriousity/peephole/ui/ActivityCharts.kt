package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Requests per hour over the last 7 days, stacked by severity (the web's week chart). */
@Composable
fun WeekChart(bars: List<List<Long>>, modifier: Modifier = Modifier) {
    val colors = LocalPeepholeColors.current
    val total = bars.sumOf { it.sum() }
    val peak = bars.maxOfOrNull { it.sum() }?.coerceAtLeast(1) ?: 1
    Column(modifier) {
        FieldLabel("Last 7 days · requests per hour")
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(top = Spacing.xs)
                .semantics { contentDescription = "${count(total)} requests in the last 7 days, at most ${count(peak)} in one hour" },
        ) {
            drawRect(colors.chartTrack, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, 1f))
            if (bars.isEmpty()) return@Canvas
            val w = size.width / bars.size
            bars.forEachIndexed { i, bar ->
                var top = size.height
                bar.forEachIndexed { s, n ->
                    if (n == 0L) return@forEachIndexed
                    val h = size.height * n / peak
                    top -= h
                    drawRect(colors.severityFill[s], topLeft = Offset(i * w, top), size = Size((w - 0.5f).coerceAtLeast(0.5f), h))
                }
            }
        }
    }
}

/** Days with requests over the last 26 weeks, coloured by the day's highest severity. */
@Composable
fun CalendarChart(grid: List<List<DayCell?>>, modifier: Modifier = Modifier) {
    val colors = LocalPeepholeColors.current
    val cells = grid.flatten().filterNotNull()
    val active = cells.count { it.count > 0 }
    Column(modifier) {
        FieldLabel("Last 26 weeks · highest severity per day")
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(grid.size.coerceAtLeast(1) / 7f)
                .padding(top = Spacing.xs)
                .semantics { contentDescription = "Requests on $active of the last ${cells.size} days, ${count(cells.sumOf { it.count })} in all" },
        ) {
            if (grid.isEmpty()) return@Canvas
            val step = minOf(size.width / grid.size, size.height / 7)
            val gap = step * 0.15f
            grid.forEachIndexed { w, week ->
                week.forEachIndexed { d, cell ->
                    if (cell == null) return@forEachIndexed
                    val color = cell.maxSeverity?.takeIf { cell.count > 0 }
                        ?.let { colors.severityFill[it.coerceIn(0, colors.severityFill.lastIndex)] }
                        ?: colors.chartTrack
                    drawRoundRect(
                        color,
                        topLeft = Offset(w * step, d * step),
                        size = Size(step - gap, step - gap),
                        cornerRadius = CornerRadius(gap),
                    )
                }
            }
        }
    }
}
