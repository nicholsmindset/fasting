package com.fastingshift.app.ui.ramadan

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fastingshift.app.R
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.ramadan.HijriDate
import com.fastingshift.domain.ramadan.HijriMonth
import com.fastingshift.domain.ramadan.Sunnah
import com.fastingshift.domain.ramadan.SunnahType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

/** String resources for the Muslim fasting screens and the Sunnah reminder. */
object MuslimText {
    @StringRes
    fun monthName(month: HijriMonth): Int = when (month) {
        HijriMonth.MUHARRAM -> R.string.hijri_month_1
        HijriMonth.SAFAR -> R.string.hijri_month_2
        HijriMonth.RABI_AL_AWWAL -> R.string.hijri_month_3
        HijriMonth.RABI_AL_THANI -> R.string.hijri_month_4
        HijriMonth.JUMADA_AL_ULA -> R.string.hijri_month_5
        HijriMonth.JUMADA_AL_AKHIRAH -> R.string.hijri_month_6
        HijriMonth.RAJAB -> R.string.hijri_month_7
        HijriMonth.SHABAN -> R.string.hijri_month_8
        HijriMonth.RAMADAN -> R.string.hijri_month_9
        HijriMonth.SHAWWAL -> R.string.hijri_month_10
        HijriMonth.DHUL_QADAH -> R.string.hijri_month_11
        HijriMonth.DHUL_HIJJAH -> R.string.hijri_month_12
    }

    @StringRes
    fun typeName(type: SunnahType): Int = when (type) {
        SunnahType.MON_THU -> R.string.sunnah_type_mon_thu
        SunnahType.AYYAMUL_BIDH -> R.string.sunnah_type_white_days
        SunnahType.ARAFAH -> R.string.sunnah_type_arafah
        SunnahType.ASHURA -> R.string.sunnah_type_ashura
        SunnahType.SHAWWAL -> R.string.sunnah_type_shawwal
    }

    @StringRes
    fun typeDescription(type: SunnahType): Int = when (type) {
        SunnahType.MON_THU -> R.string.sunnah_type_mon_thu_desc
        SunnahType.AYYAMUL_BIDH -> R.string.sunnah_type_white_days_desc
        SunnahType.ARAFAH -> R.string.sunnah_type_arafah_desc
        SunnahType.ASHURA -> R.string.sunnah_type_ashura_desc
        SunnahType.SHAWWAL -> R.string.sunnah_type_shawwal_desc
    }

    /** Body of the evening-before reminder, specific to the day's main Sunnah type. */
    @StringRes
    fun reminderBody(type: SunnahType?): Int = when (type) {
        SunnahType.MON_THU -> R.string.notif_sunnah_body_mon_thu
        SunnahType.AYYAMUL_BIDH -> R.string.notif_sunnah_body_white_days
        SunnahType.ARAFAH -> R.string.notif_sunnah_body_arafah
        SunnahType.ASHURA -> R.string.notif_sunnah_body_ashura
        SunnahType.SHAWWAL -> R.string.notif_sunnah_body_shawwal
        null -> R.string.notif_sunnah_body
    }

    fun reminderBody(reminder: com.fastingshift.domain.reminders.Reminder): Int = reminderBody(Sunnah.typeOf(reminder))

    /** Chip label for a non-regular fast in History; null for regular fasts. */
    @StringRes
    fun kindLabel(kind: FastKind): Int? = when (kind) {
        FastKind.REGULAR -> null
        FastKind.RAMADAN -> R.string.fast_kind_ramadan
        FastKind.SUNNAH -> R.string.fast_kind_sunnah
        FastKind.QADA -> R.string.fast_kind_qada
    }

    private val shortDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val fullDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

    fun gregorian(date: LocalDate): String = shortDate.format(date)
    fun gregorianFull(date: LocalDate): String = fullDate.format(date)
}

/** "12 Jumada al-Akhirah 1448 AH", in the app's language. */
@Composable
fun hijriText(h: HijriDate): String = stringResource(R.string.hijri_date, h.day, stringResource(MuslimText.monthName(h.month)), h.year)

/** "+1 day", "−2 days" or "None". */
@Composable
fun offsetText(days: Int): String = when {
    days == 0 -> stringResource(R.string.muslim_offset_none)
    else -> {
        val sign = if (days > 0) "+" else "−"
        sign + pluralStringResource(R.plurals.muslim_offset_days, abs(days), abs(days))
    }
}

/** Joined names of [types], main type first. */
@Composable
fun sunnahTypesText(types: Set<SunnahType>): String {
    val res = LocalResources.current
    val ordered = types.sortedBy { if (it == Sunnah.primary(types)) -1 else it.ordinal }
    return ordered.joinToString(" · ") { res.getString(MuslimText.typeName(it)) }
}

/** Small "Sunnah" / "Qada" / "Ramadan" label for History rows; nothing for regular fasts. */
@Composable
fun FastKindChip(kind: FastKind) {
    val label = MuslimText.kindLabel(kind) ?: return
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        androidx.compose.material3.Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
