package com.fastingshift.app.ui.partner

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.fastingshift.app.share.ShareCards
import com.fastingshift.app.ui.components.LoadingBox
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.SwitchGroupRow
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PartnerReflectionInvite(onOpen: () -> Unit, vm: PartnerWeekViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.canInviteAfterReflection) {
        PlainButton(stringResource(R.string.partner_reflection_invite), onOpen, Modifier.testTag("partner_reflection_invite"))
    }
}

@Composable
internal fun PartnerTimerEntry(onOpen: () -> Unit, vm: PartnerWeekViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val week = state.week
    if (state.adultAllowed != true || !state.showTimerShortcut) return
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(week?.id, owner) {
        if (week != null) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() }
    }
    if (week?.status == "active") {
        val member = if (week.self.role == "participant") week.self else week.partner
        val other = week.partner
        val latestDay = localDate(other?.days?.maxOrNull())
        val subtitle = when {
            other != null && latestDay != null -> stringResource(R.string.partner_last_checkin, other.alias, latestDay)
            other != null -> stringResource(R.string.partner_with, other.alias)
            else -> stringResource(R.string.partner_week_heading)
        }
        AppCard(modifier = Modifier.testTag("partner_open").clickable(role = Role.Button, onClick = onOpen)) {
            Text(stringResource(R.string.partner_title), style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            member?.let {
                MarkDots(it.days.distinct().size)
                Text(stringResource(if (week.self.role == "participant") R.string.partner_your_count else R.string.partner_count,
                    it.days.distinct().size), style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    val subtitle = when (week?.status) {
        "pending" -> stringResource(R.string.partner_pending_title)
        "finished" -> stringResource(R.string.partner_recap)
        else -> stringResource(R.string.partner_discover)
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
    var shareOnCreate by rememberSaveable { mutableStateOf(false) }
    var qrOnCreate by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var manual by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val week = state.week
    val preview = state.preview?.takeIf { it.code == requested }
    val token = requested
    LaunchedEffect(shareOnCreate, week?.inviteCode, week?.status, state.message) {
        if (shareOnCreate) {
            val createdLink = week?.shareableInviteLink(Instant.now())
            if (createdLink != null) {
                shareOnCreate = false
                sharePartnerInvitation(context, createdLink)
            } else if (state.message != null) shareOnCreate = false
        }
    }
    LaunchedEffect(owner, vm) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refresh()
                delay(30_000)
            }
        }
    }
    LaunchedEffect(token, state.adultAllowed) {
        if (state.adultAllowed == true && token != null && PartnerLinks.valid(token)) vm.preview(token)
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
        state.message?.takeUnless { token != null && week == null && it == R.string.partner_error_unavailable }?.let { message ->
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
            state.adultAllowed == null -> LoadingBox()
            state.adultAllowed == false -> {
                Notice(stringResource(R.string.partner_adult_only))
                if (week != null) {
                    SecondaryButton(stringResource(R.string.partner_leave), { confirmLeave = true },
                        Modifier.testTag("partner_leave"), enabled = !state.busy)
                }
            }
            week != null && week.status != "left" -> {
                if (token != null && !joining) {
                    Notice(stringResource(R.string.partner_existing))
                    TextButton(onClick = ::cancelInvite) { Text(stringResource(R.string.partner_dismiss)) }
                }
                when (week.status) {
                    "pending" -> WaitingWeek(week, qrOnCreate) { qrOnCreate = false }
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
                    state.message == R.string.partner_error_unavailable -> {
                        Text(stringResource(R.string.partner_spent_link),
                            Modifier.testTag("partner_invite_unavailable").semantics { liveRegion = LiveRegionMode.Polite })
                        SecondaryButton(stringResource(R.string.partner_replace_invite), {
                            cancelInvite()
                            manual = true
                        })
                    }
                    else -> {
                        Notice(stringResource(R.string.partner_cannot_preview))
                        SecondaryButton(stringResource(R.string.partner_retry), { vm.preview(token) }, Modifier.testTag("partner_retry"))
                    }
                }
                TextButton(onClick = ::cancelInvite, modifier = Modifier.testTag("partner_close_invite")) { Text(stringResource(R.string.partner_close_invite)) }
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
                    PrimaryButton(stringResource(R.string.partner_create_share), { qrOnCreate = false; shareOnCreate = true; vm.create(alias.trim()) },
                        Modifier.testTag("partner_create"), enabled = alias.isNotBlank() && !state.busy)
                    SecondaryButton(stringResource(R.string.partner_invite_in_person), {
                        shareOnCreate = false
                        qrOnCreate = true
                        vm.create(alias.trim())
                    }, Modifier.testTag("partner_create_qr"), enabled = alias.isNotBlank() && !state.busy)
                } else {
                    Notice(stringResource(R.string.partner_support_only))
                }
                SecondaryButton(stringResource(R.string.partner_open_invite), { manual = true }, Modifier.testTag("partner_paste"),
                    enabled = !state.busy)
            }
        }
        if (state.adultAllowed == true) InsetGroup {
            SwitchGroupRow(stringResource(R.string.partner_timer_shortcut), state.showTimerShortcut, vm::setTimerShortcut)
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
private fun Notice(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

internal fun sharePartnerInvitation(context: Context, link: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.partner_share_text, link))
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.partner_share)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.partner_copy_fallback, Toast.LENGTH_LONG).show()
    }
}

