package com.fastingshift.app.ui.partner

import java.net.URI

internal object PartnerLinks {
    private val code = Regex("[a-f0-9]{64}")
    private val hosts = setOf("www.fastingshift.com", "fastingshift.com")

    fun valid(codeText: String) = code.matches(codeText)
    fun link(token: String): String {
        require(valid(token))
        return "https://www.fastingshift.com/partner/#$token"
    }

    private fun recognized(uri: URI): Boolean {
        if (uri.rawQuery != null || uri.rawUserInfo != null || uri.port != -1) return false
        return when (uri.scheme?.lowercase()) {
            "https" -> uri.rawAuthority?.lowercase() in hosts && uri.rawPath in setOf("/partner", "/partner/")
            "fastingshift" -> uri.rawAuthority?.lowercase() == "partner" && uri.rawPath in setOf("", "/")
            else -> false
        }
    }

    fun isPartnerUrl(text: String) = runCatching { recognized(URI(text)) }.getOrDefault(false)

    fun token(text: String): String? {
        val trimmed = text.trim()
        if (valid(trimmed)) return trimmed
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        return if (recognized(uri)) uri.rawFragment?.takeIf(::valid) else null
    }
}
