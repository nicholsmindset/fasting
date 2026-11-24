package com.fastingshift.domain.challenge

import com.fastingshift.domain.model.Challenge
import com.fastingshift.domain.model.ChallengeType
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.model.ProtocolType
import com.fastingshift.domain.protocol.Presets
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Base64

/*
 * Challenges without a server (moat B, "social without surveillance"). A challenge is only a
 * few parameters, shared as a link; everyone who joins tracks their own progress locally from
 * their own fasts. Nothing about anyone's progress ever leaves their device.
 */

/** Ready-made challenges offered on the "New challenge" screen. */
enum class ChallengeTemplate(val type: ChallengeType, val protocolId: String?, val lengthDays: Int) {
    /** 16:8 every day for a week. */
    DAILY_16_8_WEEK(ChallengeType.DAILY_PROTOCOL, Presets.W16_8.id, 7),

    /** 18:6 every day for two weeks. */
    DAILY_18_6_TWO_WEEKS(ChallengeType.DAILY_PROTOCOL, Presets.W18_6.id, 14),

    /** Monday and Thursday Sunnah fasts for four weeks. */
    MON_THU_FOUR_WEEKS(ChallengeType.SUNNAH_MON_THU, null, 28),

    /** The white days (13–15 of each Hijri month) for three months. */
    WHITE_DAYS_THREE_MONTHS(ChallengeType.AYYAMUL_BIDH, null, 90),
    ;

    fun create(startDate: LocalDate, joinedAt: Instant, name: String? = null): Challenge =
        Challenge(type = type, protocolId = protocolId, startDate = startDate, lengthDays = lengthDays, name = ChallengeNames.clean(name), joinedAt = joinedAt)
}

/** Where a challenge is relative to today. Leaving is tracked separately ([Challenge.leftAt]). */
enum class ChallengeStatus { UPCOMING, ACTIVE, FINISHED }

/** One day of a challenge, for the progress grid. */
enum class DayState {
    /** A qualifying fast started this day. */
    DONE,

    /** A required day that has passed without a qualifying fast. Shown neutrally, never shamed. */
    MISSED,

    /** A required day that is today (or has a fast in progress that hasn't qualified yet). */
    TODAY,

    /** A required day still ahead. */
    UPCOMING,

    /** Not a fasting day for this challenge. */
    REST,
}

data class ChallengeDay(val date: LocalDate, val required: Boolean, val state: DayState)

data class ChallengeProgress(
    val days: List<ChallengeDay>,
    val status: ChallengeStatus,
) {
    val requiredDays: Int get() = days.count { it.required }
    val doneDays: Int get() = days.count { it.state == DayState.DONE }

    /** 0..1 share of required days done. */
    val fraction: Float get() = if (requiredDays == 0) 0f else doneDays.toFloat() / requiredDays
}

object Challenges {
    /** A Sunnah fast runs dawn to sunset; anything shorter than this doesn't count as one. */
    val MIN_SUNNAH_FAST: Duration = Duration.ofHours(10)

    fun status(challenge: Challenge, today: LocalDate): ChallengeStatus = when {
        today.isBefore(challenge.startDate) -> ChallengeStatus.UPCOMING
        today.isAfter(challenge.endDate) -> ChallengeStatus.FINISHED
        else -> ChallengeStatus.ACTIVE
    }

    /** Whether [date] is a fasting day for [type]. [hijriOffsetDays] is the user's moon-sighting offset. */
    fun isRequired(type: ChallengeType, date: LocalDate, hijriOffsetDays: Int = 0): Boolean = when (type) {
        ChallengeType.DAILY_PROTOCOL -> true
        ChallengeType.SUNNAH_MON_THU ->
            (date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY) && !Hijri.isForbiddenFastDay(date, hijriOffsetDays)
        ChallengeType.AYYAMUL_BIDH ->
            Hijri.dayOfMonth(date, hijriOffsetDays) in 13..15 && !Hijri.isForbiddenFastDay(date, hijriOffsetDays)
    }

