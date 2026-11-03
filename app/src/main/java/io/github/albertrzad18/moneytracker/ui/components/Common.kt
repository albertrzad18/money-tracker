package io.github.albertrzad18.moneytracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.albertrzad18.moneytracker.ui.theme.CardShape
import io.github.albertrzad18.moneytracker.ui.theme.Durations
import io.github.albertrzad18.moneytracker.ui.theme.MoneyTheme
import io.github.albertrzad18.moneytracker.ui.theme.Pill
import io.github.albertrzad18.moneytracker.ui.theme.emphasized
import io.github.albertrzad18.moneytracker.ui.theme.gainColor
import io.github.albertrzad18.moneytracker.ui.theme.toneOfPct
import io.github.albertrzad18.moneytracker.util.Formatters
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.min

/** Scales the element down while it is pressed (.96 for buttons, .985 for large rows). */
fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float = 0.96f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, emphasized(Durations.PRESS), label = "press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun GainText(pct: BigDecimal?, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(
        text = Formatters.pct(pct),
        modifier = modifier,
        style = style,
        color = gainColor(pct),
        fontWeight = FontWeight.Bold,
    )
}

/** Percent result on a tinted pill, e.g. the live result in the sell sheet. */
@Composable
fun ResultPill(pct: BigDecimal?, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val tone = toneOfPct(pct)
    val colors = MoneyTheme.colors
    Text(
        text = Formatters.pct(pct),
        modifier = modifier.clip(Pill).background(colors.toneContainer(tone)).padding(horizontal = 12.dp, vertical = 4.dp),
        style = style,
        fontWeight = FontWeight.Bold,
        color = colors.tone(tone),
    )
}

/** Fraction of half the track filled by a result: ±30% fills one half. */
fun deviationFraction(pct: BigDecimal?): Float =
    pct?.let { min(1f, abs(it.toFloat()) / 30f) } ?: 0f

/**
 * Deviation from zero: a pill track with a fill growing from the middle, right for a gain and left for
 * a loss. [centerTick] draws the zero mark.
 */
@Composable
fun DeviationBar(
    pct: BigDecimal?,
    modifier: Modifier = Modifier,
    thickness: Dp = 5.dp,
    centerTick: Boolean = false,
) {
    val colors = MoneyTheme.colors
    val tone = toneOfPct(pct)
    val fill = colors.tone(tone)
    val fraction by animateFloatAsState(deviationFraction(pct), emphasized(Durations.CHART_MORPH), label = "deviation")
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val positive = (pct?.signum() ?: 0) >= 0
    Canvas(modifier.height(if (centerTick) thickness + 8.dp else thickness)) {
        val barHeight = thickness.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(colors.line, Offset(0f, top), Size(size.width, barHeight), radius)
        val half = size.width / 2
        val width = half * fraction
        if (width > 0f) {
            val left = if (positive) half else half - width
            drawRoundRect(fill, Offset(left, top), Size(width, barHeight), radius)
        }
        if (centerTick) {
            val tick = 1.5.dp.toPx()
            drawRect(tickColor, Offset(half - tick / 2, 0f), Size(tick, size.height))
        }
    }
}

