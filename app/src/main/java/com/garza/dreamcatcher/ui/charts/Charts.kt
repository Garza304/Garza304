package com.garza.dreamcatcher.ui.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garza.dreamcatcher.insights.DreamStats
import kotlin.math.ceil

/*
 * Small chart kit shared by every screen that shows numbers. The rules, so new charts match:
 * marks carry the series color and text never does; bars are at most 24dp thick with 4dp rounded
 * ends and square bases; touching segments are separated by a 2dp gap in the surface color;
 * gridlines are 1px and recessive; two or more series always get a legend; and every chart
 * says its numbers to screen readers and has a table or labels for reading exact values.
 */

/** One series: a name for the legend and the color its marks wear. */
data class ChartSeries(val label: String, val color: Color, val marker: Marker = Marker.SQUARE)

/** How a series is keyed in a legend; the calendar uses all three so identity never rests on color alone. */
enum class Marker { SQUARE, DOT, DIAMOND, RING }

/** One column of a stacked chart: values bottom to top, in series order. */
data class StackedColumn(val label: String, val values: List<Int>) {
    val total: Int get() = values.sum()
}

private val BarMaxWidth = 24.dp
private val BarRadius = 4.dp
private val SegmentGap = 2.dp
private val XAxisBand = 22.dp

/**
 * Stacked columns on one y-axis. Tapping a column selects it; show its numbers next to the chart
 * (the selection is the readout, not a tooltip). [description] is read out instead of the drawing.
 */
@Composable
fun StackedColumnChart(
    columns: List<StackedColumn>,
    series: List<ChartSeries>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 140.dp,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant, fontFeatureSettings = "tnum")
    val ticks = remember(columns) { DreamStats.axisTicks(columns.maxOfOrNull { it.total } ?: 0) }
    // Where the plot starts and how wide a column's slot is, from the last draw, for hit testing.
    val geometry = remember { FloatArray(2) }
    val currentOnSelect by rememberUpdatedState(onSelect)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(plotHeight + XAxisBand)
            .semantics { contentDescription = description }
            .pointerInput(columns) {
                detectTapGestures { offset ->
                    val (plotLeft, slot) = geometry
                    if (slot > 0f && columns.isNotEmpty()) {
                        currentOnSelect(((offset.x - plotLeft) / slot).toInt().coerceIn(0, columns.lastIndex))
                    }
                }
            },
    ) {
        val top = ticks.last().coerceAtLeast(1)
        val tickLabels = ticks.map { measurer.measure(it.toString(), axisStyle) }
        val plotLeft = (tickLabels.maxOf { it.size.width } + 8.dp.toPx())
        val plotTop = tickLabels.first().size.height / 2f
        val plotBottom = plotHeight.toPx()
        val plotH = plotBottom - plotTop
        val n = columns.size.coerceAtLeast(1)
        val slot = (size.width - plotLeft) / n
        geometry[0] = plotLeft
        geometry[1] = slot
        fun yOf(v: Int) = plotBottom - plotH * v / top

        val barW = minOf(BarMaxWidth.toPx(), slot * 0.6f)
        val gap = SegmentGap.toPx()
        val radius = BarRadius.toPx()

        // The selected column's highlight sits behind everything else.
        if (selected != null && selected in columns.indices) {
            val center = plotLeft + slot * (selected + 0.5f)
            drawRoundRect(
                colors.surfaceVariant,
                topLeft = Offset(center - slot / 2f + gap, plotTop - 4.dp.toPx()),
                size = Size(slot - 2 * gap, plotBottom - plotTop + 4.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
        }

        // Gridlines and y-axis labels.
        ticks.forEachIndexed { i, tick ->
            val y = yOf(tick)
            drawLine(colors.outlineVariant, Offset(plotLeft, y), Offset(size.width, y), strokeWidth = 1f)
            val label = tickLabels[i]
            drawText(label, topLeft = Offset(plotLeft - 8.dp.toPx() - label.size.width, y - label.size.height / 2f))
        }

        columns.forEachIndexed { index, column ->
            val center = plotLeft + slot * (index + 0.5f)
            val lastNonZero = column.values.indexOfLast { it > 0 }
            var running = 0
            var firstDrawn = true
            column.values.forEachIndexed { s, value ->
                if (value <= 0) return@forEachIndexed
                val bottom = yOf(running) - if (firstDrawn) 0f else gap
                running += value
                val topY = yOf(running)
                val left = center - barW / 2f
                val color = series.getOrNull(s)?.color ?: colors.primary
                if (s == lastNonZero) {
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left, top = topY, right = left + barW, bottom = bottom,
                                topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                            )
                        )
                    }
                    drawPath(path, color)
                } else {
                    drawRect(color, topLeft = Offset(left, topY), size = Size(barW, bottom - topY))
                }
                firstDrawn = false
            }
        }

        drawXLabels(columns.map { measurer.measure(it.label, axisStyle) }, plotLeft, slot, plotBottom)
    }
}

