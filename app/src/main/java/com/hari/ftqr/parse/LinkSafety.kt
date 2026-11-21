package com.hari.ftqr.parse

import com.hari.ftqr.model.LinkAssessment
import com.hari.ftqr.model.LinkWarning
import java.net.IDN

object LinkSafety {

    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "rebrand.ly", "cutt.ly",
        "shorturl.at", "tiny.cc", "rb.gy", "s.id", "t.ly", "bl.ink", "lnkd.in", "qrco.de", "short.io", "v.gd", "x.co",
    )
    private val standardPorts = setOf(80, 443)
    private val webSchemes = setOf("http", "https")
    private val schemePattern = Regex("^([a-zA-Z][a-zA-Z0-9+.\\-]*):")
    private val numericLabelPattern = Regex("^(\\d+|0x[0-9a-f]*)$")
    private val authorityTerminators = charArrayOf('/', '?', '#', '\\')

    fun assess(url: String): LinkAssessment {
        val trimmed = url.trim()
        return try {
            assessTrimmed(trimmed)
        } catch (error: Exception) {
            LinkAssessment(trimmed, "", "", emptyList())
        }
    }

    private fun authorityStart(scheme: String, afterScheme: String): String? = when {
        scheme in webSchemes -> afterScheme.trimStart('/', '\\')
        afterScheme.startsWith("//") -> afterScheme.substring(2)
        scheme.isEmpty() -> afterScheme
        else -> null
    }

    private fun assessTrimmed(trimmed: String): LinkAssessment {
        val scheme = schemePattern.find(trimmed)?.groupValues?.get(1)?.lowercase().orEmpty()
        val afterScheme = if (scheme.isEmpty()) trimmed else trimmed.substring(scheme.length + 1)
        val isWeb = scheme in webSchemes
        val hierarchicalPart = authorityStart(scheme, afterScheme).orEmpty()
        val authorityEnd = hierarchicalPart.indexOfAny(authorityTerminators).let { if (it < 0) hierarchicalPart.length else it }
        val authority = hierarchicalPart.substring(0, authorityEnd)
        val hasCredentials = authority.contains('@')
        val (rawHost, portText) = splitHostPort(authority.substringAfterLast('@'))
        val host = normalizeHost(rawHost)
        val port = portText?.trim()?.toIntOrNull()

        val warnings = ArrayList<LinkWarning>()
        if (scheme == "http") warnings.add(LinkWarning.INSECURE_HTTP)
        if (isWeb && host.isEmpty()) warnings.add(LinkWarning.MISSING_HOST)
        if (isShortener(host)) warnings.add(LinkWarning.SHORTENER)
        if (isIpAddress(host)) warnings.add(LinkWarning.IP_ADDRESS)
        if (isLookalike(host)) warnings.add(LinkWarning.LOOKALIKE_DOMAIN)
        if (hasCredentials) warnings.add(LinkWarning.CREDENTIALS_IN_URL)
        if (port != null && port !in standardPorts) warnings.add(LinkWarning.NON_STANDARD_PORT)
        if (scheme.isNotEmpty() && !isWeb) warnings.add(LinkWarning.UNUSUAL_SCHEME)

        return LinkAssessment(trimmed, scheme, host, warnings)
    }

    private fun normalizeHost(rawHost: String): String {
        val decoded = TextCodec.percentDecode(rawHost, plusAsSpace = false).trim().lowercase().trimEnd('.')
        if (decoded.isEmpty() || decoded.startsWith("[")) return decoded
        val ascii = try {
            IDN.toASCII(decoded)
        } catch (error: Exception) {
            null
        }
        return (ascii ?: decoded).lowercase().trimEnd('.')
    }

    private fun splitHostPort(hostAndPort: String): Pair<String, String?> {
        if (hostAndPort.startsWith("[")) {
            val closing = hostAndPort.indexOf(']')
            if (closing < 0) return hostAndPort to null
            val remainder = hostAndPort.substring(closing + 1)
            return hostAndPort.substring(0, closing + 1) to (if (remainder.startsWith(":")) remainder.substring(1) else null)
        }
        val colon = hostAndPort.indexOf(':')
        return if (colon >= 0) hostAndPort.substring(0, colon) to hostAndPort.substring(colon + 1) else hostAndPort to null
    }

    private fun isShortener(host: String): Boolean {
        val bareHost = host.removePrefix("www.")
        return shorteners.any { bareHost == it || bareHost.endsWith(".$it") }
    }

    private fun isIpAddress(host: String): Boolean =
        host.startsWith("[") || numericLabelPattern.matches(host.substringAfterLast('.'))

    private fun isLookalike(host: String): Boolean =
        host.any { it.code > 127 } || host.split('.').any { it.startsWith("xn--") }
}