@Composable
internal fun WaitingWeek(week: PartnerWeek, showOnCreate: Boolean = false, onQrOpened: () -> Unit = {}) {
    val context = LocalContext.current
    Heading(stringResource(R.string.partner_pending_title))
    val link = rememberShareableInvite(week)
    if (link == null) {
        Notice(stringResource(R.string.partner_reshare_unavailable))
        return
    }
    Notice(stringResource(R.string.partner_waiting))
    var showQr by rememberSaveable(week.id, link) { mutableStateOf(false) }
    LaunchedEffect(showOnCreate, link) {
        if (showOnCreate) {
            showQr = true
            onQrOpened()
        }
    }
    PrimaryButton(stringResource(R.string.partner_share), {
        sharePartnerInvitation(context, link)
    }, Modifier.testTag("partner_share"))
    SecondaryButton(stringResource(R.string.partner_show_qr), { showQr = true }, Modifier.testTag("partner_show_qr"))
    if (showQr) PartnerQrDialog(link, onDismiss = { showQr = false })
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
        if (date != null) Notice(stringResource(R.string.partner_expires, localDate(date.toString()).orEmpty()))
    }
}

@Composable
private fun localDate(value: String?): String? {
    val locale = LocalConfiguration.current.locales[0]
    return value?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }.getOrNull() }
}

@Composable
private fun WeekProgress(week: PartnerWeek, state: PartnerUiState, vm: PartnerWeekViewModel) {
    WeekProgressContent(week, state, vm::checkIn, vm::cheer, vm::renew, { vm.pause(week.id) }, planning = {
        PartnerCheckInPlan(week = week, plannedDays = state.plannedDays, enabled = !state.busy,
            onSave = { vm.savePlannedDays(week.id, it) })
    }, reminder = {
        PartnerReminderRow(week = week, plannedDays = state.plannedDays)
    })
}