/**
 * A percentage over time on a 0–100% axis: a 2dp line with a faint wash beneath, gaps where a
 * period has no data, and a ringed dot on the latest value. Tapping selects the nearest point.
 */
@Composable
fun PercentLineChart(
    points: List<Int?>,
    labels: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    description: String,
    color: Color,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 140.dp,
    surface: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant, fontFeatureSettings = "tnum")
    val geometry = remember { FloatArray(2) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val ticks = listOf(0, 25, 50, 75, 100)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(plotHeight + XAxisBand)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val (plotLeft, slot) = geometry
                    if (slot > 0f && points.isNotEmpty()) {
                        currentOnSelect(((offset.x - plotLeft) / slot).toInt().coerceIn(0, points.lastIndex))
                    }
                }
            },
    ) {
        val tickLabels = ticks.map { measurer.measure("$it%", axisStyle) }
        val plotLeft = tickLabels.maxOf { it.size.width } + 8.dp.toPx()
        val plotTop = tickLabels.first().size.height / 2f
        val plotBottom = plotHeight.toPx()
        val plotH = plotBottom - plotTop
        val slot = (size.width - plotLeft) / points.size.coerceAtLeast(1)
        geometry[0] = plotLeft
        geometry[1] = slot
        fun x(i: Int) = plotLeft + slot * (i + 0.5f)
        fun y(v: Int) = plotBottom - plotH * v / 100f

        ticks.forEachIndexed { i, tick ->
            val yy = y(tick)
            drawLine(colors.outlineVariant, Offset(plotLeft, yy), Offset(size.width, yy), strokeWidth = 1f)
            val label = tickLabels[i]
            drawText(label, topLeft = Offset(plotLeft - 8.dp.toPx() - label.size.width, yy - label.size.height / 2f))
        }

        if (selected != null && selected in points.indices) {
            drawLine(colors.outline, Offset(x(selected), plotTop), Offset(x(selected), plotBottom), strokeWidth = 1.dp.toPx())
        }

        // Continuous runs of known values; a run of one is drawn as a dot only.
        val runs = mutableListOf<MutableList<Int>>()
        points.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            if (runs.isEmpty() || points.getOrNull(i - 1) == null) runs += mutableListOf<Int>()
            runs.last() += i
        }
        runs.filter { it.size > 1 }.forEach { run ->
            val line = Path().apply {
                run.forEachIndexed { k, i -> if (k == 0) moveTo(x(i), y(points[i]!!)) else lineTo(x(i), y(points[i]!!)) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(run.last()), plotBottom)
                lineTo(x(run.first()), plotBottom)
                close()
            }
            drawPath(area, color.copy(alpha = 0.1f))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val dots = (runs.filter { it.size == 1 }.map { it.first() } + listOfNotNull(points.indexOfLast { it != null }.takeIf { it >= 0 }, selected?.takeIf { points.getOrNull(it) != null })).toSet()
        dots.forEach { i ->
            val center = Offset(x(i), y(points[i]!!))
            drawCircle(surface, radius = 6.dp.toPx(), center = center)
            drawCircle(color, radius = 4.dp.toPx(), center = center)
        }

        drawXLabels(labels.map { measurer.measure(it, axisStyle) }, plotLeft, slot, plotBottom)
    }
}