    /**
     * Whether [fast] meets [challenge]'s daily requirement. It counts for the local day it
     * STARTED on. A daily protocol needs at least the protocol's fasting hours (an active fast
     * qualifies once it gets there); a Sunnah day needs a dawn-to-sunset fast that was
     * completed as planned.
     */
    fun meets(challenge: Challenge, fast: Fast, now: Instant): Boolean = when (challenge.type) {
        ChallengeType.DAILY_PROTOCOL -> {
            val hours = challenge.protocolId?.let(Presets::byId)?.fastHours ?: return false
            val length = fast.actualDuration ?: Duration.between(fast.startAt, now)
            length >= Duration.ofHours(hours.toLong())
        }
        // A fast started as Sunnah, qada or Ramadan runs to maghrib, which can be under 10h in
        // winter or far from the equator: completing it is what counts. Other fasts need 10h.
        ChallengeType.SUNNAH_MON_THU, ChallengeType.AYYAMUL_BIDH ->
            fast.status == FastStatus.COMPLETED &&
                (fast.kind != FastKind.REGULAR || fast.plannedDuration >= MIN_SUNNAH_FAST)
    }

    fun progress(challenge: Challenge, fasts: List<Fast>, zone: ZoneId, now: Instant, hijriOffsetDays: Int = 0): ChallengeProgress {
        val today = now.atZone(zone).toLocalDate()
        val start = challenge.startDate
        val end = challenge.endDate
        val byDay = fasts.asSequence()
            .map { it to it.startAt.atZone(zone).toLocalDate() }
            .filter { (_, day) -> !day.isBefore(start) && !day.isAfter(end) }
            .groupBy({ it.second }, { it.first })
        val days = (0 until challenge.lengthDays).map { i ->
            val date = start.plusDays(i.toLong())
            val required = isRequired(challenge.type, date, hijriOffsetDays)
            val started = byDay[date].orEmpty()
            val state = when {
                !required -> DayState.REST
                started.any { meets(challenge, it, now) } -> DayState.DONE
                date.isAfter(today) -> DayState.UPCOMING
                date == today || started.any { it.isActive } -> DayState.TODAY
                else -> DayState.MISSED
            }
            ChallengeDay(date, required, state)
        }
        return ChallengeProgress(days, status(challenge, today))
    }
}

/** Optional challenge names: trimmed, single line, at most [MAX] characters. */
object ChallengeNames {
    const val MAX = 40

    fun clean(name: String?): String? =
        name?.let(::visibleOnly)?.trim()?.let { truncate(it) }?.takeIf { it.isNotEmpty() }

    private fun truncate(s: String): String =
        if (s.codePointCount(0, s.length) <= MAX) s else s.substring(0, s.offsetByCodePoints(0, MAX)).trim()

    fun isValid(name: String): Boolean = clean(name) == name

    /** Line/paragraph separators and invisible format characters (bidi overrides, zero-width). */
    private const val ZWJ = 0x200D
    private val HIDDEN_OR_BREAKING = setOf(Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.FORMAT)

    /** Drops control, line-breaking and invisible format characters, and unpaired surrogates. */
    private fun visibleOnly(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            val unpaired = n == 1 && Character.isSurrogate(s[i])
            // The zero-width joiner stays: emoji sequences need it.
            val hidden = cp != ZWJ && Character.getType(cp).toByte() in HIDDEN_OR_BREAKING
            if (!unpaired && !Character.isISOControl(cp) && !hidden) appendCodePoint(cp)
            i += n
        }
    }
}

/**
 * Tiny Hijri helper (Umm al-Qura via [HijrahChronology]) for challenge days. Kept local so
 * challenges don't depend on the Ramadan feature's calendar code.
 */
internal object Hijri {
    private const val SHAWWAL = 10
    private const val DHUL_HIJJAH = 12

    private fun hijri(date: LocalDate, offsetDays: Int): HijrahDate? =
        runCatching { HijrahChronology.INSTANCE.date(date.plusDays(offsetDays.toLong())) }.getOrNull()