@Composable
internal fun WeekProgressContent(
    week: PartnerWeek, state: PartnerUiState, onCheckIn: () -> Unit, onCheer: (String) -> Unit,
    onRenew: () -> Unit, onPause: () -> Unit,
    planning: @Composable () -> Unit = {}, reminder: @Composable () -> Unit = {},
) {
    val finished = week.status == "finished"
    val supporter = week.self.role == "supporter"
    val other = week.partner
    val members = listOfNotNull(week.self, other).filter { it.role == "participant" }
    Heading(stringResource(if (finished) R.string.partner_recap else R.string.partner_week_heading))
    val start = localDate(week.startDate)
    val end = localDate(week.endDate)
    if (start != null && end != null) Notice(stringResource(R.string.partner_period, start, end))
    if (other != null) {
        when {
            supporter -> Notice(stringResource(R.string.partner_supporting, other.alias))
            other.role == "supporter" -> Notice(stringResource(R.string.partner_supported_by, other.alias))
        }
    }
    val status = when {
        state.pendingDays.isNotEmpty() -> R.string.partner_queued
        state.offline -> R.string.partner_offline
        else -> null
    }
    status?.let { Notice(stringResource(it), Modifier.testTag("partner_sync_status").semantics { liveRegion = LiveRegionMode.Polite }) }
    if (week.lastCheer != null && other != null) {
        Text(stringResource(R.string.partner_encouragement_from, other.alias, stringResource(reactionLabel(week.lastCheer))),
            Modifier.testTag("partner_received").semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleMedium)
    }

    val participating = !supporter && state.participantAllowed == true
    val full = week.self.days.distinct().size >= 3
    if (!finished && participating) {
        val checked = week.today in week.self.days
        val label = when {
            full -> R.string.partner_all_marks
            checked -> R.string.partner_checked
            else -> R.string.partner_checkin
        }
        PrimaryButton(stringResource(label), onCheckIn, Modifier.testTag("partner_checkin"),
            enabled = week.canCheckIn && !state.busy)
        if (!checked && !full) Notice(stringResource(R.string.partner_checkin_hint))
    }
    if (!finished && other != null && !participating) Encouragement(week, !state.busy, primary = true, onSend = onCheer)
    AppCard {
        members.forEachIndexed { index, member ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val self = member === week.self
            MarkRow(member, if (self) stringResource(R.string.partner_you, member.alias) else member.alias,
                isSelf = self, weekStart = if (self) week.startDate else null)
        }
    }
    // Optimistic marks count on this phone. The shared keepsake waits for both records to sync.
    if (other != null && members.isNotEmpty() && state.pendingDays.isEmpty() && members.all { it.days.distinct().size >= 3 }) {
        PartnerKeepsake(week.id, week.startDate, start, end)
    }
    if (finished) {
        RenewalActions(week, state, onRenew, onPause)
        return
    }
    if (other != null && participating) Encouragement(week, !state.busy, primary = false, onSend = onCheer)
    if (participating && !full) {
        planning()
        reminder()
    }
}

