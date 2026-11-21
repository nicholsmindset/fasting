package com.fastingshift.app.platform

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Best guess at the country the user is in, for region-specific safety info such as the
 * emergency number. Needs no permission: the mobile network's country first (where the user
 * is right now, so a traveller gets the local emergency number), then the SIM's country, then
 * the device locale.
 */
object RegionResolver {
    /** ISO 3166-1 alpha-2 country code, uppercased, or null if nothing is known. */
    fun countryCode(context: Context): String? {
        val tm = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()
        val candidates = sequence {
            yield(runCatching { tm?.networkCountryIso }.getOrNull())
            yield(runCatching { tm?.simCountryIso }.getOrNull())
            yield(Locale.getDefault().country)
        }
        return candidates.firstNotNullOfOrNull { normalize(it) }
    }

    internal fun normalize(code: String?): String? = code?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
}
