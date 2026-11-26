package com.fastingshift.app.ui.challenge

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.BuildConfig
import com.fastingshift.app.ui.partner.PartnerLinks
import com.fastingshift.app.R
import com.fastingshift.app.share.ShareCards
import com.fastingshift.app.ui.components.LoadingBox
import com.fastingshift.app.ui.components.ProgressRing
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.SwitchGroupRow
import com.fastingshift.app.ui.design.SegmentedControl
import com.fastingshift.app.ui.onboarding.SelectGroupRow
import com.fastingshift.app.ui.onboarding.TextFieldGroupRow
import com.fastingshift.domain.challenge.ChallengeDay
import com.fastingshift.domain.challenge.ChallengeInvite
import com.fastingshift.domain.challenge.ChallengeLinks
import com.fastingshift.domain.challenge.ChallengeStatus
import com.fastingshift.domain.challenge.ChallengeTemplate
import com.fastingshift.domain.challenge.Challenges
import com.fastingshift.domain.challenge.DayState
import com.fastingshift.domain.model.ChallengeType
import com.fastingshift.domain.protocol.Presets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private fun LocalDate.text(): String = dateFormat.format(this)

/** "16:8 for 7 days", "Monday and Thursday fasts"… */
@Composable
fun challengeTypeTitle(type: ChallengeType, protocolId: String?, lengthDays: Int): String = when (type) {
    ChallengeType.DAILY_PROTOCOL -> pluralStringResource(R.plurals.challenge_daily_title, lengthDays, protocolId?.let(Presets::byId)?.name ?: "", lengthDays)
    ChallengeType.SUNNAH_MON_THU -> stringResource(R.string.challenge_monthu_title)
    ChallengeType.AYYAMUL_BIDH -> stringResource(R.string.challenge_white_title)
}

@Composable
private fun inviteTitle(i: ChallengeInvite): String = i.name ?: challengeTypeTitle(i.type, i.protocolId, i.lengthDays)

@Composable
private fun howItCounts(type: ChallengeType, protocolId: String?): String = when (type) {
    ChallengeType.DAILY_PROTOCOL -> stringResource(R.string.challenge_how_daily, protocolId?.let(Presets::byId)?.fastHours ?: 0)
    else -> stringResource(R.string.challenge_how_sunnah, Challenges.MIN_SUNNAH_FAST.toHours().toInt())
}

@Composable
private fun statusText(item: ChallengeItem): String = when {
    item.left -> stringResource(R.string.challenge_status_left)
    item.progress.status == ChallengeStatus.UPCOMING -> stringResource(R.string.challenge_status_upcoming, item.challenge.startDate.text())
    item.progress.status == ChallengeStatus.ACTIVE -> stringResource(R.string.challenge_status_active, item.challenge.endDate.text())
    else -> stringResource(R.string.challenge_status_finished)
}

@Composable
private fun progressLabel(item: ChallengeItem): String {
    val progress = item.progress
    return if (item.challenge.followsOwnPlan && progress.requiredDays == 0) {
        stringResource(R.string.friend_no_planned_days)
    } else {
        pluralStringResource(R.plurals.challenge_progress, progress.requiredDays, progress.doneDays, progress.requiredDays)
    }
}

@Composable
private fun PersonalPlanSwitch(enabled: Boolean, onChange: (Boolean) -> Unit) {
    SwitchGroupRow(
        title = stringResource(R.string.friend_own_plan), checked = enabled, onCheckedChange = onChange,
        subtitle = stringResource(R.string.friend_own_plan_hint),
    )
}

// ---------- list ----------

