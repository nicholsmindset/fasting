package com.fastingshift.app.ui.log

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.convertWeight
import com.fastingshift.app.ui.components.formatDate
import com.fastingshift.app.ui.components.weightText
import com.fastingshift.app.ui.components.TabularNumbers
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import java.time.Duration
import java.time.Instant

/** Days of weight shown in the trend chart. */
const val WEIGHT_TREND_DAYS = 90L

/** The entries a 90-day trend shows, oldest first. */
fun weightTrendPoints(weights: List<WeightLog>, now: Instant): List<WeightLog> {
    val from = now.minus(Duration.ofDays(WEIGHT_TREND_DAYS))
    return weights.filter { !it.at.isBefore(from) && !it.at.isAfter(now) }.sortedBy { it.at }
}

/**
 * Connects recorded weigh-ins at their actual dates within the last 90 days.
 */
@Composable
fun WeightTrendCard(points: List<WeightLog>, unit: WeightUnit, now: Instant) {
    Column(
        Modifier.fillMaxWidth().testTag("weight_chart").padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.log_trend_title), style = MaterialTheme.typography.titleMedium)
        if (points.size < 2) {
            Text(stringResource(R.string.log_trend_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val values = points.map { convertWeight(it.value, it.unit, unit) }
        val min = values.min()
        val max = values.max()
        val first = values.first()
        val last = values.last()
        val a11y = stringResource(R.string.log_trend_a11y, points.size, weightText(first, unit), weightText(last, unit), weightText(min, unit), weightText(max, unit))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(stringResource(R.string.log_trend_latest), weightText(last, unit), Modifier.weight(1.2f), prominent = true)
            Label(stringResource(R.string.log_trend_low), weightText(min, unit), Modifier.weight(1f))
            Label(stringResource(R.string.log_trend_high), weightText(max, unit), Modifier.weight(1f))
        }
        val line = MaterialTheme.colorScheme.primary
        val grid = MaterialTheme.colorScheme.outlineVariant
        val dotFill = MaterialTheme.colorScheme.background
        val from = now.minus(Duration.ofDays(WEIGHT_TREND_DAYS)).toEpochMilli()
        val span = (now.toEpochMilli() - from).coerceAtLeast(1L).toDouble()
        Box(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp).clearAndSetSemantics { contentDescription = a11y }) {
            Canvas(Modifier.fillMaxWidth().height(134.dp)) {
                val pad = 8.dp.toPx()
                val h = size.height - pad * 2
                val range = (max - min).coerceAtLeast(1.0)
                val midpoint = (min + max) / 2
                fun y(v: Double) = pad + h / 2 - ((v - midpoint) / range * h).toFloat()
                fun x(at: Instant) = pad + ((at.toEpochMilli() - from) / span).toFloat() * (size.width - pad * 2)
                // Hairlines at the low and high values.
                listOf(y(max), y(min)).distinct().forEach { gy ->
                    drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
                }
                val pts = points.mapIndexed { i, p -> Offset(x(p.at), y(values[i])) }
                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                pts.forEach {
                    drawCircle(dotFill, 4.5.dp.toPx(), it)
                    drawCircle(line, 4.5.dp.toPx(), it, style = Stroke(2.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(now.minus(Duration.ofDays(WEIGHT_TREND_DAYS)).formatDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(now.formatDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Label(label: String, value: String, modifier: Modifier = Modifier, prominent: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
        Text(value, style = style.merge(TabularNumbers))
    }
}
