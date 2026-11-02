package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.net.Related
import io.github.overcuriousity.peephole.store.Instance

/**
 * One request (`/admin/requests/{id}`). Target, headers, body and UA are
 * attacker text: shown only as selectable plain text, never linked,
 * opened or rendered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestScreen(
    api: PeepholeApi,
    instance: Instance,
    id: Long,
    onBack: () -> Unit,
    onOpenIp: (String) -> Unit,
    onOpenRequest: (Long) -> Unit,
    onOpenCampaign: (String) -> Unit,
    onRevoked: () -> Unit,
) {
    DossierScaffold(
        title = "Request #$id",
        titleStyle = MaterialTheme.typography.titleLarge,
        instanceName = instance.instanceName,
        shareText = "Request #$id",
        shareLabel = "Request number",
        load = { api.requestDossier(id) },
        onBack = onBack,
        onRevoked = onRevoked,
    ) { d ->
        val r = d.request
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                SelectionContainer { Text("${r.method} ${r.target}", style = MonoMedium) }
                Text(utcTime(r.at), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SeverityChip(r.severity)
                    d.campaign?.let { c -> AssistChip(onClick = { onOpenCampaign(c.key) }, label = { Text("campaign #${c.num} ›") }) }
                }
                if (r.ip.isNotBlank()) {
                    Text(
                        "${r.ip} ›",
                        style = MonoMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.heightIn(min = 48.dp).clickable { onOpenIp(r.ip) }.padding(vertical = 12.dp),
                    )
                }
            }
        }
        item {
            Section("Request") {
                Fact("Host", r.host, mono = true)
                Fact("Answer", r.answer)
                Fact("Decoy", r.decoy)
                Fact("Status", r.status?.toString())
                Fact("Held", r.heldMs?.let { "$it ms" })
                Fact("Persona", r.persona)
                Fact("Transport", r.transport)
                Fact("Via proxy", r.viaProxy?.let { if (it) "yes" else "no" })
                Fact("Labels", r.labels.joinToString(" · "))
                Fact("OWASP", r.owasp.joinToString(" · "))
                Fact("JA4", r.ja4, mono = true)
                Fact("JA4H", r.ja4h, mono = true)
                Fact("User agent", r.userAgent, mono = true)
            }
            SectionDivider()
        }
        if (r.headers.isNotEmpty()) {
            item {
                Section("Headers", initiallyOpen = false) {
                    SelectionContainer {
                        Text(
                            r.headers.joinToString("\n") { h -> h.joinToString(": ") },
                            style = MonoSmall,
                            softWrap = false,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }
                SectionDivider()
            }
        }
        bodyText(r)?.let { body ->
            item {
                Section("Body", initiallyOpen = false) { SelectionContainer { Text(body, style = MonoSmall) } }
                SectionDivider()
            }
        }
        d.webshellCmd?.let { cmd ->
            item {
                Section("Webshell command") { SelectionContainer { Text(cmd, style = MonoSmall) } }
                SectionDivider()
            }
        }
        if (d.sameIp.isNotEmpty()) {
            item {
                Section("Same address", initiallyOpen = false) { d.sameIp.forEach { RelatedRow(it, onOpenRequest) } }
                SectionDivider()
            }
        }
        if (d.sameJa4.recent.isNotEmpty()) {
            item {
                Section("Same JA4 elsewhere", initiallyOpen = false) {
                    Text(
                        "${count(d.sameJa4.ips)} other addresses in the last ${d.sameJa4.days} days",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    d.sameJa4.recent.forEach { RelatedRow(it, onOpenRequest, showIp = true) }
                }
            }
        }
    }
}

@Composable
private fun RelatedRow(r: Related, onOpenRequest: (Long) -> Unit, showIp: Boolean = false) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpenRequest(r.id) }.padding(vertical = 4.dp)) {
        Text(
            listOfNotNull(utcTime(r.at), r.ip.takeIf { showIp }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("${r.method} ${r.path} ›", style = MonoSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
