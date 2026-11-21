package com.hari.ftqr.parse

import com.hari.ftqr.model.LinkAssessment
import com.hari.ftqr.model.LinkWarning

object LinkSafety {

    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "rebrand.ly", "cutt.ly",
        "shorturl.at", "tiny.cc", "rb.gy", "s.id", "t.ly", "bl.ink", "lnkd.in", "qrco.de", "short.io", "v.gd", "x.co",
    )
    private val standardPorts = setOf(80, 443)
    private val schemePattern = Regex("^([a-zA-Z][a-zA-Z0-9+.\\-]*):")
    private val ipv4Pattern = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val numericHostPattern = Regex("^(0x[0-9a-f]+|\\d+)$")
    private val authorityTerminators = charArrayOf('/', '?', '#', '\\')

    fun assess(url: String): LinkAssessment {
        val trimmed = url.trim()
        return try {
            assessTrimmed(trimmed)
        } catch (error: Exception) {
            LinkAssessment(trimmed, "", "", emptyList())
        }
    }

    private fun assessTrimmed(trimmed: String): LinkAssessment {
        val scheme = schemePattern.find(trimmed)?.groupValues?.get(1)?.lowercase().orEmpty()
        val afterScheme = if (scheme.isEmpty()) trimmed else trimmed.substring(scheme.length + 1)
        val hierarchicalPart = when {
            afterScheme.startsWith("//") -> afterScheme.substring(2)
            scheme.isEmpty() -> afterScheme
            else -> ""
        }
        val authorityEnd = hierarchicalPart.indexOfAny(authorityTerminators).let { if (it < 0) hierarchicalPart.length else it }
        val authority = hierarchicalPart.substring(0, authorityEnd)
        val hasCredentials = authority.contains('@')
        val (rawHost, portText) = splitHostPort(authority.substringAfterLast('@'))
        val host = rawHost.trim().lowercase().trimEnd('.')
        val port = portText?.trim()?.toIntOrNull()

        val warnings = ArrayList<LinkWarning>()
        if (scheme == "http") warnings.add(LinkWarning.INSECURE_HTTP)
        if (isShortener(host)) warnings.add(LinkWarning.SHORTENER)
        if (isIpAddress(host)) warnings.add(LinkWarning.IP_ADDRESS)
        if (isLookalike(host)) warnings.add(LinkWarning.LOOKALIKE_DOMAIN)
        if (hasCredentials) warnings.add(LinkWarning.CREDENTIALS_IN_URL)
        if (port != null && port !in standardPorts) warnings.add(LinkWarning.NON_STANDARD_PORT)
        if (scheme.isNotEmpty() && scheme != "http" && scheme != "https") warnings.add(LinkWarning.UNUSUAL_SCHEME)

        return LinkAssessment(trimmed, scheme, host, warnings)
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
        host.startsWith("[") || ipv4Pattern.matches(host) || numericHostPattern.matches(host)

    private fun isLookalike(host: String): Boolean =
        host.any { it.code > 127 } || host.split('.').any { it.startsWith("xn--") }
}