/** Labels under the plot: as many as fit without touching, always including the most recent. */
private fun DrawScope.drawXLabels(measured: List<TextLayoutResult>, plotLeft: Float, slot: Float, plotBottom: Float) {
    if (measured.isEmpty()) return
    val widest = measured.maxOf { it.size.width } + 8.dp.toPx()
    val every = ceil(widest / slot).toInt().coerceAtLeast(1)
    measured.forEachIndexed { index, label ->
        if ((measured.lastIndex - index) % every != 0) return@forEachIndexed
        val center = plotLeft + slot * (index + 0.5f)
        val x = (center - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
        drawText(label, topLeft = Offset(x, plotBottom + 6.dp.toPx()))
    }
}

/** Parts of a whole as one bar, split by 2dp gaps; label the parts beside it (it carries no text). */
@Composable
fun ProportionBar(parts: List<Pair<Int, Color>>, description: String, modifier: Modifier = Modifier, height: Dp = 20.dp) {
    val shown = parts.filter { it.first > 0 }
    Row(
        modifier = modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        shown.forEachIndexed { i, (value, color) ->
            val shape = RoundedCornerShape(
                topStart = if (i == 0) BarRadius else 0.dp,
                bottomStart = if (i == 0) BarRadius else 0.dp,
                topEnd = if (i == shown.lastIndex) BarRadius else 0.dp,
                bottomEnd = if (i == shown.lastIndex) BarRadius else 0.dp,
            )
            Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(color, shape))
        }
    }
}

/**
 * One horizontal bar per row with its value at the tip; a single series, so no legend. Bars are
 * scaled to [max], or to the largest value. [labelAbove] puts long labels on their own line.
 */
@Composable
fun HorizontalBarChart(
    rows: List<Pair<String, Int>>,
    color: Color,
    unit: (Int) -> String,
    modifier: Modifier = Modifier,
    max: Int? = null,
    valueText: (Int) -> String = { it.toString() },
    labelAbove: Boolean = false,
) {
    val top = (max ?: rows.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (label, value) ->
            val labelText: @Composable (Modifier) -> Unit = { m ->
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = m)
            }
            val bar: @Composable (Modifier) -> Unit = { m ->
                BoxWithConstraints(m) {
                    val valueSpace = 44.dp
                    val barWidth = ((maxWidth - valueSpace) * (value.toFloat() / top).coerceIn(0f, 1f)).coerceAtLeast(2.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(barWidth)
                                .height(14.dp)
                                .background(color, RoundedCornerShape(topEnd = BarRadius, bottomEnd = BarRadius))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            valueText(value),
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            val semantics = Modifier.clearAndSetSemantics { contentDescription = "$label, ${unit(value)}" }
            if (labelAbove) {
                Column(semantics, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    labelText(Modifier)
                    bar(Modifier.fillMaxWidth())
                }
            } else {
                Row(semantics, verticalAlignment = Alignment.CenterVertically) {
                    labelText(Modifier.width(112.dp).padding(end = 8.dp))
                    bar(Modifier.weight(1f))
                }
            }
        }
    }
}

/** A headline number: label, value and an optional line of context. */
@Composable
fun StatTile(label: String, value: String, detail: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

/** The mark that keys a series, drawn the same way in legends and on the calendar. */
@Composable
fun MarkerShape(marker: Marker, color: Color, size: Dp = 10.dp) {
    when (marker) {
        Marker.SQUARE -> Box(Modifier.size(size).background(color, RoundedCornerShape(2.dp)))
        Marker.DOT -> Box(Modifier.size(size).background(color, CircleShape))
        Marker.DIAMOND -> Box(Modifier.size(size * 0.8f).rotate(45f).background(color, RoundedCornerShape(1.dp)))
        Marker.RING -> Box(Modifier.size(size).border(BorderStroke(2.dp, color), CircleShape))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(series: List<ChartSeries>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        series.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarkerShape(item.marker, item.color)
                Spacer(Modifier.width(6.dp))
                Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A plain table: the exact-numbers twin of a chart. The first column is the row label. */
@Composable
fun ChartTable(header: List<String>, rows: List<List<String>>, modifier: Modifier = Modifier) {
    Column(modifier) {
        TableRow(header, MaterialTheme.typography.labelSmall, MaterialTheme.colorScheme.onSurfaceVariant)
        val numberStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
        rows.forEach { TableRow(it, numberStyle, MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun TableRow(cells: List<String>, style: TextStyle, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(if (i == 0) 1.6f else 1f),
            )
        }
    }
}
