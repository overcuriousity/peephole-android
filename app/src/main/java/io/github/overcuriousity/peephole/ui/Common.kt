package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.overcuriousity.peephole.net.ApiResult
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The one 403 reason string (design §5: the `act` scope is binary). */
const val READ_ONLY = "Your pairing is read-only on this instance."

val Warning = Color(0xFFF0B344)

/** Severity text colours, peephole's ramp (design §3). */
private val SeverityColors = listOf(
    Color(0xFF9A9EB2),
    Color(0xFFF0C34A),
    Color(0xFFFF9440),
    Color(0xFFFF5C5C),
    Color(0xFFD17DFF),
)

/** What a failed call means to the reader, in the instance's own words where it gave some. */
fun ApiResult<*>.describe(instanceName: String): String = when (this) {
    is ApiResult.Ok -> ""
    ApiResult.Revoked -> "This device's pairing with $instanceName was revoked."
    ApiResult.NoAct -> READ_ONLY
    ApiResult.PinMismatch -> "$instanceName is not presenting the pinned certificate. Nothing was sent."
    is ApiResult.Unreachable -> "Can't reach $instanceName ($reason)."
    is ApiResult.Failed -> message
}

/** Sends a revoked pairing to the interstitial (design §5, 401). */
@Composable
fun RevokedEffect(result: ApiResult<*>?, onRevoked: () -> Unit) {
    LaunchedEffect(result) { if (result == ApiResult.Revoked) onRevoked() }
}

/** True while the screen is in the foreground; polling stops when it is not. */
@Composable
fun isResumed(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

@Composable
fun SeverityChip(severity: Int?) {
    if (severity == null) return
    val color = SeverityColors[severity.coerceIn(0, SeverityColors.lastIndex)]
    Text(
        "● sev $severity",
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A job status with its tone; the word carries the meaning, not only the colour. */
@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        "running" -> Warning
        "done" -> Success
        "failed", "declined", "lapsed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        status.ifBlank { "unknown" },
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/** Static skeleton rows shaped like list content (no shimmer, so nothing to reduce for motion). */
@Composable
fun SkeletonRows(count: Int = 6) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count) {
            Box(
                Modifier.fillMaxWidth(0.6f).height(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)),
            )
            Box(
                Modifier.fillMaxWidth(0.4f).height(12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
fun EmptyState(text: String, action: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) Button(onClick = action.second, modifier = Modifier.padding(top = 16.dp)) { Text(action.first) }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A collapsible section with a heading row that is a 48dp touch target. */
@Composable
fun Section(title: String, initiallyOpen: Boolean = true, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (open) "▾" else "▸", modifier = Modifier.width(20.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        }
        if (open) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
        }
    }
}

/** A disabled action keeps its place and says why (design §1: never hidden). */
@Composable
fun ActionButton(label: String, enabled: Boolean, reason: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
        if (!enabled && reason != null) {
            Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Design §5, 401: a full-screen stop, since nothing on this instance works any more. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevokedScreen(instanceName: String, onPairAgain: () -> Unit, onRemove: () -> Unit, onClose: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(instanceName) }, navigationIcon = { BackButton(onClose) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "This device's pairing with $instanceName was revoked. Pair again to continue.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(Spacing.lg))
            Button(onClick = onPairAgain, modifier = Modifier.fillMaxWidth()) { Text("Pair again") }
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Remove instance") }
        }
    }
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
