package com.fastingshift.app.ui.partner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.LoadingBox
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SecondaryButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

@Composable
internal fun PartnerTimerEntry(onOpen: () -> Unit, vm: PartnerWeekViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val week = state.week
    val subtitle = when (week?.status) {
        "pending" -> stringResource(R.string.partner_pending_title)
        "active" -> week.partner?.let { stringResource(R.string.partner_with, it.alias) }
        "finished" -> stringResource(R.string.partner_recap)
        else -> stringResource(R.string.partner_entry_hint)
    }
    InsetGroup {
        GroupRow(stringResource(R.string.partner_title), Modifier.testTag("partner_open"),
            subtitle = subtitle, showChevron = true, onClick = onOpen)
    }
}

@Composable
internal fun PartnerWeekRoute(invite: String?, onBack: () -> Unit, vm: PartnerWeekViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var requested by rememberSaveable(invite) { mutableStateOf(invite) }
    var joining by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val week = state.week
    val preview = state.preview?.takeIf { it.code == requested }
    val token = requested
    LaunchedEffect(owner, vm) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refresh()
                delay(30_000)
            }
        }
    }
    LaunchedEffect(token) {
        if (token != null && PartnerLinks.valid(token)) vm.preview(token)
    }
    LaunchedEffect(week?.id, week?.status, joining) {
        if (joining && week?.status == "active") {
            requested = null
            manual = false
            joining = false
            vm.dismissPreview()
        }
    }
    fun cancelInvite() {
        requested = null
        manual = false
        joining = false
        vm.dismissPreview()
        vm.dismissMessage()
    }

    SecondaryScaffold(stringResource(R.string.partner_title), onBack) {
        state.message?.let { message ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(stringResource(message), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = vm::dismissMessage) { Text(stringResource(R.string.partner_dismiss)) }
                }
            }
        }
        when {
            state.leaving -> {
                Notice(stringResource(R.string.partner_leaving))
                SecondaryButton(stringResource(R.string.partner_retry), vm::refresh, Modifier.testTag("partner_refresh"),
                    enabled = !state.busy)
            }
            week != null && week.status != "left" -> {
                if (token != null && !joining) {
                    Notice(stringResource(R.string.partner_existing))
                    TextButton(onClick = ::cancelInvite) { Text(stringResource(R.string.partner_dismiss)) }
                }
                when (week.status) {
                    "pending" -> WaitingWeek(week)
                    "active", "finished" -> WeekProgress(week, state, vm)
                }
                TextButton(onClick = vm::refresh, enabled = !state.busy, modifier = Modifier.testTag("partner_refresh")) {
                    Text(stringResource(R.string.partner_refresh))
                }
                TextButton(onClick = { confirmLeave = true }, enabled = !state.busy, modifier = Modifier.testTag("partner_leave")) {
                    Text(stringResource(if (week.status == "pending") R.string.partner_cancel_invite else R.string.partner_leave))
                }
            }
            token != null -> {
                when {
                    !PartnerLinks.valid(token) -> Notice(stringResource(R.string.partner_invalid))
                    preview != null -> JoinForm(preview, state, onJoin = { alias, supporter ->
                        joining = true
                        vm.join(token, alias, supporter)
                    })
                    state.busy -> LoadingBox()
                    else -> {
                        Notice(stringResource(R.string.partner_cannot_preview))
                        SecondaryButton(stringResource(R.string.partner_retry), { vm.preview(token) }, Modifier.testTag("partner_retry"))
                    }
                }
                TextButton(onClick = ::cancelInvite) { Text(stringResource(R.string.partner_close_invite)) }
            }
            manual -> PasteForm(onPreview = { requested = it }, onCancel = { manual = false })
            state.participantAllowed == null -> LoadingBox()
            else -> {
                if (state.participantAllowed == true) {
                    var alias by rememberSaveable { mutableStateOf("") }
                    Heading(stringResource(R.string.partner_start))
                    Notice(stringResource(R.string.partner_intro))
                    AliasField(alias) { alias = it }
                    Notice(stringResource(R.string.partner_privacy))
                    PrimaryButton(stringResource(R.string.partner_create), { vm.create(alias.trim()) },
                        Modifier.testTag("partner_create"), enabled = alias.isNotBlank() && !state.busy)
                } else {
                    Notice(stringResource(R.string.partner_support_only))
                }
                SecondaryButton(stringResource(R.string.partner_open_invite), { manual = true }, Modifier.testTag("partner_paste"),
                    enabled = !state.busy)
            }
        }
    }
    if (confirmLeave && week != null) {
        val pending = week.status == "pending"
        AlertDialog(onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(if (pending) R.string.partner_cancel_question else R.string.partner_leave_question)) },
            text = { Text(stringResource(if (pending) R.string.partner_cancel_note else R.string.partner_leave_note)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; cancelInvite(); vm.leave() }, modifier = Modifier.testTag("partner_confirm_leave")) {
                    Text(stringResource(if (pending) R.string.partner_cancel_invite else R.string.partner_leave))
                }
            }, dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.partner_keep)) }
            })
    }
}

