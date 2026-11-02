package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.net.Member
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.store.Instance
import java.time.Instant
import java.time.ZoneOffset

/** Members a campaign shows before "Show all". */
private const val MEMBERS_FIRST = 50

/** A campaign (`/admin/evidence/campaigns/{key}`): its members, shapes, calendar, commands and events. Edges are only counted. */
@Composable
fun CampaignScreen(
    api: PeepholeApi,
    instance: Instance,
    key: String,
    onBack: () -> Unit,
    onOpenIp: (String) -> Unit,
    onOpenRequest: (Long) -> Unit,
    onRevoked: () -> Unit,
) {
    DossierScaffold(
        title = "Campaign $key",
        titleStyle = MaterialTheme.typography.titleLarge,
        instanceName = instance.instanceName,
        shareText = key,
        shareLabel = "Campaign key",
        load = { api.campaignDossier(key) },
        onBack = onBack,
        onRevoked = onRevoked,
    ) { d ->
        val c = d.campaign
        item {
            Section("Campaign #${c.num}") {
                Fact("Key", c.key, mono = true)
                Fact("Members", count(d.totals["members"] ?: c.size))
                Fact("Links", "${count(c.hardEdges)} hard · ${count(c.softEdges)} soft")
                Fact("First seen", c.firstSeen?.let(::utcTime))
                Fact("Last seen", c.lastSeen?.let(::utcTime))
                if (d.agent) Fact("Agent", "AI agent traces in its labels")
            }
            SectionDivider()
        }
        item {
            Section("Members") { Members(d.members, d.truncated["members"] == true, onOpenIp) }
            SectionDivider()
        }
        if (d.shapes.isNotEmpty()) {
            item {
                Section("Shapes", initiallyOpen = false) {
                    d.shapes.forEach { s ->
                        Text(s.text, style = MonoSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("${count(s.members)} members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SectionDivider()
            }
        }
        item {
            val today = remember(d.generatedAt) { (parseInstant(d.generatedAt) ?: Instant.now()).atZone(ZoneOffset.UTC).toLocalDate() }
            Section("Activity") { CalendarChart(remember(d.calendar, today) { calendarGrid(d.calendar, today) }) }
            SectionDivider()
        }
        if (d.commands.isNotEmpty()) {
            item {
                Section("Webshell commands", initiallyOpen = false) {
                    d.commands.forEach { cmd ->
                        Text(cmd.command, style = MonoSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${count(cmd.count)}× from ${count(cmd.ips)} addresses · last ${utcTime(cmd.lastAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SectionDivider()
            }
        }
        item { TimelineSection(d.events, d.truncated["events"] == true, onOpenRequest, showIp = true) }
    }
}

@Composable
private fun Members(members: List<Member>, truncated: Boolean, onOpenIp: (String) -> Unit) {
    var all by rememberSaveable { mutableStateOf(false) }
    Column {
        (if (all) members else members.take(MEMBERS_FIRST)).forEach { m ->
            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpenIp(m.ip) }.padding(vertical = 4.dp)) {
                Text("${m.ip} ›", style = MonoMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    listOfNotNull(
                        m.country,
                        m.asn?.let { "AS$it" + (m.asnOrg?.let { org -> " $org" } ?: "") },
                        "${count(m.requests)} reqs",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!all && members.size > MEMBERS_FIRST) TextButton(onClick = { all = true }) { Text("Show all ${members.size}") }
        if (truncated) Text("More members than the dossier carries.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
