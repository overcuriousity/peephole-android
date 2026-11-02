package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.store.Instance

/** The ASN page (`/admin/asn/{n}`): its counts, labels, ports, campaigns and busiest addresses. */
@Composable
fun AsnScreen(
    api: PeepholeApi,
    instance: Instance,
    asn: Long,
    onBack: () -> Unit,
    onOpenIp: (String) -> Unit,
    onOpenCampaign: (String) -> Unit,
    onRevoked: () -> Unit,
) {
    DossierScaffold(
        title = "AS$asn",
        titleStyle = MonoMedium,
        instanceName = instance.instanceName,
        shareText = "AS$asn",
        shareLabel = "AS number",
        load = { api.asnDossier(asn) },
        onBack = onBack,
        onRevoked = onRevoked,
    ) { d ->
        item {
            Section(d.org ?: "AS$asn") {
                Fact("Addresses", count(d.ips))
                Fact("Requests", count(d.requests))
                Fact("First seen", d.firstSeen?.let(::utcTime))
                Fact("Last seen", d.lastSeen?.let(::utcTime))
                d.maxSeverity?.let { SeverityChip(it) }
            }
            SectionDivider()
        }
        if (d.labels.isNotEmpty()) {
            item {
                Section("Labels") { d.labels.forEach { Fact(it.label, "${count(it.ips)} addresses") } }
                SectionDivider()
            }
        }
        if (d.openPorts.isNotEmpty()) {
            item {
                Section("Open ports", initiallyOpen = false) { d.openPorts.forEach { Fact("${it.port}", "${count(it.ips)} addresses", mono = true) } }
                SectionDivider()
            }
        }
        if (d.campaigns.isNotEmpty()) {
            item {
                Section("Campaigns") {
                    d.campaigns.forEach { c ->
                        Text(
                            "campaign #${c.num} · ${count(c.ips)} addresses ›",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.heightIn(min = 48.dp).clickable { onOpenCampaign(c.key) }.padding(vertical = 12.dp),
                        )
                    }
                }
                SectionDivider()
            }
        }
        item {
            val shown = d.topIps.size
            val total = d.totals["ips"] ?: d.ips
            Box(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                FieldLabel(if (d.truncated["ips"] == true) "Busiest $shown of ${count(total)} addresses" else "Addresses")
            }
        }
        items(d.topIps, key = { it.ip }) { row ->
            IpRow(row, onClick = { onOpenIp(row.ip) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