    /** Day of the Hijri month, or 0 outside the supported calendar range. */
    fun dayOfMonth(date: LocalDate, offsetDays: Int = 0): Int = hijri(date, offsetDays)?.get(ChronoField.DAY_OF_MONTH) ?: 0

    /** The two Eids and the days of tashreeq (11–13 Dhul Hijjah), when fasting is not allowed. */
    fun isForbiddenFastDay(date: LocalDate, offsetDays: Int = 0): Boolean {
        val h = hijri(date, offsetDays) ?: return false
        val month = h.get(ChronoField.MONTH_OF_YEAR)
        val day = h.get(ChronoField.DAY_OF_MONTH)
        return (month == SHAWWAL && day == 1) || (month == DHUL_HIJJAH && day in 10..13)
    }
}

/** What a challenge link carries: only the challenge parameters, never anyone's progress. */
data class ChallengeInvite(
    val type: ChallengeType,
    val protocolId: String?,
    val startDate: LocalDate,
    val lengthDays: Int,
    val name: String?,
) {
    /** A dated observance has no fixed duration; the normal start-time gate still applies. */
    fun allowsEnrollment(maxDuration: Duration?): Boolean {
        if (maxDuration == null || !ChallengeLinks.validProtocol(type, protocolId.orEmpty())) return false
        if (type != ChallengeType.DAILY_PROTOCOL) return true
        val protocol = protocolId?.let(Presets::byId) ?: return false
        return Duration.ofHours(protocol.fastHours.toLong()) <= maxDuration
    }

    fun matchesJoined(challenge: Challenge): Boolean = challenge.leftAt == null && of(challenge) == this

    fun toChallenge(joinedAt: Instant): Challenge =
        Challenge(type = type, protocolId = protocolId, startDate = startDate, lengthDays = lengthDays, name = name, joinedAt = joinedAt)

    companion object {
        fun of(c: Challenge) = ChallengeInvite(c.type, c.protocolId, c.startDate, c.lengthDays, ChallengeNames.clean(c.name))
    }
}

sealed interface InviteResult {
    data class Valid(val invite: ChallengeInvite) : InviteResult

    /** Made by a newer app version: ask the user to update rather than calling it broken. */
    data object NewerVersion : InviteResult
    data object Invalid : InviteResult
}

/**
 * Invite links: `https://fastingshift.com/c/<code>`, where code is unpadded base64url of
 * `version|type|protocol|yyyyMMdd|days|name`. Decoding is strict: anything malformed,
 * out of range or not in canonical form is rejected, so a link can never smuggle in values
 * the app wouldn't create itself.
 */
