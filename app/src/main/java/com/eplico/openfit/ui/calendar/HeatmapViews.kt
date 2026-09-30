package com.eplico.openfit.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eplico.openfit.core.Heatmap
import com.eplico.openfit.core.HeatmapGrid
import com.eplico.openfit.ui.theme.LocalHeatmapColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private fun readableOn(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xDE000000) else Color.White

/** Classic month calendar where each day is a heatmap square. Weeks are rows. */
@Composable
fun MonthHeatmap(
    grid: HeatmapGrid,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHeatmapColors.current.levels
    val todayOutline = MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            grid.weekdays.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        grid.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(3.dp),
                    ) {
                        if (day != null) {
                            val fill = colors[day.level]
                            val shape = RoundedCornerShape(8.dp)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(shape)
                                    .background(fill)
                                    .then(if (day.date == today) Modifier.border(2.dp, todayOutline, shape) else Modifier)
                                    .clickable { onDayClick(day.date) },
                            ) {
                                Text(
                                    "${day.date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (day.count > 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (day.level == 0) MaterialTheme.colorScheme.onSurfaceVariant else readableOn(fill),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * GitHub-style strip: weeks are columns, weekdays are rows, month names across the top.
 * Drawn on a Canvas so a whole year (or several) stays cheap.
 */
@Composable
fun YearHeatmap(
    grid: HeatmapGrid,
    today: LocalDate,
    cellSize: Dp,
    gap: Dp,
    onDayClick: ((LocalDate) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHeatmapColors.current.levels
    val todayOutline = MaterialTheme.colorScheme.onSurface
    val step = cellSize + gap
    val width = step * grid.weeks.size - gap
    val height = step * 7 - gap
    val labelHeight = 16.dp

    Column(modifier.width(width)) {
        Box(
            Modifier
                .height(labelHeight)
                .fillMaxWidth(),
        ) {
            var lastLabelColumn = -10
            grid.monthStarts.forEach { (column, month) ->
                // Skip labels that would collide with the previous one (ranges starting late in a month).
                if (column - lastLabelColumn >= 3) {
                    lastLabelColumn = column
                    Text(
                        month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.offset(x = step * column),
                    )
                }
            }
        }
        val tapModifier = if (onDayClick != null) {
            Modifier.pointerInput(grid, cellSize, gap) {
                detectTapGestures { offset ->
                    val stepPx = step.toPx()
                    val column = (offset.x / stepPx).toInt()
                    val row = (offset.y / stepPx).toInt()
                    grid.weeks.getOrNull(column)?.getOrNull(row)?.let { onDayClick(it.date) }
                }
            }
        } else {
            Modifier
        }
        Canvas(
            Modifier
                .size(width, height)
                .then(tapModifier),
        ) {
            val cellPx = cellSize.toPx()
            val stepPx = step.toPx()
            val radius = CornerRadius(cellPx * 0.2f)
            grid.weeks.forEachIndexed { column, week ->
                week.forEachIndexed { row, day ->
                    if (day != null) {
                        val topLeft = Offset(column * stepPx, row * stepPx)
                        drawRoundRect(
                            color = colors[day.level],
                            topLeft = topLeft,
                            size = Size(cellPx, cellPx),
                            cornerRadius = radius,
                        )
                        if (day.date == today) {
                            drawRoundRect(
                                color = todayOutline,
                                topLeft = topLeft,
                                size = Size(cellPx, cellPx),
                                cornerRadius = radius,
                                style = Stroke(width = (cellPx * 0.15f).coerceAtLeast(1.5f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Mon / Wed / Fri labels aligned with the rows of a [YearHeatmap]. */
@Composable
fun WeekdayLabels(
    weekdays: List<DayOfWeek>,
    cellSize: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    val shown = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    Column(modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        weekdays.forEach { day ->
            Box(Modifier.height(cellSize), contentAlignment = Alignment.CenterStart) {
                if (day in shown) {
                    Text(
                        day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
    val colors = LocalHeatmapColors.current.levels
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (0..Heatmap.MAX_LEVEL).forEach { level ->
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors[level]),
            )
        }
        Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
