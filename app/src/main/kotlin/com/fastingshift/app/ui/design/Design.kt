package com.fastingshift.app.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * Shared navigation, grouped rows and actions for Fasting Shift.
 * - LargeTitle for tab screens; SecondaryScaffold (components/Common.kt) for pushed screens
 * - InsetGroup + GroupRow / SwitchGroupRow for settings-style lists
 * - PrimaryButton / SecondaryButton / PlainButton for actions
 * - SegmentedControl for 2-4 options
 * - rememberHaptics() for start/end/confirm moments
 */

object Spacing {
    val screen = 20.dp
    val section = 24.dp
    val item = 12.dp
}

/** Page heading that scrolls with the tab content. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Text(text, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Section heading above grouped rows. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 2.dp, bottom = 8.dp, top = 4.dp).semantics { heading() },
    )
}

/**
 * A rounded white card holding rows separated by hairlines (iOS inset grouped list).
 * Put [GroupRow]s inside; separators are drawn between them automatically via [GroupDivider].
 */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (header != null) GroupHeader(header)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

/** Hairline between rows, inset from the left like iOS. */
@Composable
fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** One tappable row: title, optional subtitle, optional trailing value and chevron. */
@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    opensPicker: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth().then(
        if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
    )) {
        val valueBelow = value != null && (LocalDensity.current.fontScale > 1.25f || maxWidth < 310.dp)
        val valueWidth = maxWidth * 0.40f
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
                if (valueBelow && value != null) {
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (value != null && !valueBelow) {
                Text(value, modifier = Modifier.widthIn(max = valueWidth), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
            trailing?.invoke(this)
            if (showChevron) {
                val icon = if (opensPicker) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The entire row is a single switch target. The thumb position and check also communicate state. */
@Composable
fun SwitchGroupRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null) {
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = { haptics.tick(); onCheckedChange(it) })
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PreferenceSwitchMark(checked)
    }
}

@Composable
private fun PreferenceSwitchMark(checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(150), label = "switchThumb")
    val track by animateColorAsState(if (checked) colors.primary else colors.surfaceVariant, label = "switchTrack")
    val outline by animateColorAsState(if (checked) colors.primary else colors.outline, label = "switchOutline")
    val thumb by animateColorAsState(if (checked) colors.onPrimary else colors.onSurfaceVariant, label = "switchKnob")

    Canvas(Modifier.padding(start = 10.dp).size(width = 46.dp, height = 28.dp)) {
        val border = 1.5.dp.toPx()
        val radius = size.height / 2f
        drawRoundRect(track, cornerRadius = CornerRadius(radius))
        drawRoundRect(outline, Offset(border / 2, border / 2), Size(size.width - border, size.height - border),
            CornerRadius(radius - border / 2), style = Stroke(border))
        val fraction = if (layoutDirection == LayoutDirection.Rtl) 1f - progress else progress
        val center = Offset(radius + (size.width - 2 * radius) * fraction, radius)
        drawCircle(thumb, radius = (7.5f + 2.5f * progress).dp.toPx(), center = center)
        if (checked) {
            val bend = center + Offset(-1.dp.toPx(), 2.5.dp.toPx())
            drawLine(track, center + Offset(-4.dp.toPx(), 0f), bend, 1.7.dp.toPx(), StrokeCap.Round)
            drawLine(track, bend, center + Offset(4.dp.toPx(), -3.dp.toPx()), 1.7.dp.toPx(), StrokeCap.Round)
        }
    }
}

private val ButtonShape = RoundedCornerShape(12.dp)

/** Main call to action: full width, 52dp, rounded rectangle (iOS filled button). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, haptic: Boolean = false) {
    val haptics = rememberHaptics()
    Button(
        onClick = { if (haptic) haptics.confirm(); onClick() },
        enabled = enabled,
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Secondary action: tinted background, accent text (iOS tinted button). */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Text-only action in the accent colour (iOS plain button). */
@Composable
fun PlainButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center)
    }
}

/** Equal hit areas expand together when translated labels or larger text wrap. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Row(
        modifier.fillMaxWidth().selectableGroup().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp).height(IntrinsicSize.Min),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            val fill by animateColorAsState(
                if (active) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceVariant,
                label = "segmentFill",
            )
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(fill)
                    .selectable(selected = active, role = Role.Tab, onClick = { haptics.tick(); onSelect(index) })
                    .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A card surface for custom content (timer, charts) with the system's card colour and radius. */
@Composable
fun AppCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(18.dp), content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

class Haptics internal constructor(private val feedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    /** Light tick for toggles and segment changes. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** Firm confirmation for starting or ending a fast. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberHaptics(): Haptics = Haptics(LocalHapticFeedback.current)