@Composable
private fun PartnerKeepsake(weekId: String, startDay: String?, start: String?, end: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.partner_week_complete)
    val note = stringResource(R.string.partner_keepsake_note)
    val period = if (start != null && end != null) stringResource(R.string.partner_period, start, end) else ""
    var export by rememberSaveable { mutableStateOf<String?>(null) }
    var feedback by rememberSaveable(weekId) { mutableStateOf<Int?>(null) }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
        val range = export
        export = null
        if (uri != null && range != null) scope.launch {
            feedback = try {
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("No output stream")
                    out.use { stream ->
                        val image = ShareCards.renderPartnerKeepsake(context.resources, range)
                        try {
                            if (!image.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw IOException("PNG encoding failed")
                        } finally {
                            image.recycle()
                        }
                    }
                }
                R.string.partner_keepsake_saved
            } catch (_: IOException) {
                R.string.partner_keepsake_failed
            } catch (_: SecurityException) {
                R.string.partner_keepsake_failed
            }
        }
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().testTag("partner_keepsake").padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(note, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (period.isNotBlank()) Text(period, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = {
                export = period
                try {
                    val day = startDay?.let { runCatching { LocalDate.parse(it).toString() }.getOrNull() }
                    destination.launch(listOfNotNull("Fasting-Shift", day).joinToString("-") + ".png")
                } catch (_: ActivityNotFoundException) {
                    export = null
                    feedback = R.string.partner_keepsake_failed
                }
            }, modifier = Modifier.testTag("partner_save_keepsake")) { Text(stringResource(R.string.partner_save_keepsake)) }
            feedback?.let { Text(stringResource(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }
}

@Composable
private fun RenewalActions(week: PartnerWeek, state: PartnerUiState, onRenew: () -> Unit, onPause: () -> Unit) {
    var confirmPause by rememberSaveable(week.id) { mutableStateOf(false) }
    val paused = state.pausedWeekId == week.id && !week.renewalRequestedByMe
    when {
        week.renewalRequestedByMe -> Notice(stringResource(R.string.partner_renew_wait), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        week.renewalRequestedByPartner -> Notice(stringResource(R.string.partner_renew_invited))
        week.canRenew && !paused -> Notice(stringResource(R.string.partner_renew_choice))
    }
    if (paused) Text(stringResource(R.string.partner_paused_note),
        Modifier.testTag("partner_paused").semantics { liveRegion = LiveRegionMode.Polite })
    if (week.canRenew && !week.renewalRequestedByMe) {
        PrimaryButton(stringResource(if (week.renewalRequestedByPartner) R.string.partner_renew_accept else R.string.partner_renew),
            onRenew, Modifier.testTag("partner_renew"), enabled = !state.busy)
    }
    if (!paused && (week.canRenew || week.renewalRequestedByMe)) {
        SecondaryButton(stringResource(R.string.partner_pause), {
            if (week.renewalRequestedByMe) confirmPause = true else onPause()
        }, Modifier.testTag("partner_pause"), enabled = !state.busy)
    }
    if (confirmPause) AlertDialog(onDismissRequest = { confirmPause = false },
        title = { Text(stringResource(R.string.partner_pause_question)) },
        text = { Text(stringResource(R.string.partner_withdraw_note)) },
        confirmButton = { TextButton(onClick = { confirmPause = false; onPause() }) { Text(stringResource(R.string.partner_pause)) } },
        dismissButton = { TextButton(onClick = { confirmPause = false }) { Text(stringResource(R.string.partner_back)) } })
}

private fun reactionLabel(kind: String) = when (kind) {
    "here" -> R.string.partner_reaction_here
    "thanks" -> R.string.partner_reaction_thanks
    else -> R.string.partner_reaction_glad
}

@Composable
private fun Encouragement(week: PartnerWeek, enabled: Boolean, primary: Boolean, onSend: (String) -> Unit) {
    var kind by rememberSaveable(week.id) { mutableStateOf("glad") }
    var choose by rememberSaveable(week.id) { mutableStateOf(false) }
    val sent = !week.canCheer && week.startDate?.let { week.today >= it } == true &&
        week.endDate?.let { week.today <= it } == true
    val label = if (sent) stringResource(R.string.partner_cheered)
        else stringResource(R.string.partner_send_preset, stringResource(reactionLabel(kind)))
    val modifier = Modifier.testTag("partner_cheer")
    val send = { onSend(kind) }
    if (primary) PrimaryButton(label, send, modifier, enabled && week.canCheer)
    else SecondaryButton(label, send, modifier, enabled && week.canCheer)
    if (week.canCheer) {
        TextButton(onClick = { choose = true }, enabled = enabled, modifier = Modifier.testTag("partner_choose_preset")) {
            Text(stringResource(R.string.partner_choose_preset))
        }
    }
    if (choose) AlertDialog(onDismissRequest = { choose = false },
        title = { Text(stringResource(R.string.partner_choose_preset)) },
        text = {
            Column(Modifier.selectableGroup()) {
                for (option in partnerReactions) {
                    Row(Modifier.fillMaxWidth().selectable(selected = kind == option, role = Role.RadioButton,
                        onClick = { kind = option; choose = false }).heightIn(min = 48.dp).padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == option, onClick = null)
                        Text(stringResource(reactionLabel(option)))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choose = false }) { Text(stringResource(R.string.partner_back)) } })
}

@Composable
private fun MarkRow(member: PartnerMember, name: String, isSelf: Boolean, weekStart: String?) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag(if (isSelf) "partner_self_marks" else "partner_other_marks").semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        val count = member.days.distinct().size.coerceIn(0, 3)
        val start = weekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (start != null) {
            BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                if (maxWidth >= 245.dp && LocalDensity.current.fontScale <= 1.3f) {
                    val locale = LocalConfiguration.current.locales[0]
                    val weekday = DateTimeFormatter.ofPattern("EEEEE", locale)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        repeat(7) { offset ->
                            val day = start.plusDays(offset.toLong())
                            val checked = day.toString() in member.days
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.format(weekday), style = MaterialTheme.typography.labelSmall)
                                val chip = Modifier.size(28.dp).border(1.dp, if (checked) colors.primary else colors.outline, CircleShape)
                                Box(if (checked) chip.background(colors.primary, CircleShape) else chip, contentAlignment = Alignment.Center) {
                                    Text(day.dayOfMonth.toString(), color = if (checked) colors.onPrimary else colors.onSurface,
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.partner_count, count), style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant)
        val dates = member.days.distinct().sorted().mapNotNull { localDate(it) }
        if (dates.isNotEmpty()) Notice(stringResource(R.string.partner_checked_dates, dates.joinToString(", ")))
    }
}

@Composable
private fun MarkDots(count: Int) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(3) { index ->
            val mark = Modifier.size(20.dp).border(1.5.dp, if (index < count) colors.primary else colors.outline, CircleShape)
            Box(if (index < count) mark.background(colors.primary, CircleShape) else mark)
        }
    }
}