@Composable
private fun Heading(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AliasField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { if (it.codePointCount(0, it.length) <= 32) onChange(it) },
        modifier = Modifier.fillMaxWidth().testTag("partner_alias"), singleLine = true,
        label = { Text(stringResource(R.string.partner_alias)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
}

@Composable
private fun PasteForm(onPreview: (String) -> Unit, onCancel: () -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    Heading(stringResource(R.string.partner_open_invite))
    OutlinedTextField(value, { value = it.take(512); invalid = false }, modifier = Modifier.fillMaxWidth().testTag("partner_link"),
        label = { Text(stringResource(R.string.partner_link_label)) }, singleLine = true, isError = invalid,
        supportingText = if (invalid) { { Text(stringResource(R.string.partner_invalid)) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    PrimaryButton(stringResource(R.string.partner_preview), {
        val token = PartnerLinks.token(value)
        if (token == null) invalid = true else onPreview(token)
    }, Modifier.testTag("partner_preview"), enabled = value.isNotBlank())
    TextButton(onClick = onCancel) { Text(stringResource(R.string.partner_back)) }
}

@Composable
private fun JoinForm(preview: PartnerInvitePreview, state: PartnerUiState, onJoin: (String, Boolean) -> Unit) {
    var alias by rememberSaveable(preview.code) { mutableStateOf("") }
    var supporter by rememberSaveable(preview.code) { mutableStateOf(false) }
    val canParticipate = state.participantAllowed == true
    val cheering = supporter || !canParticipate
    Heading(stringResource(R.string.partner_invited, preview.hostAlias))
    Notice(stringResource(R.string.partner_join_intro))
    AliasField(alias) { alias = it }
    AppCard {
        Column(Modifier.selectableGroup()) {
            if (canParticipate) {
                RoleChoice(stringResource(R.string.partner_participate), stringResource(R.string.partner_participate_hint),
                    !cheering, "partner_participant") { supporter = false }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            RoleChoice(stringResource(R.string.partner_support), stringResource(R.string.partner_support_hint),
                cheering, "partner_supporter") { supporter = true }
        }
    }
    Notice(stringResource(R.string.partner_privacy))
    PrimaryButton(stringResource(R.string.partner_join), { onJoin(alias.trim(), cheering) }, Modifier.testTag("partner_join"),
        enabled = alias.isNotBlank() && state.participantAllowed != null && !state.busy)
}

@Composable
private fun RoleChoice(title: String, note: String, selected: Boolean, tag: String, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).selectable(selected, role = Role.RadioButton, onClick = onSelect)
        .heightIn(min = 56.dp).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected, null)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WaitingWeek(week: PartnerWeek) {
    val context = LocalContext.current
    Heading(stringResource(R.string.partner_pending_title))
    Notice(stringResource(R.string.partner_waiting))
    val token = week.inviteCode?.takeIf(PartnerLinks::valid) ?: return
    val link = PartnerLinks.link(token)
    PrimaryButton(stringResource(R.string.partner_share), {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
            context.getString(R.string.partner_share_text, link))
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.partner_share)))
    }, Modifier.testTag("partner_share"))
    SecondaryButton(stringResource(R.string.partner_copy), {
        val clip = ClipData.newPlainText(context.getString(R.string.partner_title), link)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        if (android.os.Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, R.string.partner_copied, Toast.LENGTH_SHORT).show()
        }
    }, Modifier.testTag("partner_copy"))
    week.inviteExpiresAt?.let { expires ->
        val date = runCatching { Instant.parse(expires).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        if (date != null) Notice(stringResource(R.string.partner_expires, date.format(shortDate)))
    }
}

private val shortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private fun localDate(value: String?) = value?.let { runCatching { LocalDate.parse(it).format(shortDate) }.getOrNull() }

@Composable
private fun WeekProgress(week: PartnerWeek, state: PartnerUiState, vm: PartnerWeekViewModel) {
    val finished = week.status == "finished"
    Heading(stringResource(if (finished) R.string.partner_recap else R.string.partner_week_heading))
    val start = localDate(week.startDate)
    val end = localDate(week.endDate)
    if (start != null && end != null) Notice(stringResource(R.string.partner_period, start, end))
    AppCard {
        MarkRow(week.self, stringResource(R.string.partner_you, week.self.alias), "partner_self_marks")
        week.partner?.let {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MarkRow(it, it.alias, "partner_other_marks")
        }
    }
    if (state.pendingDays.isNotEmpty()) Notice(stringResource(R.string.partner_queued))
    else if (state.offline) Notice(stringResource(R.string.partner_offline))
    week.lastCheer?.let { cheer ->
        val message = when (cheer) {
            "here" -> R.string.partner_reaction_here
            "thanks" -> R.string.partner_reaction_thanks
            else -> R.string.partner_reaction_glad
        }
        week.partner?.let { sender ->
            Text(stringResource(R.string.partner_encouragement_from, sender.alias, stringResource(message)),
                Modifier.testTag("partner_received").semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleMedium)
        }
    }

    if (finished) {
        val participants = listOfNotNull(week.self, week.partner).filter { it.role == "participant" }
        if (participants.isNotEmpty() && participants.all { it.days.distinct().size >= 3 }) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(20.dp).semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.partner_week_complete),
                        style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.partner_keepsake_note), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        if (week.renewalRequestedByMe) Notice(stringResource(R.string.partner_renew_wait))
        else if (week.renewalRequestedByPartner) Notice(stringResource(R.string.partner_renew_invited))
        if (week.canRenew && !week.renewalRequestedByMe) {
            PrimaryButton(stringResource(if (week.renewalRequestedByPartner) R.string.partner_renew_accept else R.string.partner_renew),
                vm::renew, Modifier.testTag("partner_renew"), enabled = !state.busy)
        }
    } else {
        if (week.self.role == "participant" && state.participantAllowed == true) {
            val checked = week.today in week.self.days
            val full = week.self.days.distinct().size >= 3
            val label = when {
                full -> R.string.partner_all_marks
                checked -> R.string.partner_checked
                else -> R.string.partner_checkin
            }
            PrimaryButton(stringResource(label), vm::checkIn, Modifier.testTag("partner_checkin"),
                enabled = week.canCheckIn && !state.busy)
            if (!checked && !full) Notice(stringResource(R.string.partner_checkin_hint))
        }
        if (week.partner != null) {
            val sentToday = !week.canCheer && week.startDate?.let { week.today >= it } == true &&
                week.endDate?.let { week.today <= it } == true
            SecondaryButton(stringResource(if (sentToday) R.string.partner_cheered else R.string.partner_cheer),
                { vm.cheer("glad") }, Modifier.testTag("partner_cheer"),
                enabled = week.canCheer && !state.busy)
        }
        if (week.self.role == "participant" && state.participantAllowed == true && week.self.days.distinct().size < 3) {
            PartnerReminderRow(weekId = week.id, enabledForWeek = true)
        }
    }
}

@Composable
private fun MarkRow(member: PartnerMember, name: String, tag: String) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        if (member.role == "supporter") {
            Text(stringResource(R.string.partner_supporter_label), color = colors.onSurfaceVariant)
        } else {
            val count = member.days.distinct().size.coerceIn(0, 3)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) { index ->
                    val mark = Modifier.size(20.dp).border(1.5.dp, if (index < count) colors.primary else colors.outline, CircleShape)
                    Box(if (index < count) mark.background(colors.primary, CircleShape) else mark)
                }
            }
            Text(stringResource(R.string.partner_count, count), style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant)
        }
    }
}
