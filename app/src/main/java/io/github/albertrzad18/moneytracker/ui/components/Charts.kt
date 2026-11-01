package io.github.albertrzad18.moneytracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import io.github.albertrzad18.moneytracker.R
import kotlin.math.abs

/** One line of a [LineChart]; x is epoch millis. */
data class ChartSeries(
    val points: List<Pair<Long, Float>>,
    val color: Color,
    val label: String? = null,
    val fill: Boolean = false,
)

/** Dashed horizontal marker, e.g. the price of a purchase. */
data class ChartReferenceLine(val value: Float, val label: String, val color: Color)

private class PlotGeometry {
    var left = 0f
    var right = 1f
    var minX = 0L
    var maxX = 1L

    fun xValueAt(px: Float): Long {
        val fraction = ((px - left) / (right - left).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return minX + ((maxX - minX) * fraction).toLong()
    }
}

/**
 * Line chart with a recessive grid, y labels on the right, first/middle/last x labels,
 * dashed reference lines and a crosshair with tooltip while the finger is down.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    formatX: (Long) -> String,
    formatY: (Float) -> String,
    modifier: Modifier = Modifier,
    referenceLines: List<ChartReferenceLine> = emptyList(),
    description: String = "",
) {
    val points = series.flatMap { it.points }
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.chart_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val crosshairColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val surface = MaterialTheme.colorScheme.surface
    val geometry = remember { PlotGeometry() }
    var selectedX by remember(series) { mutableStateOf<Long?>(null) }

    val minX = points.minOf { it.first }
    val maxX = points.maxOf { it.first }.coerceAtLeast(minX + 1)
    val values = points.map { it.second } + referenceLines.map { it.value }
    var minY = values.min()
    var maxY = values.max()
    if (minY == maxY) {
        minY -= 1f
        maxY += 1f
    }
    val padding = (maxY - minY) * 0.08f
    minY -= padding
    maxY += padding

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(series) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    selectedX = geometry.xValueAt(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val delta = change.position - change.previousPosition
                        if (abs(delta.x) > abs(delta.y)) change.consume()
                        selectedX = geometry.xValueAt(change.position.x)
                    }
                    selectedX = null
                }
            },
    ) {
        val yTicks = (0..3).map { minY + (maxY - minY) * it / 3f }
        val yLabels = yTicks.map { textMeasurer.measure(formatY(it), labelStyle) }
        val gutter = yLabels.maxOf { it.size.width } + 8.dp.toPx()
        val labelHeight = yLabels.first().size.height.toFloat()
        val plotLeft = 0f
        val plotRight = size.width - gutter
        val plotTop = labelHeight / 2
        val plotBottom = size.height - labelHeight - 6.dp.toPx()
        geometry.left = plotLeft
        geometry.right = plotRight
        geometry.minX = minX
        geometry.maxX = maxX

        fun xPos(x: Long) = plotLeft + (x - minX).toFloat() / (maxX - minX) * (plotRight - plotLeft)
        fun yPos(y: Float) = plotBottom - (y - minY) / (maxY - minY) * (plotBottom - plotTop)

        yTicks.forEachIndexed { i, tick ->
            val y = yPos(tick)
            drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
            drawText(yLabels[i], topLeft = Offset(plotRight + 6.dp.toPx(), (y - labelHeight / 2).coerceIn(0f, size.height - labelHeight)))
        }

        listOf(minX, (minX + maxX) / 2, maxX).forEachIndexed { i, x ->
            val layout = textMeasurer.measure(formatX(x), labelStyle)
            val left = when (i) {
                0 -> plotLeft
                1 -> xPos(x) - layout.size.width / 2
                else -> plotRight - layout.size.width
            }
            drawText(layout, topLeft = Offset(left, plotBottom + 4.dp.toPx()))
        }

        referenceLines.forEach { line ->
            val y = yPos(line.value)
            drawLine(
                color = line.color,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
            val layout = textMeasurer.measure(line.label, labelStyle)
            drawText(layout, topLeft = Offset(plotLeft + 4.dp.toPx(), (y - layout.size.height - 1.dp.toPx()).coerceAtLeast(0f)))
        }

        series.forEach { s ->
            if (s.points.size < 2) return@forEach
            val path = Path()
            s.points.forEachIndexed { i, (x, y) ->
                if (i == 0) path.moveTo(xPos(x), yPos(y)) else path.lineTo(xPos(x), yPos(y))
            }
            if (s.fill) {
                val area = Path().apply {
                    addPath(path)
                    lineTo(xPos(s.points.last().first), plotBottom)
                    lineTo(xPos(s.points.first().first), plotBottom)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.22f), Color.Transparent), plotTop, plotBottom))
            }
            drawPath(path, s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        val selected = selectedX ?: return@Canvas
        val nearest = series.mapNotNull { s -> s.points.minByOrNull { abs(it.first - selected) }?.let { s to it } }
        if (nearest.isEmpty()) return@Canvas
        val crossX = xPos(nearest.first().second.first)
        drawLine(crosshairColor, Offset(crossX, plotTop), Offset(crossX, plotBottom), strokeWidth = 1.dp.toPx())
        nearest.forEach { (s, point) ->
            val center = Offset(xPos(point.first), yPos(point.second))
            drawCircle(surface, radius = 6.dp.toPx(), center = center)
            drawCircle(s.color, radius = 4.dp.toPx(), center = center)
        }
        val text = buildString {
            append(formatX(nearest.first().second.first))
            nearest.forEach { (s, point) ->
                append('\n')
                if (s.label != null) append(s.label).append(": ")
                append(formatY(point.second))
            }
        }
        val layout = textMeasurer.measure(text, tooltipStyle)
        val pad = 8.dp.toPx()
        val boxSize = Size(layout.size.width + pad * 2, layout.size.height + pad * 2)
        val boxLeft = (crossX - boxSize.width / 2).coerceIn(0f, (size.width - boxSize.width).coerceAtLeast(0f))
        drawRoundRect(tooltipBackground, topLeft = Offset(boxLeft, plotTop), size = boxSize, cornerRadius = CornerRadius(8.dp.toPx()))
        drawText(layout, topLeft = Offset(boxLeft + pad, plotTop + pad))
    }
}

/** Ring chart; [colors] must line up with [values]. A 2px surface gap separates the slices. */
@Composable
fun DonutChart(values: List<Float>, colors: List<Color>, modifier: Modifier = Modifier, description: String = "") {
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier.semantics { contentDescription = description }) {
        val total = values.sum()
        if (total <= 0f) return@Canvas
        val stroke = size.minDimension * 0.2f
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)
        var start = -90f
        values.forEachIndexed { i, value ->
            val sweep = 360f * value / total
            drawArc(colors[i], start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            start += sweep
        }
        if (values.size > 1) {
            // Thin surface-colored spokes create the gaps between slices.
            val center = Offset(size.width / 2, size.height / 2)
            val inner = diameter / 2 - stroke / 2
            val outer = diameter / 2 + stroke / 2
            var angle = -90f
            values.forEach { value ->
                val radians = Math.toRadians(angle.toDouble())
                val direction = Offset(kotlin.math.cos(radians).toFloat(), kotlin.math.sin(radians).toFloat())
                drawLine(surface, center + direction * inner, center + direction * outer, strokeWidth = 2.dp.toPx())
                angle += 360f * value / total
            }
        }
    }
}

/** Colored swatch plus text label; identity never relies on color alone. */
@Composable
fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
