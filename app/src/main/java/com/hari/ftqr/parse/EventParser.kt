package com.hari.ftqr.parse

import com.hari.ftqr.model.ScanContent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal object EventParser {

    private val dateOnlyPattern = Regex("^\\d{8}$")
    private val utcDateTimePattern = Regex("^\\d{8}T\\d{6}Z$", RegexOption.IGNORE_CASE)
    private val localDateTimePattern = Regex("^\\d{8}T\\d{6}$", RegexOption.IGNORE_CASE)
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private class EventTime(val millis: Long, val dateOnly: Boolean)

    fun parse(text: String): ScanContent.Event? {
        val properties = ArrayList<PropertyLine>()
        var insideEvent = false
        var sawEvent = false
        var nestedDepth = 0

        for (line in LineFormat.unfoldLines(text)) {
            val property = LineFormat.parsePropertyLine(line) ?: continue
            val componentName = property.rawValue.trim().uppercase()
            if (property.name == "BEGIN") {
                if (!insideEvent && componentName == "VEVENT") {
                    insideEvent = true
                    sawEvent = true
                } else if (insideEvent) {
                    nestedDepth++
                }
                continue
            }
            if (property.name == "END") {
                if (insideEvent && nestedDepth > 0) {
                    nestedDepth--
                } else if (insideEvent && componentName == "VEVENT") {
                    break
                }
                continue
            }
            if (insideEvent && nestedDepth == 0) properties.add(property)
        }

        if (!sawEvent) return null

        fun first(name: String): PropertyLine? = properties.firstOrNull { it.name == name }
        fun textOf(name: String): String? = first(name)?.let { TextCodec.cleanText(it.decodedValue(), true) }

        val start = parseTime(first("DTSTART"))
        val end = parseTime(first("DTEND"))
        return ScanContent.Event(
            summary = textOf("SUMMARY"),
            startMillis = start?.millis,
            endMillis = end?.millis,
            allDay = start?.dateOnly == true,
            location = textOf("LOCATION"),
            description = textOf("DESCRIPTION"),
        )
    }

    private fun parseTime(property: PropertyLine?): EventTime? {
        if (property == null) return null
        val value = property.rawValue.trim()
        return runCatching {
            when {
                dateOnlyPattern.matches(value) -> EventTime(
                    LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
                        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    true,
                )
                utcDateTimePattern.matches(value) -> EventTime(
                    LocalDateTime.parse(value.dropLast(1).uppercase(), dateTimeFormatter)
                        .toInstant(ZoneOffset.UTC).toEpochMilli(),
                    false,
                )
                localDateTimePattern.matches(value) -> EventTime(
                    LocalDateTime.parse(value.uppercase(), dateTimeFormatter)
                        .atZone(zoneFor(property.parameter("TZID"))).toInstant().toEpochMilli(),
                    false,
                )
                else -> null
            }
        }.getOrNull()
    }

    internal fun zoneFor(timeZoneId: String?): ZoneId =
        timeZoneId?.trim()?.trim('"')?.takeIf { it.isNotEmpty() }?.let(::resolveZone) ?: ZoneId.systemDefault()

    private fun resolveZone(id: String): ZoneId? {
        zoneOrNull(id)?.let { return it }
        windowsZones[id.lowercase()]?.let { return zoneOrNull(it) }
        val segments = id.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        for (count in minOf(3, segments.size) downTo 1) {
            val candidate = segments.takeLast(count).joinToString("/")
            zoneOrNull(candidate)?.let { return it }
            windowsZones[candidate.lowercase()]?.let { mapped -> zoneOrNull(mapped)?.let { return it } }
        }
        return null
    }

    private fun zoneOrNull(id: String): ZoneId? = runCatching { ZoneId.of(id) }.getOrNull()

    private val windowsZones: Map<String, String> = mapOf(
        "India Standard Time" to "Asia/Kolkata",
        "UTC" to "UTC",
        "Coordinated Universal Time" to "UTC",
        "GMT Standard Time" to "Europe/London",
        "Greenwich Standard Time" to "Atlantic/Reykjavik",
        "Pacific Standard Time" to "America/Los_Angeles",
        "Mountain Standard Time" to "America/Denver",
        "Central Standard Time" to "America/Chicago",
        "Eastern Standard Time" to "America/New_York",
        "W. Europe Standard Time" to "Europe/Berlin",
        "Romance Standard Time" to "Europe/Paris",
        "Central Europe Standard Time" to "Europe/Budapest",
        "Central European Standard Time" to "Europe/Warsaw",
        "Singapore Standard Time" to "Asia/Singapore",
        "China Standard Time" to "Asia/Shanghai",
        "Tokyo Standard Time" to "Asia/Tokyo",
        "AUS Eastern Standard Time" to "Australia/Sydney",
        "Arabian Standard Time" to "Asia/Dubai",
        "Arab Standard Time" to "Asia/Riyadh",
        "SE Asia Standard Time" to "Asia/Bangkok",
        "Sri Lanka Standard Time" to "Asia/Colombo",
        "Nepal Standard Time" to "Asia/Kathmandu",
        "Bangladesh Standard Time" to "Asia/Dhaka",
        "Pakistan Standard Time" to "Asia/Karachi",
    ).mapKeys { it.key.lowercase() }
}
