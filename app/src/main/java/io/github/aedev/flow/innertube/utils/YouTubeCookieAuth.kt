package io.github.aedev.flow.innertube.utils

import java.security.MessageDigest
import java.time.Instant

internal sealed interface CookieParseResult {
    data class Valid(
        val cookieHeader: String,
        val cookies: Map<String, String>,
    ) : CookieParseResult

    data object Empty : CookieParseResult

    data object InvalidFormat : CookieParseResult

    data object MissingRequiredCookies : CookieParseResult

    data object RequiredCookieExpired : CookieParseResult
}

internal object YouTubeCookieParser {
    private val requiredCookies = setOf("SID", "SAPISID", "__Secure-3PSID")

    fun parse(
        input: String,
        nowEpochSeconds: Long = Instant.now().epochSecond,
    ): CookieParseResult {
        if (input.isBlank()) return CookieParseResult.Empty

        val parsed =
            if (input.lineSequence().any { it.contains('\t') }) {
                parseNetscape(input)
            } else {
                parseCookieHeader(input)
            }
        if (parsed.isEmpty()) return CookieParseResult.InvalidFormat

        val expiredRequired =
            parsed.any { cookie ->
                cookie.name in requiredCookies &&
                    cookie.expiresAtEpochSeconds?.let { it > 0 && it <= nowEpochSeconds } == true
            }
        if (expiredRequired) return CookieParseResult.RequiredCookieExpired

        val valid =
            parsed
                .filterNot { cookie ->
                    cookie.expiresAtEpochSeconds?.let { it > 0 && it <= nowEpochSeconds } == true
                }.associate { it.name to it.value }
        if (!valid.keys.containsAll(requiredCookies)) return CookieParseResult.MissingRequiredCookies

        return CookieParseResult.Valid(
            cookieHeader = valid.entries.joinToString("; ") { (name, value) -> "$name=$value" },
            cookies = valid,
        )
    }

    private fun parseCookieHeader(input: String): List<ParsedCookie> {
        val header =
            input
                .trim()
                .lineSequence()
                .joinToString(" ")
                .replace(Regex("^Cookie\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        return header
            .split(';')
            .mapNotNull { item ->
                val separator = item.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val name = item.substring(0, separator).trim()
                val value = item.substring(separator + 1).trim()
                if (name.isBlank() || value.isBlank() || name.any(Char::isWhitespace)) {
                    null
                } else {
                    ParsedCookie(name, value)
                }
            }
    }

    private fun parseNetscape(input: String): List<ParsedCookie> =
        input
            .lineSequence()
            .mapNotNull { originalLine ->
                val line = originalLine.removePrefix("#HttpOnly_")
                if (line.isBlank() || (originalLine.startsWith('#') && !originalLine.startsWith("#HttpOnly_"))) {
                    return@mapNotNull null
                }
                val columns = line.split('\t')
                if (columns.size < 7) return@mapNotNull null
                val domain = columns[0].trim().removePrefix(".").lowercase()
                if (domain != "youtube.com" && !domain.endsWith(".youtube.com")) return@mapNotNull null
                val expiresAt = columns[4].trim().toLongOrNull()
                val name = columns[5].trim()
                val value = columns.drop(6).joinToString("\t").trim()
                if (name.isBlank() || value.isBlank()) {
                    null
                } else {
                    ParsedCookie(name, value, expiresAt)
                }
            }.toList()

    private data class ParsedCookie(
        val name: String,
        val value: String,
        val expiresAtEpochSeconds: Long? = null,
    )
}

internal object YouTubeCookieAuthHeaders {
    fun build(
        cookieHeader: String?,
        origin: String,
        timestampSeconds: Long,
    ): Map<String, String> {
        if (cookieHeader.isNullOrBlank()) return emptyMap()
        val cookies = parseCookieString(cookieHeader)
        val sapisid = cookies["SAPISID"]?.takeIf(String::isNotBlank) ?: return emptyMap()
        val digest =
            MessageDigest
                .getInstance("SHA-1")
                .digest("$timestampSeconds $sapisid $origin".toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return mapOf(
            "Cookie" to cookieHeader,
            "Authorization" to "SAPISIDHASH ${timestampSeconds}_$digest",
            "X-Origin" to origin,
            "X-Goog-AuthUser" to "0",
        )
    }
}
