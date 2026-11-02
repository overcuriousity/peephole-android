package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.ApiResult
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Text helpers with no Compose in them, so plain JVM tests can use them.

/** The one 403 reason string (design §5: the `act` scope is binary). */
const val READ_ONLY = "Your pairing is read-only on this instance."

/** What a failed call means to the reader, in the instance's own words where it gave some. */
fun ApiResult<*>.describe(instanceName: String): String = when (this) {
    is ApiResult.Ok -> ""
    ApiResult.Revoked -> "This device's pairing with $instanceName was revoked."
    ApiResult.NoAct -> READ_ONLY
    ApiResult.PinMismatch -> "$instanceName is not presenting the pinned certificate. Nothing was sent."
    is ApiResult.Unreachable -> "Can't reach $instanceName ($reason)."
    is ApiResult.Failed -> message
}

private val utc = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

fun parseInstant(rfc3339: String?): Instant? = rfc3339?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** `2026-10-10 08:41 UTC`, or the raw text if it doesn't parse. */
fun utcTime(rfc3339: String?): String = parseInstant(rfc3339)?.let(utc::format) ?: rfc3339.orEmpty()

/** `5m ago`, `3h ago`, `2d ago`. */
fun ago(rfc3339: String?, now: Instant = Instant.now()): String {
    val at = parseInstant(rfc3339) ?: return rfc3339.orEmpty()
    val d = Duration.between(at, now).coerceAtLeast(Duration.ZERO)
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toHours() < 1 -> "${d.toMinutes()}m ago"
        d.toDays() < 1 -> "${d.toHours()}h ago"
        else -> "${d.toDays()}d ago"
    }
}

/** 1284 → `1,284`. */
fun count(n: Long): String = NumberFormat.getIntegerInstance().format(n)
