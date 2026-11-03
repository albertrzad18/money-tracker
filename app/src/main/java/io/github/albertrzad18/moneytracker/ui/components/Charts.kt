package io.github.albertrzad18.moneytracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.albertrzad18.moneytracker.ui.theme.Durations
import io.github.albertrzad18.moneytracker.ui.theme.MoneyTheme
import io.github.albertrzad18.moneytracker.ui.theme.emphasized
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Horizontal reference line: dashed (a purchase price) or dotted (the average). */
data class ChartRef(val value: Float, val color: Color, val dotted: Boolean = false)

/** Points per path after resampling; enough for a phone-wide chart and fixed so paths can morph. */
private const val PATH_POINTS = 160

/** A chart normalized to 0..1 (0 = bottom), so two charts can be interpolated. */
private class ChartFrame(
    val main: FloatArray,
    val secondary: FloatArray?,
    val refs: FloatArray,
    val refStyles: List<ChartRef>,
) {
    fun lerp(to: ChartFrame, t: Float): ChartFrame {
        fun mix(a: FloatArray, b: FloatArray) = FloatArray(b.size) { a[it] + (b[it] - a[it]) * t }
        val secondary = to.secondary?.let { target -> secondary?.let { mix(it, target) } ?: target }
        val refs = if (refs.size == to.refs.size) mix(refs, to.refs) else to.refs
        return ChartFrame(mix(main, to.main), secondary, refs, to.refStyles)
    }
}

private fun resample(values: List<Float>, count: Int): FloatArray {
    if (values.size == 1) return FloatArray(count) { values[0] }
    return FloatArray(count) { i ->
        val position = i.toFloat() / (count - 1) * (values.size - 1)
        val left = position.toInt().coerceAtMost(values.size - 2)
        val t = position - left
        values[left] + (values[left + 1] - values[left]) * t
    }
}

private class Scale(val lo: Float, val hi: Float) {
    fun norm(v: Float) = (v - lo) / (hi - lo)
}

private fun scaleFor(values: List<Float>, floorAtZero: Boolean): Scale {
    var lo = values.min()
    var hi = values.max()
    val pad = if (hi > lo) (hi - lo) * 0.1f else max(abs(hi) * 0.05f, 1e-6f)
    lo -= pad
    hi += pad
    if (floorAtZero) lo = max(0f, lo)
    return Scale(lo, hi)
}

/**
 * Area chart with points spaced evenly by index: a filled area and a 2.5 dp line, an optional dashed
 * second line, horizontal reference lines and max/min labels at the right edge. Changing the data
 * morphs the paths over 420 ms. Touching shows a vertical line, a dot and a pill with [tooltip].
 */