/** Small label above a pill field, indented to line up with the field's text. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Muted helper line under a field. */
@Composable
fun FieldHint(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        modifier = modifier.padding(start = 18.dp, top = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

/** Section heading inside a screen (19/700) or above a settings card (18/700). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleMedium) {
    Text(text, modifier = modifier, style = style, color = MaterialTheme.colorScheme.onBackground)
}

/** Large screen title (30/700) of Summary, History and Settings. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 10.dp),
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** Surface container with large radius. */
@Composable
fun MoneyCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(shape).background(color).padding(contentPadding),
        verticalArrangement = verticalArrangement,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

/** Label on the left, value on the right (14). */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

/** Round icon button; the pressed state fills it with the surface color. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.surface else Color.Transparent,
        emphasized(Durations.PRESS),
        label = "iconButton",
    )
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, modifier = iconModifier.size(iconSize), tint = tint)
    }
}

enum class PillButtonStyle { Filled, Outlined, Text, Danger }

/** Pill button: filled accent, outlined, plain text, or danger (loss tint). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillButtonStyle = PillButtonStyle.Filled,
    height: Dp = 52.dp,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp),
    /** Medium shadow, for the floating action button. */
    elevated: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val money = MoneyTheme.colors
    val (container, content) = when (style) {
        PillButtonStyle.Filled -> scheme.primary to scheme.onPrimary
        PillButtonStyle.Outlined -> Color.Transparent to scheme.onBackground
        PillButtonStyle.Text -> Color.Transparent to scheme.primary
        PillButtonStyle.Danger -> money.lossContainer to money.loss
    }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = Pill,
        color = container,
        contentColor = content,
        border = if (style == PillButtonStyle.Outlined) BorderStroke(1.dp, scheme.outline) else null,
        interactionSource = interaction,
        shadowElevation = if (elevated) 3.dp else 0.dp,
        modifier = modifier
            .height(height)
            .pressScale(interaction)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Row(
            Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
            }
            Text(text, style = textStyle, maxLines = 1)
        }
    }
}

/**
 * Segmented control: options on an inset pill, the selected one filled with the accent.
 */
@Composable
fun SegmentedPills(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
    background: Color = MoneyTheme.colors.inset,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(Pill).background(background).padding(4.dp)) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(if (selected) scheme.primary else Color.Transparent, emphasized(Durations.SWITCH_TRACK), label = "seg")
            val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onBackground, emphasized(Durations.SWITCH_TRACK), label = "segText")
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(Pill)
                    .background(bg)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
            }
        }
    }
}

/**
 * Range chips (1T, 1M, 6M…): 34 dp pills; the active one inverts text and background colors.
 */
@Composable
fun RangeChips(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = horizontalArrangement) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(if (selected) scheme.onBackground else Color.Transparent, emphasized(200), label = "chip")
            val fg by animateColorAsState(if (selected) scheme.background else scheme.onSurfaceVariant, emphasized(200), label = "chipText")
            Box(
                Modifier
                    .height(34.dp)
                    .widthIn(min = 50.dp)
                    .clip(Pill)
                    .background(bg)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}

/** 52×32 switch: accent track when on, 24 dp thumb sliding over 240 ms. */
@Composable
fun MoneySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val money = MoneyTheme.colors
    val track by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else money.switchTrackOff,
        emphasized(Durations.SWITCH_TRACK),
        label = "track",
    )
    val offset by animateDpAsState(if (checked) 24.dp else 4.dp, emphasized(Durations.SWITCH_THUMB), label = "thumb")
    Box(
        modifier
            .size(52.dp, 32.dp)
            .clip(Pill)
            .background(track)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Surface(
            shape = CircleShape,
            color = money.switchThumb,
            shadowElevation = 1.dp,
            modifier = Modifier.offset { IntOffset(offset.roundToPx(), 4.dp.roundToPx()) }.size(24.dp),
        ) {}
    }
}

/** Title, description and a switch on the right. */
@Composable
fun SwitchRow(title: String, description: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        MoneySwitch(checked, onCheckedChange)
    }
}

/**
 * Empty screen: a 112 dp circle with an icon, a title, a description and an optional button,
 * aligned to the left.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    circleColor: Color = MoneyTheme.colors.soft,
    iconColor: Color = MoneyTheme.colors.onSoft,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Box(Modifier.size(112.dp).clip(CircleShape).background(circleColor), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 290.dp),
            textAlign = TextAlign.Start,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(28.dp))
            PillButton(
                text = actionLabel,
                onClick = onAction,
                icon = actionIcon,
                contentPadding = PaddingValues(start = 18.dp, end = 22.dp),
                modifier = Modifier.defaultMinSize(minWidth = 160.dp),
            )
        }
    }
}

/** Legend entry: a colored dot, a label and an optional muted value. */
@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier, value: String? = null, dotSize: Dp = 8.dp, fontSize: Int = 12) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(dotSize).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize.sp))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
