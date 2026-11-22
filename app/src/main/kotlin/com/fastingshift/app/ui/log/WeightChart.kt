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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.fastingshift.app.ui.design.AppCard
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
 * Free weight trend (last 90 days): a smooth line with a dot per entry, plus the lowest and
 * highest values. Neutral colours: no "good" or "bad" direction.
 */
@Composable
fun WeightTrendCard(points: List<WeightLog>, unit: WeightUnit, now: Instant) {
    AppCard(Modifier.testTag("weight_chart")) {
        Text(stringResource(R.string.log_trend_title), style = MaterialTheme.typography.titleMedium)
        if (points.size < 2) {
            Text(stringResource(R.string.log_trend_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppCard
        }
        val values = points.map { convertWeight(it.value, it.unit, unit) }
        val min = values.min()
        val max = values.max()
        val first = values.first()
        val last = values.last()
        val a11y = stringResource(R.string.log_trend_a11y, points.size, weightText(first, unit), weightText(last, unit), weightText(min, unit), weightText(max, unit))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(stringResource(R.string.log_trend_low), weightText(min, unit))
            Label(stringResource(R.string.log_trend_latest), weightText(last, unit), Alignment.CenterHorizontally)
            Label(stringResource(R.string.log_trend_high), weightText(max, unit), Alignment.End)
        }
        val line = MaterialTheme.colorScheme.primary
        val grid = MaterialTheme.colorScheme.outlineVariant
        val dotFill = MaterialTheme.colorScheme.surfaceContainer
        val from = now.minus(Duration.ofDays(WEIGHT_TREND_DAYS)).toEpochMilli().toFloat()
        val span = (now.toEpochMilli() - from).coerceAtLeast(1f)
        Box(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp).clearAndSetSemantics { contentDescription = a11y }) {
            Canvas(Modifier.fillMaxWidth().height(134.dp)) {
                val pad = 8.dp.toPx()
                val h = size.height - pad * 2
                val range = (max - min).takeIf { it > 0.05 } ?: 1.0
                fun y(v: Double) = pad + h - ((v - min) / range * h).toFloat()
                fun x(at: Instant) = pad + (at.toEpochMilli() - from) / span * (size.width - pad * 2)
                // Hairlines at the low and high values.
                listOf(y(max), y(min)).forEach { gy ->
                    drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
                }
                val pts = points.mapIndexed { i, p -> Offset(x(p.at), y(values[i])) }
                // Smooth curve through the points (horizontal-tangent cubic segments).
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val p0 = pts[i - 1]
                        val p1 = pts[i]
                        val mx = (p0.x + p1.x) / 2
                        cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                    }
                }
                val fill = Path().apply {
                    addPath(path)
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.18f), line.copy(alpha = 0f))))
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
private fun Label(label: String, value: String, align: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = align) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
