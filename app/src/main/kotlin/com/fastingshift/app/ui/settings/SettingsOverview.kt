package com.fastingshift.app.ui.settings

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.fastingshift.app.ui.components.rememberWeekFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.ui.components.formatDate
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.LargeTitle
import com.fastingshift.app.ui.design.Spacing
import com.fastingshift.data.settings.AppAppearance
import com.fastingshift.domain.billing.Entitlement
import com.fastingshift.domain.billing.PlanType
import java.util.Locale

internal enum class SettingsSection(@StringRes val title: Int, @StringRes val summary: Int?) {
    REMINDERS(R.string.settings_reminders, R.string.settings_reminders_summary),
    FASTING(R.string.settings_fasting_preferences, R.string.settings_fasting_summary),
    PRIVACY(R.string.settings_privacy, R.string.settings_privacy_summary),
    SAFETY(R.string.settings_safety_reports, R.string.settings_safety_summary),
    PLAN(R.string.settings_plan, null);

    private val slug get() = name.lowercase(Locale.ROOT)
    val route get() = "settings/$slug"
    val overviewTag get() = "settings_open_$slug"
}

@Composable
internal fun SettingsOverview(
    appearance: AppAppearance,
    planner: PlannerState?,
    entitlement: Entitlement,
    onOpenWeek: () -> Unit,
    onAppearance: () -> Unit,
    onSection: (SettingsSection) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().testTag("settings_overview").verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
    ) {
        LargeTitle(stringResource(R.string.settings_title))
        WeekShortcut(planner, onOpenWeek)
        InsetGroup(header = stringResource(R.string.settings_group_routine)) {
            SectionRow(SettingsSection.REMINDERS, onSection)
            GroupDivider()
            SectionRow(SettingsSection.FASTING, onSection)
        }
        InsetGroup(header = stringResource(R.string.settings_group_app)) {
            GroupRow(
                stringResource(R.string.appearance_title), modifier = Modifier.testTag("settings_open_appearance"),
                value = stringResource(appearanceLabel(appearance)),
                showChevron = true, opensPicker = true, onClick = onAppearance,
            )
            GroupDivider()
            LanguageRow()
        }
        InsetGroup(header = stringResource(R.string.settings_group_account)) {
            SectionRow(SettingsSection.SAFETY, onSection)
            GroupDivider()
            SectionRow(SettingsSection.PRIVACY, onSection)
            GroupDivider()
            val tier = stringResource(planLabel(entitlement.plan))
            GroupRow(stringResource(SettingsSection.PLAN.title),
                modifier = Modifier.testTag(SettingsSection.PLAN.overviewTag),
                subtitle = entitlement.expiresAt?.let {
                    stringResource(if (entitlement.willRenew) R.string.plan_renews else R.string.plan_ends, it.formatDate())
                },
                value = if (entitlement.inTrial) "$tier · " + stringResource(R.string.plan_trial) else tier,
                showChevron = true, onClick = { onSection(SettingsSection.PLAN) })
            if (entitlement.plan == PlanType.MONTHLY || entitlement.plan == PlanType.ANNUAL) {
                GroupDivider()
                ManagePlanRow()
            }
        }
    }
}

@Composable
private fun SectionRow(section: SettingsSection, onSection: (SettingsSection) -> Unit) {
    GroupRow(stringResource(section.title), modifier = Modifier.testTag(section.overviewTag),
        subtitle = section.summary?.let { stringResource(it) }, showChevron = true, onClick = { onSection(section) })
}

@Composable
private fun WeekShortcut(planner: PlannerState?, onClick: () -> Unit) {
    val format = rememberWeekFormat()
    val today = planner?.today ?: LocalDate.now()
    val days = (0L..6L).map { offset ->
        val date = today.plusDays(offset)
        date to planner?.starts(date).orEmpty().map { it.second }
    }
    val next = planner?.let { state -> days.asSequence().flatMap { it.second.asSequence() }.firstOrNull { it > state.now }
        ?.atZone(state.zone) }
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.primaryContainer) {
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_schedules_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onPrimaryContainer)
            }
            val subtitle = when {
                next != null -> stringResource(R.string.week_timer_planned, format.dateTime.format(next))
                planner?.routines?.any { it.enabled } == true -> stringResource(R.string.settings_no_starts_this_week)
                else -> stringResource(R.string.settings_schedules_subtitle)
            }
            Text(subtitle, minLines = 2, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer)
            BoxWithConstraints {
                if (maxWidth >= 280.dp && LocalDensity.current.fontScale <= 1.3f) {
                    val scheduled = days.filter { it.second.isNotEmpty() }.joinToString(", ") { it.first.format(format.date) }
                    val description = when {
                        planner == null -> null
                        scheduled.isEmpty() -> stringResource(R.string.settings_no_starts_this_week)
                        else -> stringResource(R.string.settings_planned_dates, scheduled)
                    }
                    Row(Modifier.fillMaxWidth().clearAndSetSemantics { if (description != null) contentDescription = description },
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val dayNumber = DateTimeFormatter.ofPattern("d", format.locale)
                        days.forEachIndexed { index, (date, starts) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, format.locale),
                                    style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer)
                                Box(Modifier.size(30.dp).then(if (index == 0) Modifier.background(colors.onPrimaryContainer, CircleShape)
                                    else Modifier), contentAlignment = Alignment.Center) {
                                    Text(dayNumber.format(date), style = MaterialTheme.typography.labelLarge,
                                        color = if (index == 0) colors.primaryContainer else colors.onPrimaryContainer)
                                }
                                Box(Modifier.fillMaxWidth(0.55f).height(3.dp).background(
                                    when {
                                        planner == null -> Color.Transparent
                                        starts.isNotEmpty() -> colors.onPrimaryContainer
                                        else -> colors.onPrimaryContainer.copy(alpha = 0.14f)
                                    },
                                    CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

@StringRes
internal fun planLabel(plan: PlanType): Int = when (plan) {
    PlanType.FREE -> R.string.plan_free
    PlanType.MONTHLY -> R.string.plan_monthly
    PlanType.ANNUAL -> R.string.plan_annual
    PlanType.LIFETIME -> R.string.plan_lifetime
}

@Composable
internal fun ManagePlanRow() {
    val context = LocalContext.current
    GroupRow(stringResource(R.string.plan_cancel), showChevron = true, onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, BillingManager.playSubscriptionsUri(context.packageName))) }
    })
}
