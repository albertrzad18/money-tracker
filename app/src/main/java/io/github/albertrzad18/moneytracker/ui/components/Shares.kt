package io.github.albertrzad18.moneytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.albertrzad18.moneytracker.domain.AllocationSlice
import io.github.albertrzad18.moneytracker.domain.Calc
import io.github.albertrzad18.moneytracker.ui.theme.Pill
import io.github.albertrzad18.moneytracker.util.Formatters
import java.math.BigDecimal

/** A slice ready to draw: label, share 0..1 and its palette color. */
data class ShareSlice(val label: String, val share: Float, val color: Color) {
    val shareText: String get() = Formatters.pct(BigDecimal(share * 100.0)).removePrefix("+")
}

/**
 * Colors slices from [palette] in order (they come sorted by value). When there are more slices
 * than colors, the smallest ones are folded into [otherLabel] with the last color.
 */
fun shareSlices(slices: List<AllocationSlice>, palette: List<Color>, otherLabel: String): List<ShareSlice> {
    val folded = if (slices.size <= palette.size) {
        slices
    } else {
        val kept = slices.take(palette.size - 1)
        val rest = slices.drop(palette.size - 1)
        kept + AllocationSlice(otherLabel, null, Calc.sum(rest.map { it.valuePln }), rest.sumOf { it.share.toDouble() }.toFloat())
    }
    return folded.mapIndexed { i, slice -> ShareSlice(slice.label, slice.share, palette[i]) }
}

/** Portfolio structure: pill segments 10 dp high with 3 dp gaps, widths proportional to the share. */
@Composable
fun StructureBar(slices: List<ShareSlice>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics { contentDescription = slices.joinToString { "${it.label} ${it.shareText}" } },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        slices.forEach { slice ->
            if (slice.share > 0f) {
                Box(Modifier.weight(slice.share).fillMaxHeight().clip(Pill).background(slice.color))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareLegend(slices: List<ShareSlice>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        slices.forEach { LegendDot(it.color, it.label, value = it.shareText) }
    }
}
