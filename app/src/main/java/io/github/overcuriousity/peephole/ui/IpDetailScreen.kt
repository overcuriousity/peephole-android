package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.net.ApiResult
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import io.github.overcuriousity.peephole.net.IpDetail
import io.github.overcuriousity.peephole.net.IpDossier
import io.github.overcuriousity.peephole.net.IpLoad
import io.github.overcuriousity.peephole.net.loadIp
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.net.ScanResult
import io.github.overcuriousity.peephole.store.Instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Design §6.4, v0.4 spec §2: the web IP page's order, from the address's
 * dossier (0.23+). On an older server the plain record shows, without the
 * dossier's sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpDetailScreen(
    api: PeepholeApi,
    instance: Instance,
    addr: String,
    onBack: () -> Unit,
    onProbe: () -> Unit,
    onScan: (level: Int) -> Unit,
    onOpenAsn: (Long) -> Unit,
    onOpenCampaign: (String) -> Unit,
    onOpenRequest: (Long) -> Unit,
    onRevoked: () -> Unit,
) {
    var load by remember { mutableStateOf<IpLoad?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(addr, attempt) {
        load = null
        load = withContext(Dispatchers.IO) { api.loadIp(addr) }
    }
    RevokedEffect((load as? IpLoad.Error)?.result, onRevoked)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(addr, style = MonoMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    val raw = (load as? IpLoad.Full)?.dossier?.raw
                    ShareMenu(text = { addr }, json = raw?.let { r -> { r } })
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            when (val l = load) {
                null -> item { SkeletonRows(4) }
                IpLoad.NotStored -> {
                    item { IpHeader(addr, null, null, onOpenAsn, onOpenCampaign) }
                    item {
                        Text(
                            "Not recorded here: not in the dataset.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                is IpLoad.Error -> item { ErrorBanner(l.result.describe(instance.instanceName), { attempt++ }, EdgeBanner) }
                is IpLoad.Plain -> ipItems(api, instance, l.detail, null, onProbe, onScan, onOpenAsn, onOpenCampaign, onOpenRequest)
                is IpLoad.Full -> {
                    val d = l.dossier.value
                    ipItems(api, instance, d.detail(), d, onProbe, onScan, onOpenAsn, onOpenCampaign, onOpenRequest)
                }
            }
        }
    }
}

private fun LazyListScope.ipItems(
    api: PeepholeApi,
    instance: Instance,
    ip: IpDetail,
    d: IpDossier?,
    onProbe: () -> Unit,
    onScan: (Int) -> Unit,
    onOpenAsn: (Long) -> Unit,
    onOpenCampaign: (String) -> Unit,
    onOpenRequest: (Long) -> Unit,
) {
    item { IpHeader(ip.ip, ip, d, onOpenAsn, onOpenCampaign) }
    item { Tiles(ip) }
    item { SignalStrip(signalsOf(ip)) }
    if (d == null) {
        item {
            Text(
                "This instance runs peephole older than 0.23: activity, timeline and verdicts need 0.23.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    if (ip.labels.isNotEmpty() || ip.families.isNotEmpty()) {
        item {
            Section("What it was after") {
                if (ip.labels.isNotEmpty()) Text(ip.labels.joinToString(" · "))
                ip.families.forEach { Text("${it.name} · ${count(it.count)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SectionDivider()
        }
    }
    d?.evidence?.let { v -> item { VerdictSection("Evidence", v); SectionDivider() } }
    d?.sharing?.let { v -> item { VerdictSection("Address sharing", v); SectionDivider() } }
    if (d != null) {
        item { ActivitySection(d.week, d.calendar, d.generatedAt); SectionDivider() }
        item { TimelineSection(d.events, d.truncated["events"] == true, onOpenRequest); SectionDivider() }
    }
    item { ProviderAnswers(ip); SectionDivider() }
    if (ip.names.isNotEmpty() || ip.neighbours != null) item { NamesAndNeighbours(ip); SectionDivider() }
    if (d != null && d.probes.isNotEmpty()) item { ProbeHistorySection(d.probes); SectionDivider() }
    item {
        Section("Scans") {
            if (ip.scans.isEmpty()) Text("No scans stored.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ip.scans.forEach { ScanBlock(it) }
            ScanPicker(api, ip.ip, enabled = instance.canAct, onScan = onScan)
        }
        SectionDivider()
    }
    item {
        Section("Probes") {
            ActionButton("Probe this address", enabled = instance.canAct, reason = READ_ONLY, onClick = onProbe)
        }
    }
    if (d != null) item { LuresSection(d.uploads, d.commands, d.canaries) }
    item { Spacer(Modifier.height(Spacing.lg)) }
}

@Composable
private fun Tiles(ip: IpDetail) {
    val big = MaterialTheme.typography.titleLarge
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Requests", Modifier.weight(1f)) { Text(count(ip.requestCount), style = big) }
            Tile("Rank", Modifier.weight(1f)) { Text(ip.rank?.let { "#$it" } ?: "—", style = big) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Max sev.", Modifier.weight(1f)) {
                if (ip.severity == null) Text("—", style = big) else SeverityChip(ip.severity)
            }
            Tile("Open ports", Modifier.weight(1f)) { Text(newestOpenPorts(ip.scans)?.toString() ?: "—", style = big) }
        }
    }
}

/** Each provider's newest stored answer, raw (the signals strip above reads them). */
@Composable
private fun ProviderAnswers(ip: IpDetail) {
    Section("Provider answers", initiallyOpen = false) {
        if (ip.intel.isEmpty()) Text("No stored enrichment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ip.intel.toSortedMap().forEach { (provider, intel) ->
            Column(Modifier.padding(bottom = 12.dp)) {
                Text(provider, style = MaterialTheme.typography.titleSmall)
                if (intel == null) {
                    Text("no answer stored", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("checked ${ago(intel.fetchedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    scalarFields(intel.data).forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun NamesAndNeighbours(ip: IpDetail) {
    Section("Names and neighbours", initiallyOpen = false) {
        ip.names.forEach { n ->
            Text(n.name, style = MonoMedium)
            Text(
                n.source + if (n.agreed) " · resolvers agree" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ip.neighbours?.let { nb ->
            Spacer(Modifier.height(Spacing.sm))
            Text("${nb.net ?: "network"}: ${count(nb.otherIps)} other addresses · ${count(nb.sameAsn)} in the same ASN")
        }
    }
}

@Composable
private fun Tile(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            FieldLabel(label)
            value()
        }
    }
}

@Composable
fun ScanBlock(scan: ScanResult) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(
            "${utcTime(scan.scannedAt)} · ${scan.ports.size} open port${if (scan.ports.size == 1) "" else "s"}",
            style = MaterialTheme.typography.titleSmall,
        )
        scan.ports.forEach { p ->
            Text(
                listOfNotNull("${p.port}", p.service, p.product).joinToString("  "),
                style = MonoMedium,
            )
        }
    }
}

/**
 * Level picker (design §6.4): one level per cost-confirm, never a combined buy.
 * Each level shows its price range, fetched when the menu opens; the confirm
 * screen's quote stays the binding price. Without prices (an older instance,
 * no scanner) the levels show bare.
 */
@Composable
private fun ScanPicker(api: PeepholeApi, addr: String, enabled: Boolean, onScan: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var prices by remember(addr) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { api.scanPrices(addr) }
        prices = if (r is ApiResult.Ok) levelPrices(r.value) else emptyMap()
    }
    Box {
        ActionButton("Run a counter-scan ▾", enabled = enabled, reason = READ_ONLY) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (1..5).forEach { level ->
                DropdownMenuItem(
                    text = { Text("Level $level") },
                    trailingIcon = {
                        prices[level]?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                    onClick = {
                        open = false
                        onScan(level)
                    },
                )
            }
        }
    }
}

/** The rule between IP page sections. */
@Composable
fun SectionDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** Top-level scalar fields of a provider's stored answer, enough to read without a JSON viewer. */
private fun scalarFields(data: Any?, max: Int = 8): List<Pair<String, String>> =
    (data as? JsonObject).orEmpty().entries
        .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it !is JsonNull && it.content.isNotBlank() }?.let { k to it.content } }
        .take(max)
