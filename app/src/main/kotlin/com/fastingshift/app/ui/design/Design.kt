package com.fastingshift.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Fasting Shift design system (iOS-inspired). Use these instead of raw Material components
 * so every screen shares one calm, premium look:
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

/** Big bold title at the top of a tab screen (scrolls with content, like iOS large titles). */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Text(text, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small uppercase-free caption above a group, iOS style. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, bottom = 6.dp, top = 4.dp),
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
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
        if (showChevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** A row with an iOS-style switch; the whole row toggles (accessible as one switch). */
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
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
                uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}

private val ButtonShape = RoundedCornerShape(14.dp)

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
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** iOS-style segmented control with a sliding selection. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selected, spring(dampingRatio = 0.85f, stiffness = 500f), label = "segment")
        Box(
            Modifier.offset(x = offset).width(segment).heightIn(min = 32.dp)
                .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val color by animateColorAsState(
                    if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "segmentText",
                )
                Box(
                    Modifier.weight(1f).heightIn(min = 32.dp)
                        .selectable(selected = i == selected, role = Role.Tab, onClick = { haptics.tick(); onSelect(i) }),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1) }
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
