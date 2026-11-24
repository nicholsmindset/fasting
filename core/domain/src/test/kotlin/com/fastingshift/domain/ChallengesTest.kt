package com.fastingshift.domain

import com.fastingshift.domain.challenge.ChallengeInvite
import com.fastingshift.domain.challenge.ChallengeLinks
import com.fastingshift.domain.challenge.ChallengeNames
import com.fastingshift.domain.challenge.ChallengeStatus
import com.fastingshift.domain.challenge.ChallengeTemplate
import com.fastingshift.domain.challenge.Challenges
import com.fastingshift.domain.challenge.DayState
import com.fastingshift.domain.challenge.InviteResult
import com.fastingshift.domain.model.Challenge
import com.fastingshift.domain.model.ChallengeType
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.schedule.DayOverride
import com.fastingshift.domain.schedule.PlanHistory
import com.fastingshift.domain.schedule.WeekChoice
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChallengesTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val joined = Instant.parse("2026-11-20T00:00:00Z")
    private val monday = LocalDate.of(2026, 11, 23) // also 13 Jumada al-Akhirah 1448

    @Test fun enrollmentRespectsTheScreenedLimitAndSupportedDailyProtocols() {
        val daily = ChallengeInvite(ChallengeType.DAILY_PROTOCOL, "window_16_8", monday, 7, null)
        val sunnah = ChallengeInvite(ChallengeType.SUNNAH_MON_THU, null, monday, 28, null)
        assertFalse(daily.allowsEnrollment(null))
        assertFalse(sunnah.allowsEnrollment(null))
        assertFalse(daily.allowsEnrollment(Duration.ofHours(15)))
        assertTrue(daily.allowsEnrollment(Duration.ofHours(16)))
        assertTrue(sunnah.allowsEnrollment(Duration.ofHours(24)))
        assertFalse(daily.copy(protocolId = "duration_36").allowsEnrollment(Duration.ofHours(120)))
        assertFalse(daily.copy(protocolId = "unknown").allowsEnrollment(Duration.ofHours(120)))
    }

    private fun at(day: LocalDate, time: LocalTime) = day.atTime(time).atZone(zone).toInstant()

    /** A fast starting at [startTime] on [day], planned for [planned] hours and ended after [actual] hours. */
    private fun fast(day: LocalDate, planned: Long, actual: Long? = planned, startTime: LocalTime = LocalTime.of(20, 0), kind: FastKind = FastKind.REGULAR): Fast {
        val start = at(day, startTime)
        val f = Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(planned)), protocolId = "window_16_8", kind = kind)
        return if (actual == null) f else f.end(start.plus(Duration.ofHours(actual)))
    }

    private fun daily(start: LocalDate = monday) = ChallengeTemplate.DAILY_16_8_WEEK.create(start, joined)

    @Test fun personalProgressUsesOnlyTheParticipantsPlannedDaysAndFinishedFasts() {
        val shared = daily()
        val personal = shared.copy(followsOwnPlan = true)
        val plan = PlanHistory(
            revisions = mapOf(monday to 0b0010101), // Mon, Wed, Fri
            dayOverrides = mapOf(monday.plusDays(2) to DayOverride()),
        )
        val fasts = listOf(
            fast(monday, 16, actual = 4), fast(monday, 16, actual = 2, startTime = LocalTime.MIDNIGHT),
            fast(monday.plusDays(1), 16), fast(monday.plusDays(2), 16), fast(monday.plusDays(4), 16, actual = null),
        )
        val now = at(monday.plusDays(5), LocalTime.NOON)
        val progress = Challenges.progress(personal, fasts, zone, now, personalPlan = plan)
        assertEquals(2, progress.requiredDays)
        assertEquals(1, progress.doneDays)
        assertEquals(DayState.DONE, progress.days[0].state)
        assertEquals(DayState.REST, progress.days[2].state)
        assertEquals(DayState.TODAY, progress.days[4].state)
        assertEquals(0, Challenges.progress(personal, fasts, zone, now).requiredDays)
        val weekOff = plan.copy(choices = mapOf(monday to WeekChoice.BREAK))
        assertEquals(0, Challenges.progress(personal, fasts, zone, now, personalPlan = weekOff).requiredDays)
        assertEquals(7, Challenges.progress(shared, fasts, zone, now, personalPlan = plan).requiredDays)
        assertEquals(ChallengeLinks.link(ChallengeInvite.of(shared)), ChallengeLinks.link(ChallengeInvite.of(personal)))
    }

    // ---------- templates ----------

    @Test fun `templates have the documented shapes`() {
        assertEquals(7, ChallengeTemplate.DAILY_16_8_WEEK.lengthDays)
        assertEquals("window_16_8", ChallengeTemplate.DAILY_16_8_WEEK.protocolId)
        assertEquals(14, ChallengeTemplate.DAILY_18_6_TWO_WEEKS.lengthDays)
        assertEquals("window_18_6", ChallengeTemplate.DAILY_18_6_TWO_WEEKS.protocolId)
        assertEquals(28, ChallengeTemplate.MON_THU_FOUR_WEEKS.lengthDays)
        assertEquals(ChallengeType.SUNNAH_MON_THU, ChallengeTemplate.MON_THU_FOUR_WEEKS.type)
        assertEquals(90, ChallengeTemplate.WHITE_DAYS_THREE_MONTHS.lengthDays)
        assertNull(ChallengeTemplate.WHITE_DAYS_THREE_MONTHS.protocolId)
    }

    @Test fun `every template round-trips through a link`() {
        ChallengeTemplate.entries.forEach { t ->
            val c = t.create(monday, joined, "Family")
            val r = ChallengeLinks.parse(ChallengeLinks.link(ChallengeInvite.of(c)))
            assertEquals(InviteResult.Valid(ChallengeInvite.of(c)), r)
        }
    }

    // ---------- status ----------

    @Test fun `status is upcoming, active, then finished`() {
        val c = daily()
        assertEquals(ChallengeStatus.UPCOMING, Challenges.status(c, monday.minusDays(1)))
        assertEquals(ChallengeStatus.ACTIVE, Challenges.status(c, monday))
        assertEquals(ChallengeStatus.ACTIVE, Challenges.status(c, monday.plusDays(6)))
        assertEquals(ChallengeStatus.FINISHED, Challenges.status(c, monday.plusDays(7)))
    }

    // ---------- daily protocol ----------

    @Test fun `a daily day counts for the day the fast started, even if it ended the next day`() {
        val c = daily()
        val now = at(monday.plusDays(3), LocalTime.NOON)
        val p = Challenges.progress(c, listOf(fast(monday, 16)), zone, now)
        assertEquals(DayState.DONE, p.days[0].state)
        assertEquals(DayState.MISSED, p.days[1].state) // the fast ended on Tuesday but started Monday
        assertEquals(1, p.doneDays)
        assertEquals(7, p.requiredDays)
    }

    @Test fun `a daily fast shorter than the protocol does not count`() {
        val c = daily()
        val now = at(monday.plusDays(1), LocalTime.NOON)
        val p = Challenges.progress(c, listOf(fast(monday, 16, actual = 15)), zone, now)
        assertEquals(DayState.MISSED, p.days[0].state)
    }

    @Test fun `a longer fast also meets the daily protocol`() {
        val c = ChallengeTemplate.DAILY_18_6_TWO_WEEKS.create(monday, joined)
        val now = at(monday.plusDays(2), LocalTime.NOON)
        val p = Challenges.progress(c, listOf(fast(monday, 18), fast(monday.plusDays(1), 16)), zone, now)
        assertEquals(listOf(DayState.DONE, DayState.MISSED, DayState.TODAY), p.days.take(3).map { it.state })
    }

    @Test fun `an active fast counts once it reaches the hours, and shows as today before that`() {
        val c = daily()
        val active = fast(monday, 16, actual = null)
        val early = Challenges.progress(c, listOf(active), zone, active.startAt.plus(Duration.ofHours(10)))
        assertEquals(DayState.TODAY, early.days[0].state) // started Monday 20:00, now Tuesday 06:00
        val later = Challenges.progress(c, listOf(active), zone, active.startAt.plus(Duration.ofHours(16)))
        assertEquals(DayState.DONE, later.days[0].state)
    }

    @Test fun `fasts outside the challenge are ignored and future days are upcoming`() {
        val c = daily()
        val now = at(monday, LocalTime.NOON)
        val p = Challenges.progress(c, listOf(fast(monday.minusDays(1), 16), fast(monday.plusDays(7), 16)), zone, now)
        assertEquals(0, p.doneDays)
        assertEquals(DayState.TODAY, p.days[0].state)
        assertTrue(p.days.drop(1).all { it.state == DayState.UPCOMING })
        assertEquals(ChallengeStatus.ACTIVE, p.status)
    }

    @Test fun `the local day uses the user's zone`() {
        val c = daily()
        // 23:30 Monday in Singapore is 15:30 UTC Monday; 00:30 Tuesday in Singapore is still Monday UTC.
        val tuesdayEarly = fast(monday.plusDays(1), 16, startTime = LocalTime.of(0, 30))
        val p = Challenges.progress(c, listOf(tuesdayEarly), zone, at(monday.plusDays(3), LocalTime.NOON))
        assertEquals(DayState.MISSED, p.days[0].state)
        assertEquals(DayState.DONE, p.days[1].state)
    }

    @Test fun `fraction of required days`() {
        val c = daily()
        val now = at(monday.plusDays(7), LocalTime.NOON)
        val p = Challenges.progress(c, (0L..6L).filter { it % 2 == 0L }.map { fast(monday.plusDays(it), 16) }, zone, now)
        assertEquals(4, p.doneDays)
        assertEquals(4f / 7f, p.fraction)
        assertEquals(ChallengeStatus.FINISHED, p.status)
    }

    // ---------- Sunnah ----------

    @Test fun `monday and thursday are the required days`() {
        val c = ChallengeTemplate.MON_THU_FOUR_WEEKS.create(monday, joined)
        val p = Challenges.progress(c, emptyList(), zone, at(monday.minusDays(1), LocalTime.NOON))
        val required = p.days.filter { it.required }.map { it.date.dayOfWeek }.toSet()
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), required)
        assertEquals(8, p.requiredDays)
        assertEquals(ChallengeStatus.UPCOMING, p.status)
        assertTrue(p.days.filter { !it.required }.all { it.state == DayState.REST })
    }

    @Test fun `a sunnah day needs a completed dawn-to-sunset fast`() {
        val c = ChallengeTemplate.MON_THU_FOUR_WEEKS.create(monday, joined)
        val now = at(monday.plusDays(4), LocalTime.of(21, 0))
        val fasts = listOf(
            fast(monday, 13, startTime = LocalTime.of(5, 30), kind = FastKind.SUNNAH),
            fast(monday.plusDays(3), 13, actual = 9, startTime = LocalTime.of(5, 30), kind = FastKind.SUNNAH), // broken early
        )
        val p = Challenges.progress(c, fasts, zone, now)
        assertEquals(DayState.DONE, p.days[0].state)
        assertEquals(DayState.MISSED, p.days[3].state)
        // A short completed fast is not a Sunnah fast.
        assertFalse(Challenges.meets(c, fast(monday, 4), now))
        // A regular completed long fast on a Monday does count.
        assertTrue(Challenges.meets(c, fast(monday, 16), now))
    }

    @Test fun `a completed winter sunnah fast to maghrib counts even under 10 hours`() {
        val c = ChallengeTemplate.MON_THU_FOUR_WEEKS.create(monday, joined)
        val now = at(monday, LocalTime.of(21, 0))
        // Started from the hub at 8:00, ending at a 17:35 maghrib: 9h35m.
        assertTrue(Challenges.meets(c, fast(monday, 9, startTime = LocalTime.of(8, 0), kind = FastKind.SUNNAH), now))
        assertTrue(Challenges.meets(c, fast(monday, 8, startTime = LocalTime.of(8, 0), kind = FastKind.QADA), now))
        // A regular fast still needs 10 hours, and a broken one never counts.
        assertFalse(Challenges.meets(c, fast(monday, 9, startTime = LocalTime.of(8, 0)), now))
        assertFalse(Challenges.meets(c, fast(monday, 9, actual = 5, startTime = LocalTime.of(8, 0), kind = FastKind.SUNNAH), now))
    }

    @Test fun `names drop line breaks, invisible format characters and half emoji`() {
        assertEquals("Family fasts", ChallengeNames.clean("Family\u2028 fasts"))
        assertEquals("abc", ChallengeNames.clean("a\u202Eb\u200Bc"))
        assertFalse(ChallengeNames.isValid("evil\u2066name"))
        // A cut emoji (lone high surrogate) is dropped; a whole emoji and a ZWJ sequence stay.
        assertEquals("hi", ChallengeNames.clean("hi\uD83D"))
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69"
        assertEquals(family, ChallengeNames.clean(family))
    }

    @Test fun `white days follow the Hijri calendar`() {
        // 23–25 Nov 2026 are 13–15 Jumada al-Akhirah 1448; 22–24 Dec 2026 are 13–15 Rajab.
        val c = ChallengeTemplate.WHITE_DAYS_THREE_MONTHS.create(monday.minusDays(2), joined)
        val p = Challenges.progress(c, emptyList(), zone, at(monday.minusDays(3), LocalTime.NOON))
        val required = p.days.filter { it.required }.map { it.date }
        assertEquals(LocalDate.of(2026, 11, 23), required.first())
        assertTrue(LocalDate.of(2026, 12, 22) in required)
        assertTrue(LocalDate.of(2026, 12, 24) in required)
        assertFalse(LocalDate.of(2026, 12, 25) in required)
        assertEquals(9, p.requiredDays) // three months × three days
    }

    @Test fun `the moon-sighting offset shifts white days`() {
        assertTrue(Challenges.isRequired(ChallengeType.AYYAMUL_BIDH, LocalDate.of(2026, 11, 23)))
        assertFalse(Challenges.isRequired(ChallengeType.AYYAMUL_BIDH, LocalDate.of(2026, 11, 23), hijriOffsetDays = -1))
        assertTrue(Challenges.isRequired(ChallengeType.AYYAMUL_BIDH, LocalDate.of(2026, 11, 26), hijriOffsetDays = -1))
    }

    @Test fun `no fasting days on Eid or the days of tashreeq`() {
        // 17 May 2027 is a Monday and 11 Dhul Hijjah 1448 (tashreeq).
        assertFalse(Challenges.isRequired(ChallengeType.SUNNAH_MON_THU, LocalDate.of(2027, 5, 17)))
        // 13 Dhul Hijjah is a white day but also tashreeq: skipped. 14 and 15 remain.
        assertFalse(Challenges.isRequired(ChallengeType.AYYAMUL_BIDH, LocalDate.of(2027, 5, 19)))
        assertTrue(Challenges.isRequired(ChallengeType.AYYAMUL_BIDH, LocalDate.of(2027, 5, 20)))
        // The Thursday after is a normal Sunnah day.
        assertTrue(Challenges.isRequired(ChallengeType.SUNNAH_MON_THU, LocalDate.of(2027, 5, 20)))
    }

    @Test fun `dates outside the Hijri calendar range are never white days`() {
        assertFalse(Challenges.isRequired(ChallengeType.AYYAMUL_BIDH, LocalDate.of(1700, 1, 1)))
    }

    // ---------- invite links ----------

    private val invite = ChallengeInvite(ChallengeType.DAILY_PROTOCOL, "window_16_8", monday, 7, "Office 16:8 | week")

    private fun code(payload: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())

    @Test fun `link format and round trip`() {
        val link = ChallengeLinks.link(invite)
        assertTrue(link.startsWith("https://fastingshift.com/c/"))
        assertFalse(link.contains('='))
        assertEquals(InviteResult.Valid(invite), ChallengeLinks.parse(link))
        assertEquals(InviteResult.Valid(invite), ChallengeLinks.parse("  $link/  ".trim()))
        assertEquals(InviteResult.Valid(invite), ChallengeLinks.parse(link.replace("https://", "https://www.")))
    }

    @Test fun `pilot links carry only the code in the fragment and retain version handling`() {
        val link = ChallengeLinks.link(invite, pilot = true)
        val encoded = ChallengeLinks.encode(invite)
        assertEquals("https://nicholsmindset.github.io/fasting/c/#$encoded", link)
        assertEquals(encoded, ChallengeLinks.codeFrom("  $link  "))
        assertEquals(InviteResult.Valid(invite), ChallengeLinks.parse(link))
        assertIs<InviteResult.NewerVersion>(ChallengeLinks.parse(link.substringBefore('#') + "#" + code("2|D|window_16_8|20261123|7|")))
    }

    @Test fun `pilot and branded URL components cannot be mixed`() {
        val c = ChallengeLinks.encode(invite)
        val pilot = "https://${ChallengeLinks.PILOT_HOST}/fasting/c/"
        listOf(
            pilot, "$pilot#", "$pilot$c", "$pilot$c#$c", "$pilot?invite=$c#$c",
            "$pilot#%41$c", "$pilot#$c/", "$pilot#$c#$c",
            "https://${ChallengeLinks.PILOT_HOST}/fasting/c#$c",
            "https://${ChallengeLinks.PILOT_HOST}/c/#$c", "https://${ChallengeLinks.PILOT_HOST}/other/c/#$c",
            "https://www.${ChallengeLinks.PILOT_HOST}/fasting/c/#$c",
            "https://${ChallengeLinks.PILOT_HOST}.example.org/fasting/c/#$c",
            "https://user@${ChallengeLinks.PILOT_HOST}/fasting/c/#$c",
            "https://${ChallengeLinks.PILOT_HOST}:8080/fasting/c/#$c",
            "http://${ChallengeLinks.PILOT_HOST}/fasting/c/#$c",
            "https://fastingshift.com/c/#$c", "https://fastingshift.com/c/$c#$c",
            "https://fastingshift.com/c/$c?invite=$c",
        ).forEach { assertEquals(InviteResult.Invalid, ChallengeLinks.parse(it), it) }
    }

    @Test fun `bare codes work for in-app paste`() {
        assertEquals(InviteResult.Valid(invite), ChallengeLinks.parse(ChallengeLinks.encode(invite)))
    }

    @Test fun `unicode names survive`() {
        val i = invite.copy(name = "Puasa keluarga 家庭 🌙")
        assertEquals(InviteResult.Valid(i), ChallengeLinks.parse(ChallengeLinks.link(i)))
    }

    @Test fun `no name and sunnah invites`() {
        val i = ChallengeInvite(ChallengeType.AYYAMUL_BIDH, null, monday, 90, null)
        assertEquals(InviteResult.Valid(i), ChallengeLinks.decode(ChallengeLinks.encode(i)))
        assertEquals(code("1|W||20261123|90|"), ChallengeLinks.encode(i))
    }

    @Test fun `a newer version asks for an update`() {
        assertIs<InviteResult.NewerVersion>(ChallengeLinks.decode(code("2|D|window_16_8|20261123|7|")))
    }

    @Test fun `wrong hosts, schemes and paths are rejected`() {
        val c = ChallengeLinks.encode(invite)
        listOf(
            "http://fastingshift.com/c/$c",
            "https://evil.com/c/$c",
            "https://fastingshift.com.evil.com/c/$c",
            "https://user@fastingshift.com/c/$c",
            "https://fastingshift.com:8443/c/$c",
            "https://fastingshift.com/x/$c",
            "https://fastingshift.com/c/",
            "https://fastingshift.com/c/$c/extra",
            "",
            "not a link",
        ).forEach { assertEquals(InviteResult.Invalid, ChallengeLinks.parse(it), it) }
    }

    @Test fun `malformed payloads are rejected`() {
        listOf(
            "0|D|window_16_8|20261123|7|", // version 0
            "01|D|window_16_8|20261123|7|", // non-canonical version
            "x|D|window_16_8|20261123|7|",
            "1|D|window_16_8|20261123|7", // missing field
            "1|X|window_16_8|20261123|7|", // unknown type
            "1|D||20261123|7|", // daily without protocol
            "1|M|window_16_8|20261123|28|", // sunnah with protocol
            "1|D|duration_72|20261123|7|", // extended fasts can't be shared as a daily challenge
            "1|D|omad|20261123|7|", // 23h daily is past the limit
            "1|D|custom_20|20261123|7|",
            "1|D|nope|20261123|7|",
            "1|D|window_16_8|20261131|7|", // no such date
            "1|D|window_16_8|2026-11-23|7|",
            "1|D|window_16_8|19991231|7|", // out of range
            "1|D|window_16_8|21010101|7|",
            "1|D|window_16_8|20261123|0|",
            "1|D|window_16_8|20261123|367|",
            "1|D|window_16_8|20261123|9999999999|",
            "1|D|window_16_8|20261123|-7|",
            "1|D|window_16_8|20261123|07|", // leading zero
            "1|D|window_16_8|20261123|+7|",
            "1|D|window_16_8|20261123|7| padded ",
            "1|D|window_16_8|20261123|7|line\nbreak",
            "1|D|window_16_8|20261123|7|" + "x".repeat(ChallengeNames.MAX + 1),
        ).forEach { assertEquals(InviteResult.Invalid, ChallengeLinks.decode(code(it)), it) }
    }

    @Test fun `malformed codes are rejected`() {
        val good = ChallengeLinks.encode(invite)
        assertEquals(InviteResult.Invalid, ChallengeLinks.decode("$good=")) // padding is not canonical
        assertEquals(InviteResult.Invalid, ChallengeLinks.decode(good.dropLast(1) + "/"))
        assertEquals(InviteResult.Invalid, ChallengeLinks.decode("a")) // not valid base64
        assertEquals(InviteResult.Invalid, ChallengeLinks.decode("A".repeat(ChallengeLinks.MAX_CODE_LENGTH + 4)))
        assertEquals(InviteResult.Invalid, ChallengeLinks.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(0xC3.toByte(), 0x28)))) // bad UTF-8
        assertEquals(InviteResult.Invalid, ChallengeLinks.decode(""))
    }

    @Test fun `a non-canonical base64 tail is rejected`() {
        // Flip the unused low bits of the last character: decodes to the same bytes, different code.
        val good = ChallengeLinks.encode(invite.copy(name = "ab"))
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val last = alphabet.indexOf(good.last())
        val tweaked = good.dropLast(1) + alphabet[last or 1]
        if (tweaked != good) assertEquals(InviteResult.Invalid, ChallengeLinks.decode(tweaked))
    }

    @Test fun `names are cleaned`() {
        assertEquals("Family", ChallengeNames.clean("  Family\n"))
        assertNull(ChallengeNames.clean("   "))
        assertEquals(ChallengeNames.MAX, ChallengeNames.clean("y".repeat(100))!!.length)
    }

    @Test fun `an invite becomes a challenge`() {
        val c: Challenge = invite.toChallenge(joined)
        assertEquals(monday.plusDays(6), c.endDate)
        assertEquals("window_16_8", c.protocolId)
    }
}
