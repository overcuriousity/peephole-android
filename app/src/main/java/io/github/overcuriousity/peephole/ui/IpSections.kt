package io.github.overcuriousity.peephole.ui

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.R
import io.github.overcuriousity.peephole.net.Bucket
import io.github.overcuriousity.peephole.net.CalendarDay
import io.github.overcuriousity.peephole.net.Command
import io.github.overcuriousity.peephole.net.Event
import io.github.overcuriousity.peephole.net.IpCanaries
import io.github.overcuriousity.peephole.net.IpDetail
import io.github.overcuriousity.peephole.net.IpDossier
import io.github.overcuriousity.peephole.net.ProbeGroup
import io.github.overcuriousity.peephole.net.Upload
import io.github.overcuriousity.peephole.net.Verdict
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * Past this a share intent risks Binder's transaction limit (text travels
 * as UTF-16, and the chooser parcels it again); the item says so instead.
 */
private const val SHARE_MAX_CHARS = 100_000

/** Rows the timeline shows before "Show all". */
private const val TIMELINE_FIRST = 50

/**
 * The app bar's Share: the address as text, and the dossier's JSON when
 * there is one. Plain text through the share sheet; nothing is written to disk.
 */
@Composable
fun ShareMenu(text: () -> String, json: (() -> String)?, textLabel: String = "Address") {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    fun send(body: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
        // A device with a smaller transaction buffer still refuses; say so rather than crash.
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
            .onFailure { Toast.makeText(context, "Could not share: too large for this device.", Toast.LENGTH_LONG).show() }
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(painterResource(R.drawable.ic_share), "Share") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(textLabel) }, onClick = {
                open = false
                send(text())
            })
            if (json != null) {
                val body = remember(json) { json() }
                val fits = body.length <= SHARE_MAX_CHARS
                DropdownMenuItem(
                    text = { Text(if (fits) "Dossier JSON" else "Dossier JSON (too large to share)") },
                    enabled = fits,
                    onClick = {
                        open = false
                        send(body)
                    },
                )
            }
        }
    }
}

