package com.fastingshift.app.ui.food

import android.os.SystemClock
import com.fastingshift.app.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** GTIN-8, UPC-A, EAN-13 and GTIN-14, including the GS1 check digit. Preserve leading zeroes. */
internal fun validGtin(code: String): Boolean {
    if (code.length !in setOf(8, 12, 13, 14) || code.any { it !in '0'..'9' } || code.all { it == '0' }) return false
    val sum = code.reversed().mapIndexed { index, c -> c.digitToInt() * if (index % 2 == 0) 1 else 3 }.sum()
    return sum % 10 == 0
}

internal data class LabelValue(val amount: Double, val modifier: String)
internal data class LabelAmounts(val energy: LabelValue?, val sugars: LabelValue?) {
    val available get() = energy != null || sugars != null
}

internal data class FoodLabel(
    val name: String?,
    val serving: String?,
    val perServing: LabelAmounts,
    val perHundred: LabelAmounts,
    val explicitlyPer100ml: Boolean,
)

internal sealed interface LookupResult {
    data class Found(val label: FoodLabel) : LookupResult
    data object Missing : LookupResult
    data object Unavailable : LookupResult
    data object SlowDown : LookupResult
}

private fun JSONObject.field(key: String): String? =
    (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(160)

internal fun parseFoodLabel(body: String): LookupResult {
    val json = JSONObject(body)
    if (json.optInt("status", -1) == 0) return LookupResult.Missing
    if (json.optInt("status", -1) != 1) return LookupResult.Unavailable
    val product = json.optJSONObject("product") ?: return LookupResult.Unavailable
    val nutrients = product.optJSONObject("nutriments") ?: JSONObject()
    fun value(key: String, basis: String): LabelValue? {
        val amount = nutrients.optDouble("${key}_${basis}", Double.NaN)
        if (!amount.isFinite() || amount < 0) return null
        val modifier = nutrients.field("${key}_modifier").takeIf { it == "<" || it == ">" } ?: ""
        return LabelValue(amount, modifier)
    }
    fun amounts(basis: String) = LabelAmounts(value("energy-kcal", basis), value("sugars", basis))
    return LookupResult.Found(FoodLabel(
        product.field("product_name"), product.field("serving_size"), amounts("serving"), amounts("100g"),
        product.field("nutrition_data_per") == "100ml",
    ))
}

private const val FIELDS = "product_name,serving_size,nutrition_data_per,nutriments"

/** Limit request starts across all sheets. Provider limits reads to 15/minute/IP. */
internal class LookupPacer(private val nowMillis: () -> Long) {
    private var nextAllowed = 0L
    @Synchronized fun take(): Boolean {
        val now = nowMillis()
        if (now < nextAllowed) return false
        nextAllowed = now + 4_100L
        return true
    }
    @Synchronized fun coolDown(seconds: Long) {
        nextAllowed = maxOf(nextAllowed, nowMillis() + seconds.coerceIn(1, 86_400) * 1_000L)
    }
}

internal object OpenFoodFacts {
    private val pacer = LookupPacer(SystemClock::elapsedRealtime)

    fun lookup(code: String): LookupResult {
        require(validGtin(code))
        if (!pacer.take()) return LookupResult.SlowDown
        val connection = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$FIELDS")
            .openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 6_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "FastingShift/${BuildConfig.VERSION_NAME} (https://www.fastingshift.com/app)")
            when (connection.responseCode) {
                200 -> connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val chars = CharArray(4096)
                    val body = StringBuilder()
                    while (true) {
                        val count = reader.read(chars)
                        if (count < 0) break
                        if (body.length + count > 256_000) throw IOException("Oversize product response")
                        body.append(chars, 0, count)
                    }
                    parseFoodLabel(body.toString())
                }
                404 -> LookupResult.Missing
                429 -> {
                    pacer.coolDown(connection.getHeaderField("Retry-After")?.toLongOrNull() ?: 60L)
                    LookupResult.SlowDown
                }
                else -> LookupResult.Unavailable
            }
        } catch (_: IOException) {
            LookupResult.Unavailable
        } catch (_: org.json.JSONException) {
            LookupResult.Unavailable
        } finally {
            connection.disconnect()
        }
    }
}