@Composable
fun AreaChart(
    values: List<Float>,
    formatValue: (Float) -> String,
    tooltip: (Int) -> String,
    modifier: Modifier = Modifier,
    secondary: List<Float>? = null,
    refs: List<ChartRef> = emptyList(),
    lineColor: Color = MoneyTheme.colors.chartLine,
    fillColor: Color = MoneyTheme.colors.chartFill,
    secondaryColor: Color = MoneyTheme.colors.chartSecondary,
    /** Dot ring and tooltip text; the color of whatever is behind the chart. */
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    floorAtZero: Boolean = false,
    description: String = "",
) {
    if (values.isEmpty()) return
    val all = values + secondary.orEmpty() + refs.map { it.value }
    val scale = remember(values, secondary, refs, floorAtZero) { scaleFor(all, floorAtZero) }
    val target = remember(values, secondary, refs, scale) {
        ChartFrame(
            main = resample(values.map(scale::norm), PATH_POINTS),
            secondary = secondary?.takeIf { it.isNotEmpty() }?.let { s -> resample(s.map(scale::norm), PATH_POINTS) },
            refs = FloatArray(refs.size) { scale.norm(refs[it].value) },
            refStyles = refs,
        )
    }
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (to !== target) {
            from = from.lerp(to, progress.value)
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, emphasized(Durations.CHART_MORPH))
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(color = backgroundColor, fontWeight = FontWeight.SemiBold)
    val textColor = MaterialTheme.colorScheme.onBackground
    val hiLabel = formatValue(values.max().let { m -> max(m, (secondary.orEmpty() + refs.map { it.value }).maxOrNull() ?: m) })
    val loLabel = formatValue(values.min().let { m -> minOf(m, (secondary.orEmpty() + refs.map { it.value }).minOrNull() ?: m) })

    var scrub by remember(values) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<Job?>(null) }

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(values) {
                fun indexAt(x: Float) = (x / size.width * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    hideJob?.cancel()
                    scrub = indexAt(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val delta = change.position - change.previousPosition
                        if (abs(delta.x) > abs(delta.y)) change.consume()
                        scrub = indexAt(change.position.x)
                    }
                    hideJob = scope.launch {
                        delay(2_000)
                        scrub = null
                    }
                }
            },
    ) {
        val frame = if (progress.value >= 1f) to else from.lerp(to, progress.value)
        val w = size.width
        val h = size.height
        fun y(norm: Float) = h - norm * h
        fun path(points: FloatArray) = Path().apply {
            points.forEachIndexed { i, v ->
                val x = i.toFloat() / (points.size - 1) * w
                if (i == 0) moveTo(x, y(v)) else lineTo(x, y(v))
            }
        }

        val line = path(frame.main)
        val area = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(area, fillColor)

        frame.refs.forEachIndexed { i, norm ->
            val style = frame.refStyles.getOrNull(i) ?: return@forEachIndexed
            val effect = if (style.dotted) {
                PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx()))
            } else {
                PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
            }
            drawLine(
                color = style.color,
                start = Offset(0f, y(norm)),
                end = Offset(w, y(norm)),
                strokeWidth = (if (style.dotted) 2.dp else 1.5.dp).toPx(),
                cap = if (style.dotted) StrokeCap.Round else StrokeCap.Butt,
                pathEffect = effect,
            )
        }

        frame.secondary?.let {
            drawPath(
                path(it),
                secondaryColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                ),
            )
        }
        drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val hi = textMeasurer.measure(hiLabel, labelStyle)
        val lo = textMeasurer.measure(loLabel, labelStyle)
        drawText(hi, topLeft = Offset(w - hi.size.width, -6.dp.toPx()))
        drawText(lo, topLeft = Offset(w - lo.size.width, h + 6.dp.toPx() - lo.size.height))

        val index = scrub ?: return@Canvas
        drawReadout(
            x = if (values.size > 1) index.toFloat() / (values.size - 1) * w else w / 2,
            y = y(scale.norm(values[index])),
            text = tooltip(index),
            lineColor = lineColor,
            textColor = textColor,
            ringColor = backgroundColor,
            measure = { textMeasurer.measure(it, tooltipStyle) },
        )
    }
}

private fun DrawScope.drawReadout(
    x: Float,
    y: Float,
    text: String,
    lineColor: Color,
    textColor: Color,
    ringColor: Color,
    measure: (String) -> androidx.compose.ui.text.TextLayoutResult,
) {
    val w = size.width
    drawRect(textColor.copy(alpha = 0.3f), Offset(x - 0.75.dp.toPx(), 0f), Size(1.5.dp.toPx(), size.height))
    drawCircle(ringColor, radius = 9.dp.toPx(), center = Offset(x, y))
    drawCircle(lineColor, radius = 6.dp.toPx(), center = Offset(x, y))

    val layout = measure(text)
    val padX = 12.dp.toPx()
    val padY = 4.dp.toPx()
    val pill = Size(layout.size.width + padX * 2, layout.size.height + padY * 2)
    // Centered over the line, pulled in at the edges.
    val left = (x - pill.width / 2).coerceIn(0f, (w - pill.width).coerceAtLeast(0f))
    val top = -22.dp.toPx() - pill.height / 2
    drawRoundRect(textColor, Offset(left, top), pill, CornerRadius(pill.height / 2))
    drawText(layout, topLeft = Offset(left + padX, top + padY))
}

/**
 * Ring chart: thickness 5/36 of the diameter, round ends and gaps between slices. Slices grow in
 * over 500 ms whenever the values change.
 */
@Composable
fun DonutChart(values: List<Float>, colors: List<Color>, modifier: Modifier = Modifier, description: String = "") {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, emphasized(Durations.DONUT))
    }
    val track = MoneyTheme.colors.line
    Canvas(modifier.semantics { contentDescription = description }) {
        val total = values.sum()
        val stroke = size.minDimension * 5f / 36f
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)
        if (total <= 0f) {
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            return@Canvas
        }
        val visible = values.filter { it > 0f }
        if (visible.size == 1) {
            drawArc(colors[values.indexOf(visible[0])], -90f, 360f * progress.value, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            return@Canvas
        }
        // A round cap reaches stroke/2 past the arc end, so the gap covers both caps plus 3 dp of air.
        val radius = diameter / 2
        val gapDegrees = ((stroke + 3.dp.toPx()) / radius * 180f / PI.toFloat())
        var start = -90f
        values.forEachIndexed { i, value ->
            if (value <= 0f) return@forEachIndexed
            val sweep = 360f * value / total
            val drawn = ((sweep - gapDegrees).coerceAtLeast(0.1f)) * progress.value
            drawArc(colors[i], start + gapDegrees / 2, drawn, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            start += sweep
        }
    }
}