/** Address, property chips, place, ASN and campaign links, first and last seen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IpHeader(
    addr: String,
    ip: IpDetail?,
    dossier: IpDossier?,
    onOpenAsn: (Long) -> Unit,
    onOpenCampaign: (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(addr, style = MonoMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize))
            }
            IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Address", addr))) } }) {
                Icon(painterResource(R.drawable.ic_content_copy), "Copy address")
            }
        }
        if (ip == null) return@Column
        val chips = propChips(dossier?.props, ip.unseen, ip.isTorRelay).let { c ->
            // An older server has no props; its is_tor still says Tor exit.
            if (dossier?.props == null && ip.isTor) listOf("Tor exit") + c else c
        }
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val brand = LocalPeepholeColors.current.brand
                chips.forEach { ToneChip(it, if (it.startsWith("Tor")) brand else muted) }
            }
        }
        ip.geo.country?.let { Text(it) }
        ip.geo.asn?.let { asn ->
            val label = "AS$asn" + (ip.geo.asnOrg?.let { " $it" } ?: "")
            Text(
                "$label ›",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.heightIn(min = 48.dp).clickable { onOpenAsn(asn) }.padding(vertical = 12.dp),
            )
        }
        dossier?.campaign?.let { c ->
            AssistChip(onClick = { onOpenCampaign(c.key) }, label = { Text("campaign #${c.num} ›") })
        }
        ip.firstSeen?.let { Text("first seen ${utcTime(it)}", color = muted) }
        ip.lastSeen?.let { Text("last seen ${utcTime(it)}", color = muted) }
    }
}

/** The web's signals strip: a pill per reading, the abuse score with its meter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalStrip(signals: List<Signal>) {
    if (signals.isEmpty()) return
    val colors = LocalPeepholeColors.current
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        signals.forEach { s ->
            val tone = when (s.tone) {
                Tone.Ok -> colors.success
                Tone.Warn -> colors.warning
                Tone.Bad -> MaterialTheme.colorScheme.error
                Tone.Info -> MaterialTheme.colorScheme.primary
            }
            Column(
                Modifier
                    .background(tone.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(s.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.value, style = MaterialTheme.typography.bodyMedium, color = tone)
                s.meter?.let { Meter(it, tone, colors.chartTrack) }
            }
        }
    }
}

@Composable
private fun Meter(value: Int, fill: Color, track: Color) {
    Canvas(Modifier.padding(top = Spacing.xs).width(64.dp).height(4.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(fill, size = Size(size.width * value / 100f, size.height), cornerRadius = r)
    }
}

/** The evidence verdict or the address-sharing reading, with the lines it rests on. */
@Composable
fun VerdictSection(title: String, v: Verdict) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Section(title) {
        Text(v.label, style = MaterialTheme.typography.titleSmall)
        Text(v.sentence)
        v.note?.let { Text(it, color = muted, style = MaterialTheme.typography.bodySmall) }
        v.caveat?.let { Text(it, color = muted, style = MaterialTheme.typography.bodySmall) }
        v.lines.forEach { l ->
            Text(
                listOfNotNull(l.kind, l.what, l.port?.let { "port $it" }, l.source, l.date).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/** The week bars and the half-year calendar, dated from the dossier's own time. */
@Composable
fun ActivitySection(week: List<Bucket>, calendar: List<CalendarDay>, generatedAt: String?) {
    val end = remember(generatedAt) { parseInstant(generatedAt) ?: Instant.now() }
    val bars = remember(week, end) { weekBars(week, end) }
    val grid = remember(calendar, end) { calendarGrid(calendar, end.atZone(ZoneOffset.UTC).toLocalDate()) }
    Section("Activity") {
        WeekChart(bars)
        CalendarChart(grid, Modifier.padding(top = Spacing.md))
    }
}

/**
 * One Timeline row, inside a [Section]'s inset; a request opens its own
 * screen. Paths and summaries are attacker text, shown as plain text.
 */
@Composable
fun EventRow(e: Event, onOpenRequest: (Long) -> Unit, showIp: Boolean = false) {
    val request = e.kind == "request"
    val what = if (request) {
        listOfNotNull(e.method, e.path?.let { it + (e.query?.takeIf { q -> q.isNotEmpty() }?.let { q -> "?$q" } ?: "") }).joinToString(" ")
    } else {
        e.summary.orEmpty()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (request) Modifier.clickable { onOpenRequest(e.id) } else Modifier)
            .heightIn(min = 48.dp)
            .padding(vertical = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(utcTime(e.at), e.kind, e.ip?.takeIf { showIp }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            SeverityChip(e.severity)
            if (request) Text(" ›", color = MaterialTheme.colorScheme.primary)
        }
        if (what.isNotBlank()) Text(what, style = MonoSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (e.labels.isNotEmpty()) {
            Text(e.labels.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The newest events, [TIMELINE_FIRST] at first; says when the dossier left older ones out. */
@Composable
fun TimelineSection(events: List<Event>, truncated: Boolean, onOpenRequest: (Long) -> Unit, showIp: Boolean = false) {
    var all by rememberSaveable { mutableStateOf(false) }
    Section("Timeline") {
        if (events.isEmpty()) Text("No events.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        (if (all) events else events.take(TIMELINE_FIRST)).forEach { EventRow(it, onOpenRequest, showIp) }
        if (!all && events.size > TIMELINE_FIRST) TextButton(onClick = { all = true }) { Text("Show all ${events.size}") }
        if (truncated) Text("Older events not shown.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProbeHistorySection(groups: List<ProbeGroup>) {
    if (groups.isEmpty()) return
    Section("Probe history", initiallyOpen = false) {
        groups.forEach { g ->
            Text("asked ${utcTime(g.askedAt)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
            g.members.forEach { m ->
                val open = m.ports.filter { it.outcome == "open" }.joinToString(", ") { "${it.port}/${it.protocol}" }
                Text(
                    listOfNotNull(m.vantage, m.state, m.why, m.rttMs?.let { "$it ms" }, open.ifEmpty { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Uploads (metadata only), webshell commands and canary reuse; nothing when all are empty. */
@Composable
fun LuresSection(uploads: List<Upload>, commands: List<Command>, canaries: IpCanaries) {
    if (uploads.isEmpty() && commands.isEmpty() && canaries.reuses.isEmpty() && canaries.usedElsewhere == 0L && canaries.usedFrom == 0L) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Section("Lures", initiallyOpen = false) {
        if (canaries.usedElsewhere > 0 || canaries.usedFrom > 0) {
            Text("Canaries served here used elsewhere: ${count(canaries.usedElsewhere)} · used here from elsewhere: ${count(canaries.usedFrom)}")
        }
        canaries.reuses.forEach { r ->
            Text(
                "${r.kind} (${r.place}) served to ${r.servedIp}, used from ${r.usedIp} after ${duration(r.deltaS)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (uploads.isNotEmpty()) FieldLabel("Uploads")
        uploads.forEach { u ->
            Text(u.filename ?: "(no name)", style = MonoSmall)
            Text(
                listOf(u.sniffedType, "${count(u.size)} bytes", utcTime(u.at), u.sha256.take(12)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        if (commands.isNotEmpty()) FieldLabel("Webshell commands")
        commands.forEach { c -> Text(c.command, style = MonoSmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    }
}

/** `90 s`, `12 min`, `5 h`, `3 d`. */
private fun duration(seconds: Long): String = when {
    seconds < 120 -> "$seconds s"
    seconds < 7_200 -> "${seconds / 60} min"
    seconds < 172_800 -> "${seconds / 3_600} h"
    else -> "${seconds / 86_400} d"
}
