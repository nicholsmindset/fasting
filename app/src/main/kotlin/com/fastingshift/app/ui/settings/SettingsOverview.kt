package com.fastingshift.app.ui.settings

import android.content.Intent
import androidx.annotation.StringRes
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
        InsetGroup {
            GroupRow(
                stringResource(R.string.settings_schedules_title),
                subtitle = stringResource(R.string.settings_schedules_subtitle),
                showChevron = true,
                onClick = onOpenWeek,
            )
            GroupDivider()
            GroupRow(
                stringResource(R.string.appearance_title),
                value = stringResource(appearanceLabel(appearance)),
                showChevron = true, opensPicker = true, onClick = onAppearance,
            )
        }
        InsetGroup {
            SettingsSection.entries.forEachIndexed { index, section ->
                if (index > 0) GroupDivider()
                val summary = section.summary
                val plan = section == SettingsSection.PLAN
                GroupRow(
                    stringResource(section.title),
                    modifier = Modifier.testTag(section.overviewTag),
                    subtitle = if (summary != null) stringResource(summary) else entitlement.expiresAt?.let {
                        stringResource(if (entitlement.willRenew) R.string.plan_renews else R.string.plan_ends, it.formatDate())
                    },
                    value = if (plan) {
                        val tier = stringResource(planLabel(entitlement.plan))
                        if (entitlement.inTrial) "$tier · " + stringResource(R.string.plan_trial) else tier
                    } else null,
                    showChevron = true,
                    onClick = { onSection(section) },
                )
            }
            if (entitlement.plan == PlanType.MONTHLY || entitlement.plan == PlanType.ANNUAL) {
                GroupDivider()
                ManagePlanRow()
            }
            GroupDivider()
            LanguageRow()
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
