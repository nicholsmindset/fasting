package com.fastingshift.app.ui.partner

import java.time.LocalDate
import org.json.JSONObject

data class PartnerMember(val alias: String, val role: String, val days: List<String>)

data class PartnerWeek(
    val id: String,
    val status: String,
    val self: PartnerMember,
    val partner: PartnerMember?,
    val startDate: String?,
    val endDate: String?,
    val inviteCode: String?,
    val lastCheer: String?,
    val canCheckIn: Boolean,
    val canCheer: Boolean,
    val canRenew: Boolean,
    val renewalRequestedByMe: Boolean,
    val renewalRequestedByPartner: Boolean,
    val inviteExpiresAt: String?,
    val today: String,
)

data class PartnerInvitePreview(val hostAlias: String, val code: String, val expiresAt: String? = null)

data class PartnerUiState(
    val week: PartnerWeek? = null,
    val preview: PartnerInvitePreview? = null,
    val busy: Boolean = false,
    val offline: Boolean = false,
    val pendingDays: Set<String> = emptySet(),
    val message: String? = null,
    val participantAllowed: Boolean? = null,
    val leaving: Boolean = false,
)

internal fun JSONObject.optionalString(name: String): String? =
    if (isNull(name)) null else getString(name)

internal val partnerReactions = mapOf(
    "glad" to "Glad you’re here.",
    "here" to "I’m here with you.",
    "thanks" to "Thanks for checking in.",
)

private fun JSONObject.member(pendingDays: Set<String> = emptySet()): PartnerMember {
    val days = getJSONArray("days")
    return PartnerMember(
        getString("alias"), getString("role"),
        (List(days.length()) { days.getString(it) } + pendingDays).distinct().sorted(),
    )
}

internal fun projectPartnerWeek(
    data: JSONObject,
    today: LocalDate,
    invite: String?,
    pendingDays: Set<String>,
): PartnerWeek {
    val me = data.getString("self")
    val other = if (me == "host") "guest" else "host"
    val own = data.getJSONObject(me)
    val otherMember = data.optJSONObject(other)
    val start = data.optionalString("startDay")
    val end = data.optionalString("endDay")
    val date = today.toString()
    val status = data.getString("status").let {
        if (it == "active" && end != null && date > end) "finished" else it
    }
    val self = own.member(pendingDays)
    val cheers = data.getJSONArray("cheers")
    val reactions = List(cheers.length()) { cheers.getJSONObject(it) }
    val sentToday = reactions.any { it.getString("from") == me && it.getString("day") == date }
    val incoming = reactions.lastOrNull { it.getString("from") == other }
    val active = status == "active" && start != null && end != null && date in start..end
    val voted = own.optBoolean("renewed")
    return PartnerWeek(
        data.getString("id"), status, self, otherMember?.member(), start, end,
        invite.takeIf { status == "pending" && me == "host" },
        incoming?.getString("kind")?.let(partnerReactions::get),
        active && self.role == "participant" && date !in self.days && self.days.size < 3,
        active && otherMember != null && !sentToday,
        status == "finished" && !voted, voted, otherMember?.optBoolean("renewed") == true,
        data.optionalString("expiresAt"), date,
    )
}
