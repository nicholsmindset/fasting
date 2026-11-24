package com.fastingshift.app.ui.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import androidx.compose.material3.LinearProgressIndicator
import com.fastingshift.app.ui.components.formatTime
import com.fastingshift.app.ui.components.waterLabel
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
    Column(modifier.fillMaxWidth().testTag("today_card"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.daily_today_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val waterValue = stringResource(R.string.daily_water_of, waterLabel(today.waterMl), waterLabel(today.waterGoalMl.toDouble()))
        val waterA11y = stringResource(R.string.daily_water_a11y, today.waterMl.toInt(), today.waterGoalMl)
        val addLabel = stringResource(R.string.daily_add_water)
        Row(
            Modifier.fillMaxWidth().testTag("today_water")
                .clearAndSetSemantics {
                    contentDescription = waterA11y
                    role = Role.Button
                    onClick(label = addLabel) { onAddWater(); true }
                }
                .clickable(onClickLabel = addLabel, role = Role.Button) { haptics.tick(); onAddWater() }
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.daily_water), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(waterValue, style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { today.waterProgress },
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Text(
                stringResource(R.string.log_add_water, 250),
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val next = when (val n = today.next) {
            NextFast.FastingNow -> stringResource(R.string.daily_fasting_now)
            is NextFast.At -> n.at.formatTime()
            NextFast.None -> stringResource(R.string.daily_next_none)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TodayStat(stringResource(R.string.daily_next_fast), next, Modifier.weight(1f).testTag("today_next"))
            TodayStat(stringResource(R.string.daily_streak), pluralStringResource(R.plurals.daily_streak_days, today.streak, today.streak),
                Modifier.weight(1f).testTag("today_streak"))
        }
    }
}

@Composable
private fun TodayStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
