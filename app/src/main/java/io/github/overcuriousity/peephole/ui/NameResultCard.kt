package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.lookup.NameRow
import io.github.overcuriousity.peephole.lookup.agreementLine
import io.github.overcuriousity.peephole.lookup.isDisputed
import io.github.overcuriousity.peephole.net.AddrVote

/** Agreed addresses the server looks up per name; the rest are listed in `also` (api-v1 "names"). */
private const val MAX_FOLLOWED = 16

/** v2 §3.1: one looked-up name, its addresses (each opens IP Detail) and how far the resolvers agreed. */
@Composable
fun NameResultCard(row: NameRow, instanceName: String, onOpenIp: (String) -> Unit) {
    Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            when (row) {
                is NameRow.Resolved -> Resolved(row, onOpenIp)
                is NameRow.Failed -> {
                    Header(row.host, MaterialTheme.colorScheme.onSurface)
                    Line(failedMessage(row), MaterialTheme.colorScheme.error)
                }
                is NameRow.TooOld -> {
                    Header(row.host, MaterialTheme.colorScheme.onSurface)
                    Line(tooOldMessage(instanceName), MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Resolved(row: NameRow.Resolved, onOpenIp: (String) -> Unit) {
    val r = row.resolve
    val n = r.votes.size
    Header(
        "${row.host} → $n ${if (n == 1) "address" else "addresses"}",
        if (isDisputed(r)) Warning else MaterialTheme.colorScheme.onSurface,
    )
    // Never omitted: no caveat must not read as unanimity (v2 §3.1).
    Line(agreementLine(r), MaterialTheme.colorScheme.onSurfaceVariant)
    val stored = row.results.associateBy { it.ip }
    r.votes.forEach { vote -> AddressRow(vote, stored[vote.ip]?.maxSeverity, vote.ip in row.missing, onOpenIp) }
    if (r.also.isNotEmpty()) {
        Line(
            "Also resolves to ${r.also.joinToString(", ")} (not looked up: only the first $MAX_FOLLOWED agreed addresses are)",
            MaterialTheme.colorScheme.onSurfaceVariant,
            mono = true,
        )
    }
    if (r.resolvers.isNotEmpty()) Line("asked: ${r.resolvers.joinToString(", ")}", MaterialTheme.colorScheme.onSurfaceVariant)
    r.errors.forEach { e -> Line("${e.node} did not answer: ${e.why}", MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** `IpRow` style; the verdict takes the summary line. Tappable whether agreed or not. */
@Composable
private fun AddressRow(vote: AddrVote, severity: Int?, notStored: Boolean, onOpenIp: (String) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onOpenIp(vote.ip) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(vote.ip, style = MonoMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.sm))
                if (notStored) Text("not stored", color = MaterialTheme.colorScheme.onSurfaceVariant) else SeverityChip(severity)
            }
        },
        supportingContent = if (vote.verdict.isNotEmpty()) {
            { Text(vote.verdict, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            null
        },
    )
}

@Composable
private fun Header(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun Line(text: String, color: Color, mono: Boolean = false) {
    Text(
        text,
        color = color,
        style = if (mono) MonoMedium else MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

/** v2 §3.2: a per-name failure, in words the reader can act on. */
fun failedMessage(row: NameRow.Failed): String = when (row.code) {
    "unresolved" -> "${row.host} — could not be resolved."
    "capped" -> "${row.host} — not resolved: max 10 names per lookup."
    "invalid" -> "${row.host} — not a host name or URL."
    else -> "${row.host} — ${row.message}"
}

/** Plan Decision 2: the server predates name lookup; its IP results still show. */
fun tooOldMessage(instanceName: String): String =
    "Server too old for name lookup — update peephole on $instanceName to look up host names."