@Composable
fun ChallengeListRoute(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onJoinCode: (String) -> Unit,
    vm: ChallengeListViewModel = hiltViewModel(),
    onPartner: ((String?) -> Unit)? = null,
) {
    val st by vm.state.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    SecondaryScaffold(title = stringResource(R.string.challenges_title), onBack = onBack) {
        Text(stringResource(R.string.challenges_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val s = st
        if (s == null) {
            LoadingBox()
            return@SecondaryScaffold
        }
        onPartner?.let { open ->
            InsetGroup {
                GroupRow(stringResource(R.string.partner_title), modifier = Modifier.testTag("partner_open"),
                    subtitle = stringResource(R.string.partner_discover), showChevron = true, onClick = { open(null) })
            }
        }
        if (!s.allowed) {
            AppCard { Text(stringResource(R.string.challenges_not_allowed), style = MaterialTheme.typography.bodyMedium) }
        }
        if (s.current.isEmpty() && s.past.isEmpty()) {
            Text(stringResource(R.string.challenges_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s.current.isNotEmpty()) ChallengeGroup(stringResource(R.string.challenges_current), s.current, onOpen)
        if (s.allowed) PrimaryButton(stringResource(R.string.challenges_new), onClick = onNew)
        if (s.allowed) {
            InsetGroup(
                header = stringResource(R.string.challenges_have_link),
                footer = if (invalid) stringResource(R.string.challenges_link_invalid) else null,
            ) {
                TextFieldGroupRow(
                    stringResource(R.string.challenges_link_label),
                    link,
                    { link = it; invalid = false },
                    placeholder = stringResource(R.string.challenges_link_hint),
                    keyboardType = KeyboardType.Uri,
                    valueWidth = 200.dp,
                )
                GroupDivider()
                GroupRow(stringResource(R.string.challenges_open_link), titleColor = MaterialTheme.colorScheme.primary, onClick = {
                    val code = ChallengeLinks.codeFrom(link)
                    val partnerCode = PartnerLinks.token(link)
                    when {
                        partnerCode != null && onPartner != null -> onPartner(partnerCode)
                        code != null -> onJoinCode(code)
                        else -> invalid = true
                    }
                })
            }
        }
        if (s.past.isNotEmpty()) ChallengeGroup(stringResource(R.string.challenges_past), s.past, onOpen)
    }
}

@Composable
private fun ChallengeGroup(header: String, items: List<ChallengeItem>, onOpen: (Long) -> Unit) {
    InsetGroup(header = header) {
        items.forEachIndexed { i, item ->
            if (i > 0) GroupDivider()
            val c = item.challenge
            GroupRow(
                c.name ?: challengeTypeTitle(c.type, c.protocolId, c.lengthDays),
                subtitle = progressLabel(item) + " · " + statusText(item),
                leading = { ProgressRing(item.progress.fraction, Modifier.size(32.dp), stroke = 5.dp) },
                showChevron = true,
                onClick = { onOpen(c.id) },
            )
        }
    }
}

// ---------- create ----------

@Composable
fun CreateChallengeRoute(onBack: () -> Unit, onCreated: (Long) -> Unit, vm: CreateChallengeViewModel = hiltViewModel()) {
    val allowed by vm.allowed.collectAsStateWithLifecycle()
    var template by rememberSaveable { mutableStateOf(ChallengeTemplate.DAILY_16_8_WEEK) }
    var followsOwnPlan by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf(StartOption.TOMORROW) }
    var name by rememberSaveable { mutableStateOf("") }
    SecondaryScaffold(title = stringResource(R.string.challenge_new_title), onBack = onBack) {
        if (allowed == false) {
            AppCard { Text(stringResource(R.string.challenges_not_allowed), style = MaterialTheme.typography.bodyMedium) }
            return@SecondaryScaffold
        }
        if (allowed == null) {
            LoadingBox()
            return@SecondaryScaffold
        }
        InsetGroup(header = stringResource(R.string.challenge_choose)) {
            ChallengeTemplate.entries.forEachIndexed { i, t ->
                if (i > 0) GroupDivider()
                SelectGroupRow(
                    challengeTypeTitle(t.type, t.protocolId, t.lengthDays),
                    selected = t == template,
                    onClick = { template = t },
                    subtitle = stringResource(templateDescription(t)),
                )
            }
        }
        Column {
            Text(
                stringResource(R.string.challenge_start),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
            )
            SegmentedControl(
                options = listOf(stringResource(R.string.challenge_start_today), stringResource(R.string.challenge_start_tomorrow), stringResource(R.string.challenge_start_monday)),
                selected = start.ordinal,
                onSelect = { start = StartOption.entries[it] },
            )
            Text(
                start.date(vm.today()).text(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }
        InsetGroup(footer = stringResource(R.string.challenge_create_note)) {
            TextFieldGroupRow(
                stringResource(R.string.challenge_name),
                name,
                { name = if (it.codePointCount(0, it.length) <= 40) it else it.substring(0, it.offsetByCodePoints(0, 40)) },
                placeholder = stringResource(R.string.challenge_name_hint),
                keyboardType = KeyboardType.Text,
                valueWidth = 180.dp,
            )
        }
        PersonalPlanSwitch(followsOwnPlan) { followsOwnPlan = it }
        PrimaryButton(stringResource(R.string.challenge_create), onClick = { vm.create(template, start, name, followsOwnPlan, onCreated) }, haptic = true)
    }
}

private fun templateDescription(t: ChallengeTemplate) = when (t) {
    ChallengeTemplate.DAILY_16_8_WEEK -> R.string.challenge_tmpl_16_8
    ChallengeTemplate.DAILY_18_6_TWO_WEEKS -> R.string.challenge_tmpl_18_6
    ChallengeTemplate.MON_THU_FOUR_WEEKS -> R.string.challenge_tmpl_monthu
    ChallengeTemplate.WHITE_DAYS_THREE_MONTHS -> R.string.challenge_tmpl_white
}

// ---------- detail ----------

internal fun challengeInviteIntent(context: Context, title: String, link: String): Intent =
    Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_challenge_text, title, link))

@Composable
fun ChallengeDetailRoute(onBack: () -> Unit, onOpenWeek: () -> Unit, vm: ChallengeDetailViewModel = hiltViewModel()) {
    val item by vm.item.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val it = item
    SecondaryScaffold(
        title = it?.challenge?.let { c -> c.name ?: challengeTypeTitle(c.type, c.protocolId, c.lengthDays) } ?: stringResource(R.string.challenges_title),
        onBack = onBack,
    ) {
        if (it == null) {
            LoadingBox()
            return@SecondaryScaffold
        }
        val c = it.challenge
        val p = it.progress
        val title = c.name ?: challengeTypeTitle(c.type, c.protocolId, c.lengthDays)
        val link = ChallengeLinks.link(ChallengeInvite.of(c), pilot = BuildConfig.USE_PILOT_INVITES)
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressRing(p.fraction, Modifier.size(64.dp), stroke = 8.dp)
                Column {
                    Text(progressLabel(it), style = MaterialTheme.typography.titleLarge)
                    // The date range already says when it starts and ends; only "Finished"/"Left" add anything.
                    if (it.left || p.status == ChallengeStatus.FINISHED) {
                        Text(statusText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        stringResource(R.string.challenge_dates, c.startDate.text(), c.endDate.text()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (p.requiredDays > 0) {
                ProgressGrid(p.days, Modifier.testTag("challenge_grid"), p.doneDays, p.requiredDays)
                Legend()
            }
            Text(
                if (c.followsOwnPlan) stringResource(R.string.friend_personal_count) else howItCounts(c.type, c.protocolId),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!it.left) {
            if (p.status != ChallengeStatus.FINISHED) {
                InsetGroup {
                    PersonalPlanSwitch(c.followsOwnPlan) { enabled -> vm.setOwnPlan(enabled) }
                    if (c.followsOwnPlan) {
                        GroupDivider()
                        GroupRow(stringResource(R.string.week_title), showChevron = true, onClick = onOpenWeek)
                    }
                }
            }
            PrimaryButton(stringResource(R.string.challenge_invite), onClick = {
                val chooser = Intent.createChooser(challengeInviteIntent(context, title, link), null)
                runCatching { context.startActivity(chooser) }
            })
            if (p.requiredDays > 0) {
                SecondaryButton(stringResource(R.string.challenge_share_progress), onClick = {
                    ShareCards.shareChallenge(context, title, p.doneDays, p.requiredDays, link, c.followsOwnPlan)
                })
            }
            PlainButton(stringResource(R.string.friend_send_encouragement), onClick = {
                val message = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.friend_encouragement_message))
                context.startActivity(Intent.createChooser(message, null))
            })
        }
        Text(stringResource(R.string.challenge_join_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (it.left || p.status == ChallengeStatus.FINISHED) {
            PlainButton(stringResource(R.string.challenge_remove), onClick = { vm.remove(onBack) }, modifier = Modifier.fillMaxWidth())
        } else {
            PlainButton(stringResource(R.string.challenge_leave), onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.challenge_leave_confirm_title)) },
            text = { Text(stringResource(R.string.challenge_leave_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text(stringResource(R.string.challenge_leave_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** One square per day, a week to a row. Done is filled; missed days are only outlined (never red). */
@Composable
private fun ProgressGrid(days: List<ChallengeDay>, modifier: Modifier, done: Int, required: Int) {
    val a11y = pluralStringResource(R.plurals.challenge_grid_a11y, required, done, required)
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = a11y }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                week.forEach { d -> DayCell(d.state, Modifier.weight(1f)) }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(state: DayState, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(6.dp)
    val base = modifier.aspectRatio(1f).clip(shape)
    val cell = when (state) {
        DayState.DONE -> base.background(primary)
        DayState.TODAY -> base.border(2.dp, primary, shape)
        DayState.MISSED -> base.border(1.dp, MaterialTheme.colorScheme.outline, shape)
        DayState.UPCOMING -> base.background(primary.copy(alpha = 0.14f))
        DayState.REST -> base
    }
    Box(cell, contentAlignment = Alignment.Center) {
        if (state == DayState.REST) Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(MaterialTheme.colorScheme.primary, stringResource(R.string.challenge_legend_done))
        LegendItem(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), stringResource(R.string.challenge_legend_ahead))
        LegendItem(Color.Transparent, stringResource(R.string.challenge_legend_rest), dot = true)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color), contentAlignment = Alignment.Center) {
            if (dot) Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- join ----------

@Composable
fun JoinChallengeRoute(onBack: () -> Unit, onJoined: (Long) -> Unit, vm: JoinChallengeViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    SecondaryScaffold(title = stringResource(R.string.challenge_join_title), onBack = onBack) {
        when (val s = st) {
            JoinState.Loading -> LoadingBox()
            JoinState.Invalid -> AppCard { Text(stringResource(R.string.challenge_join_invalid), style = MaterialTheme.typography.bodyLarge) }
            JoinState.NewerVersion -> AppCard { Text(stringResource(R.string.challenge_join_newer), style = MaterialTheme.typography.bodyLarge) }
            JoinState.NotAllowed -> AppCard { Text(stringResource(R.string.challenges_not_allowed), style = MaterialTheme.typography.bodyLarge) }
            is JoinState.Ready -> {
                val i = s.invite
                var followsOwnPlan by rememberSaveable(i) { mutableStateOf(false) }
                AppCard(Modifier.testTag("join_card")) {
                    Text(inviteTitle(i), style = MaterialTheme.typography.titleLarge)
                    if (i.name != null) {
                        Text(challengeTypeTitle(i.type, i.protocolId, i.lengthDays), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stringResource(R.string.challenge_dates, i.startDate.text(), i.startDate.plusDays(i.lengthDays - 1L).text()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (followsOwnPlan && s.alreadyJoinedId == null) stringResource(R.string.friend_personal_count)
                        else howItCounts(i.type, i.protocolId),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.challenge_join_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val existing = s.alreadyJoinedId
                if (existing != null) {
                    Text(stringResource(R.string.challenge_join_already), style = MaterialTheme.typography.bodyMedium)
                    SecondaryButton(stringResource(R.string.challenge_open), onClick = { onJoined(existing) })
                } else {
                    PersonalPlanSwitch(followsOwnPlan) { followsOwnPlan = it }
                    PrimaryButton(stringResource(R.string.challenge_join), onClick = { vm.join(followsOwnPlan, onJoined) }, haptic = true)
                }
            }
        }
    }
}