object ChallengeLinks {
    const val HOST = "fastingshift.com"
    const val PILOT_HOST = "nicholsmindset.github.io"
    const val PILOT_PATH = "/fasting/c/"
    const val PATH_PREFIX = "/c/"
    const val VERSION = 1
    const val MAX_CODE_LENGTH = 300
    private val CODE = Regex("[A-Za-z0-9_-]{1,$MAX_CODE_LENGTH}")
    private const val SEP = '|'
    private val MIN_START: LocalDate = LocalDate.of(2020, 1, 1)
    private val MAX_START: LocalDate = LocalDate.of(2100, 12, 31)
    private val DATE = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT)

    /** The longest daily fast a shared challenge may ask for (no extended fasts by link). */
    const val MAX_DAILY_FAST_HOURS = 20

    private val typeCodes = mapOf(
        ChallengeType.DAILY_PROTOCOL to "D",
        ChallengeType.SUNNAH_MON_THU to "M",
        ChallengeType.AYYAMUL_BIDH to "W",
    )

    fun link(invite: ChallengeInvite, pilot: Boolean = false): String {
        val code = encode(invite)
        // The fragment stays in the browser when the pilot page is requested.
        return if (pilot) "https://$PILOT_HOST$PILOT_PATH#$code" else "https://$HOST$PATH_PREFIX$code"
    }

    fun encode(invite: ChallengeInvite): String {
        val payload = listOf(
            VERSION.toString(),
            typeCodes.getValue(invite.type),
            invite.protocolId.orEmpty(),
            DATE.format(invite.startDate),
            invite.lengthDays.toString(),
            invite.name.orEmpty(),
        ).joinToString(SEP.toString())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    /** Whether [code] is shaped like a code at all (safe to put in a route). */
    fun looksLikeCode(code: String): Boolean = CODE.matches(code)

    /** Parses a full link (or a bare code pasted in the app). */
    fun parse(text: String): InviteResult = codeFrom(text)?.let(::decode) ?: InviteResult.Invalid

    /** Accepts bare codes, branded path links and fragment links on the exact pilot host. */
    fun codeFrom(text: String): String? {
        val t = text.trim()
        if (looksLikeCode(t)) return t
        val uri = runCatching { URI(t) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.rawUserInfo != null || uri.rawQuery != null) return null
        if (host == PILOT_HOST) {
            if (!uri.rawAuthority.equals(PILOT_HOST, ignoreCase = true) || uri.rawPath != PILOT_PATH) return null
            return uri.rawFragment?.takeIf(::looksLikeCode)
        }
        if (host != HOST && host != "www.$HOST") return null
        if (uri.rawFragment != null || (uri.port != -1 && uri.port != 443)) return null
        val path = uri.rawPath ?: return null
        if (!path.startsWith(PATH_PREFIX)) return null
        return path.removePrefix(PATH_PREFIX).removeSuffix("/").takeIf(::looksLikeCode)
    }

    fun decode(code: String): InviteResult {
        if (!looksLikeCode(code)) return InviteResult.Invalid
        val bytes = runCatching { Base64.getUrlDecoder().decode(code) }.getOrNull() ?: return InviteResult.Invalid
        val text = strictUtf8(bytes) ?: return InviteResult.Invalid
        val version = text.substringBefore(SEP)
        if (version.isEmpty() || version.length > 3 || !version.all { it in '0'..'9' }) return InviteResult.Invalid
        if (version.toInt() > VERSION) return InviteResult.NewerVersion
        if (version != VERSION.toString()) return InviteResult.Invalid
        val parts = text.split(SEP, limit = 6)
        if (parts.size != 6) return InviteResult.Invalid
        val (_, typeCode, protocolId, date, days) = parts
        val name = parts[5]
        val type = typeCodes.entries.firstOrNull { it.value == typeCode }?.key ?: return InviteResult.Invalid
        if (!validProtocol(type, protocolId)) return InviteResult.Invalid
        if (date.length != 8) return InviteResult.Invalid
        val start = runCatching { LocalDate.parse(date, DATE) }.getOrNull() ?: return InviteResult.Invalid
        if (start.isBefore(MIN_START) || start.isAfter(MAX_START)) return InviteResult.Invalid
        if (days.isEmpty() || days.length > 3 || !days.all { it in '0'..'9' } || days.startsWith('0')) return InviteResult.Invalid
        val length = days.toInt()
        if (length !in 1..366) return InviteResult.Invalid
        if (name.isNotEmpty() && !ChallengeNames.isValid(name)) return InviteResult.Invalid
        val invite = ChallengeInvite(type, protocolId.ifEmpty { null }, start, length, name.ifEmpty { null })
        // Canonical form only: the same parameters always give the same code.
        return if (encode(invite) == code) InviteResult.Valid(invite) else InviteResult.Invalid
    }

    internal fun validProtocol(type: ChallengeType, id: String): Boolean = when (type) {
        ChallengeType.DAILY_PROTOCOL -> {
            val p = Presets.byId(id)
            p != null && !p.isCustom && p.type == ProtocolType.WINDOW && p.fastHours <= MAX_DAILY_FAST_HOURS
        }
        else -> id.isEmpty()
    }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        null
    }
}
