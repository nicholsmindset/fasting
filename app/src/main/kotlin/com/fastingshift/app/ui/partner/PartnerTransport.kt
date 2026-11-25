package com.fastingshift.app.ui.partner

import com.fastingshift.app.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONException
import org.json.JSONObject

internal class PartnerRequestException(val status: Int) : IOException("Partner service returned HTTP $status") {
    val retryable get() = status == 408 || status == 429 || status >= 500
}

internal fun interface PartnerTransport {
    fun send(body: JSONObject): JSONObject
}

internal class HttpPartnerTransport : PartnerTransport {
    override fun send(body: JSONObject): JSONObject {
        val connection = URL(BuildConfig.PARTNER_ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 6000
            connection.readTimeout = 8000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != 200) throw PartnerRequestException(connection.responseCode)
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return try {
                JSONObject(payload)
            } catch (badResponse: JSONException) {
                throw IOException("Unexpected partner service response", badResponse)
            }
        } finally {
            connection.disconnect()
        }
    }
}
