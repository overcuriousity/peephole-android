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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import io.github.overcuriousity.peephole.net.IpDetail
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.net.ScanResult
import io.github.overcuriousity.peephole.store.Instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Design §6.4: identity and verdict first, raw evidence last. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpDetailScreen(
    api: PeepholeApi,
    instance: Instance,
    addr: String,
    onBack: () -> Unit,
    onProbe: () -> Unit,
    onScan: (level: Int) -> Unit,
    onRevoked: () -> Unit,
) {
    var result by remember { mutableStateOf<ApiResult<IpDetail>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(addr, attempt) {
        result = null
        result = withContext(Dispatchers.IO) { api.ip(addr) }
    }
    RevokedEffect(result, onRevoked)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(addr, style = MonoMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            when (val r = result) {
                null -> SkeletonRows(4)
                is ApiResult.Ok -> Detail(api, r.value, instance, onProbe, onScan)
                else -> if (r is ApiResult.Failed && r.status == 404) {
                    Identity(addr, null)
                    Text(
                        "Not recorded here: not in the dataset.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    ErrorBanner(r.describe(instance.instanceName), { attempt++ }, EdgeBanner)
                }
            }
        }
    }
}

@Composable
private fun Detail(api: PeepholeApi, ip: IpDetail, instance: Instance, onProbe: () -> Unit, onScan: (Int) -> Unit) {
    Identity(ip.ip, ip)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile("Requests", Modifier.weight(1f)) { Text(count(ip.requestCount), style = MaterialTheme.typography.titleLarge) }
        Tile("Rank", Modifier.weight(1f)) { Text(ip.rank?.let { "#$it" } ?: "—", style = MaterialTheme.typography.titleLarge) }
        Tile("Max sev.", Modifier.weight(1f)) {
            if (ip.severity == null) Text("—", style = MaterialTheme.typography.titleLarge) else SeverityChip(ip.severity)
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    if (ip.labels.isNotEmpty() || ip.families.isNotEmpty()) {
        Section("Verdict") {
            if (ip.labels.isNotEmpty()) Text(ip.labels.joinToString(" · "))
            ip.families.forEach { Text("${it.name} · ${count(it.count)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Divider()
    }
    Section("Enrichment") {
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
    Divider()
    if (ip.names.isNotEmpty() || ip.neighbours != null) {
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
        Divider()
    }
    Section("Scans") {
        if (ip.scans.isEmpty()) Text("No scans stored.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ip.scans.forEach { ScanBlock(it) }
        ScanPicker(api, ip.ip, enabled = instance.canAct, onScan = onScan)
    }
    Divider()
    Section("Probes") {
        ActionButton("Probe this address", enabled = instance.canAct, reason = READ_ONLY, onClick = onProbe)
    }
}

@Composable
private fun Identity(addr: String, ip: IpDetail?) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        SelectionContainer { Text(addr, style = MonoMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize)) }
        if (ip != null) {
            val place = listOfNotNull(
                ip.geo.country,
                ip.geo.asn?.let { "AS$it" + (ip.geo.asnOrg?.let { org -> " $org" } ?: "") },
            ).joinToString(" · ")
            if (place.isNotEmpty()) Text(place)
            if (ip.isTor) Text("Tor exit", color = MaterialTheme.colorScheme.tertiary)
            ip.firstSeen?.let { Text("first seen ${utcTime(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ip.lastSeen?.let { Text("last seen ${utcTime(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** Top-level scalar fields of a provider's stored answer, enough to read without a JSON viewer. */
private fun scalarFields(data: Any?, max: Int = 8): List<Pair<String, String>> =
    (data as? JsonObject).orEmpty().entries
        .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it !is JsonNull && it.content.isNotBlank() }?.let { k to it.content } }
        .take(max)
