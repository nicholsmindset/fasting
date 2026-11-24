package com.fastingshift.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SectionCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    // Flat white card on the grouped background (iOS look), not an elevated Material card.
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/**
 * Secondary screens: a top app bar with an up arrow (no bottom "Back" buttons), an optional
 * snackbar host, and content padded below the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondaryScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // iOS-style large title that collapses into the bar as you scroll.
    val scroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** Centered spinner for the moment before a screen's data has loaded. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** "16h 20m" (in the app's language) — for summaries; the live timer uses H:MM:SS. */
fun Duration.hoursMinutes(res: android.content.res.Resources): String {
    val h = toHours()
    val m = toMinutes() % 60
    return if (m == 0L) res.getString(R.string.duration_h, h) else res.getString(R.string.duration_hm, h, m)
}

@Composable
fun Duration.hoursMinutes(): String = hoursMinutes(androidx.compose.ui.platform.LocalResources.current)

/** "16h" — whole hours, in the app's language. */
@Composable
fun hoursLabel(hours: Long): String = stringResource(R.string.duration_h, hours)

/** "25m" — minutes, in the app's language. */
@Composable
fun minutesLabel(minutes: Long): String = stringResource(R.string.duration_m, minutes)

const val LB_PER_KG = 2.2046226

/** Converts a weight to [unit] for display. */
fun convertWeight(value: Double, from: WeightUnit, to: WeightUnit): Double = when {
    from == to -> value
    to == WeightUnit.LB -> value * LB_PER_KG
    else -> value / LB_PER_KG
}

/** "82.1 kg" / "181.0 lb" from string resources. */
@Composable
fun weightText(value: Double, unit: WeightUnit): String =
    stringResource(if (unit == WeightUnit.KG) R.string.unit_kg_value else R.string.unit_lb_value, value)

@Composable
fun weightUnitLabel(unit: WeightUnit): String = stringResource(if (unit == WeightUnit.KG) R.string.unit_kg else R.string.unit_lb)

private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Formats in the device's current zone, read at call time so zone changes show correctly. */
fun Instant.formatDateTime(locale: Locale = Locale.getDefault(Locale.Category.FORMAT)): String =
    dateTime.withLocale(locale).format(atZone(ZoneId.systemDefault()))
fun Instant.formatDate(): String = date.format(atZone(ZoneId.systemDefault()))
fun Instant.formatTime(): String = time.format(atZone(ZoneId.systemDefault()))

/** Local midnight, re-checked every minute so "today" totals reset at midnight (and on zone changes). */
fun startOfTodayFlow(clock: java.time.Clock): Flow<Instant> = flow {
    while (true) {
        val zone = ZoneId.systemDefault()
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        emit(today.atStartOfDay(zone).toInstant())
        val untilMidnight = Duration.between(now, today.plusDays(1).atStartOfDay(zone).toInstant()).toMillis()
        delay(untilMidnight.coerceIn(1_000, 60_000))
    }
}.distinctUntilChanged()

/** The latest weight in kilograms, if any. */
fun List<WeightLog>.latestKg(): Double? =
    maxByOrNull { it.at }?.let { convertWeight(it.value, it.unit, WeightUnit.KG) }

/** "750 ml" / "1.3 L" for water amounts. */
fun waterLabel(ml: Double): String =
    if (ml >= 1_000) String.format(Locale.getDefault(), "%.1f L", ml / 1_000)
    else String.format(Locale.getDefault(), "%d ml", ml.toInt())

/** Digits that don't jiggle as they change (tabular figures), for timers and totals. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/**
 * A clean progress ring: soft track, solid accent arc with rounded caps, animated when the
 * value changes. Decorative; callers set the semantics on their container.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.14f),
) {
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "ring",
    )
    Canvas(modifier) {
        val px = stroke.toPx()
        val inset = px / 2
        val arc = Size(size.width - px, size.height - px)
        val topLeft = Offset(inset, inset)
        drawArc(trackColor, 0f, 360f, false, topLeft, arc, style = Stroke(px))
        if (animated > 0f) {
            drawArc(color, -90f, 360f * animated, false, topLeft, arc, style = Stroke(px, cap = StrokeCap.Round))
        }
    }
}
