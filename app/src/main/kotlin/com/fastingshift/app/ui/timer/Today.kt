package com.fastingshift.app.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.ProgressRing
import com.fastingshift.app.ui.components.formatTime
import com.fastingshift.app.ui.components.waterLabel
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.domain.hydration.WaterGoal
import java.time.Instant

/** When the next fast begins, as shown on the Today card. */
sealed interface NextFast {
    data object FastingNow : NextFast
    data class At(val at: Instant) : NextFast
    data object None : NextFast
}

/** The Timer's "Today" card: next fast, water vs goal, current streak. */
data class TodaySummary(
    val next: NextFast = NextFast.None,
    val waterMl: Double = 0.0,
    val waterGoalMl: Int = WaterGoal.FALLBACK_ML,
    val streak: Int = 0,
) {
    val waterProgress: Float get() = WaterGoal.progress(waterMl, waterGoalMl)
}

object Today {
    /** The user's goal if they set one, otherwise the wellness default from their latest weight. */
    fun waterGoalMl(settingMl: Int?, latestWeightKg: Double?): Int = settingMl ?: WaterGoal.defaultMl(latestWeightKg)
}

@Composable
fun TodayCard(today: TodaySummary, onAddWater: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    AppCard(modifier.testTag("today_card")) {
        Text(stringResource(R.string.daily_today_title), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // Next fast
            val nextValue = when (val n = today.next) {
                NextFast.FastingNow -> stringResource(R.string.daily_fasting_now)
                is NextFast.At -> n.at.formatTime()
                NextFast.None -> stringResource(R.string.daily_next_none)
            }
            TodayStat(stringResource(R.string.daily_next_fast), nextValue, Modifier.weight(1f).testTag("today_next"))
            Divider()
            // Water: a small ring; tapping adds 250 ml.
            val waterValue = stringResource(R.string.daily_water_of, waterLabel(today.waterMl), waterLabel(today.waterGoalMl.toDouble()))
            val waterA11y = stringResource(R.string.daily_water_a11y, today.waterMl.toInt(), today.waterGoalMl)
            val addLabel = stringResource(R.string.daily_add_water)
            Column(
                Modifier.weight(1.2f).clip(RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = addLabel, role = Role.Button) { haptics.tick(); onAddWater() }
                    .clearAndSetSemantics {
                        contentDescription = waterA11y
                        role = Role.Button
                        onClick(label = addLabel) { onAddWater(); true }
                    }
                    .testTag("today_water")
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.daily_water), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(contentAlignment = Alignment.Center) {
                    ProgressRing(today.waterProgress, Modifier.size(44.dp), stroke = 5.dp, color = WaterBlue)
                    Text("+", style = MaterialTheme.typography.titleMedium, color = WaterBlue)
                }
                Text(waterValue, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
            }
            Divider()
            TodayStat(
                stringResource(R.string.daily_streak),
                pluralStringResource(R.plurals.daily_streak_days, today.streak, today.streak),
                Modifier.weight(1f).testTag("today_streak"),
            )
        }
    }
}

/** Water's own calm blue, used for every hydration ring. */
val WaterBlue: androidx.compose.ui.graphics.Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) androidx.compose.ui.graphics.Color(0xFF64B5F6)
    else androidx.compose.ui.graphics.Color(0xFF2F7DD1)

private fun androidx.compose.ui.graphics.Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun Divider() {
    Box(Modifier.padding(top = 6.dp).width(0.5.dp).height(64.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun TodayStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}
