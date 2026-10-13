package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.R
import io.github.overcuriousity.peephole.lookup.LookupRequestPlan
import io.github.overcuriousity.peephole.lookup.ScanOutcome
import io.github.overcuriousity.peephole.lookup.nameRows
import io.github.overcuriousity.peephole.lookup.planLookup
import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.LookupResult
import io.github.overcuriousity.peephole.net.PeepholeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Design §6.5, v2 §3: one field for an address, network, host name, URL or
 * a pasted list, and Scan QR. The API serves stored data only (names are
 * resolved, not bought), so no provider is asked and nothing costs; a
 * single-address result offers a fresh provider lookup, quoted first. With no
 * [api] (nothing paired, v2 §1.1) the field is disabled and only Scan QR works.
 */
@Composable
fun LookupScreen(
    api: PeepholeApi?,
    instanceName: String?,
    scan: ScanOutcome?,
    onScanConsumed: () -> Unit,
    onScan: () -> Unit,
    onOpenIp: (String) -> Unit,
    onRevoked: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ApiResult<LookupResult>?>(null) }
    var plan by remember { mutableStateOf<LookupRequestPlan?>(null) }
    // Scanned text stays in memory: never saved state, never a route.
    var fromQr by remember { mutableStateOf<String?>(null) }
    var unreadable by remember { mutableStateOf<ScanOutcome.Unreadable?>(null) }
    var fresh by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    RevokedEffect(result, onRevoked)

    fun submit() {
        val entries = lookupEntries(input)
        if (api == null || entries.isEmpty() || busy) return
        val p = planLookup(entries)
        plan = p
        if (p.sendsNothing) {
            result = ApiResult.Ok(LookupResult())
            return
        }
        busy = true
        result = null
        scope.launch {
            result = withContext(Dispatchers.IO) { api.lookup(p.ips, p.names) }
            busy = false
        }
    }

    // v2 §2.2: a lookup QR fills the field and runs; an unreadable one only says why.
    LaunchedEffect(scan) {
        when (scan) {
            null -> return@LaunchedEffect
            is ScanOutcome.LookupTarget -> {
                input = scan.query
                fromQr = scan.shownFrom
                unreadable = null
                submit()
            }
            is ScanOutcome.Unreadable -> {
                unreadable = scan
                fromQr = null
            }
            is ScanOutcome.Pairing -> {}
        }
        onScanConsumed()
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        fromQr = null
                    },
                    label = { Text("IP, CIDR, host name, URL, or a pasted list") },
                    enabled = api != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                if (api == null) {
                    Text("Pair an instance to look things up.", modifier = Modifier.padding(top = 8.dp))
                    Text(
                        "No instance paired yet — scan a pairing QR from an instance's Devices page, or a lookup QR with a URL on it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Button(
                    onClick = ::submit,
                    enabled = api != null && !busy && lookupEntries(input).isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(
                        when {
                            !busy -> "Look up · stored data, no cost"
                            plan?.names.isNullOrEmpty() -> "Looking up…"
                            else -> "Resolving…"
                        },
                    )
                }
                FilledTonalButton(onClick = onScan, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(painterResource(R.drawable.ic_qr_code_scanner), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Scan QR")
                }
            }
        }
        fromQr?.let { item { Banner("From QR: $it") } }
        unreadable?.let { item { UnreadableBanner(it) } }
        val p = plan
        // v2 §3.2: a name waits on DNS, not a dataset read, so it says so.
        if (busy && p != null) {
            items(p.names) { host -> ListItem(headlineContent = { Text(host, style = MonoMedium) }, supportingContent = { Text("Resolving…") }) }
        }
        when (val r = result) {
            null -> {}
            is ApiResult.Ok -> {
                val names = p?.let { nameRows(it.names, r.value) }.orEmpty()
                items(names) { row -> NameResultCard(row, instanceName.orEmpty(), onOpenIp) }
                // Unreadable entries are never sent; they are listed below the names as the server listed them in v1.
                lookupRows(r.value.copy(unreadable = r.value.unreadable + p?.invalid.orEmpty()), names.isNotEmpty(), onOpenIp)
                // MIK-216: asking providers is opt-in, per address, and quoted before any spend.
                if (api != null && names.isEmpty()) {
                    freshTarget(r.value)?.let { addr ->
                        item {
                            OutlinedButton(onClick = { fresh = addr }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Fresh lookup · asks providers, shows the cost first")
                            }
                        }
                    }
                }
            }
            else -> item { ErrorBanner(r.describe(instanceName.orEmpty()), ::submit, EdgeBanner) }
        }
    }
    val freshAddr = fresh
    if (api != null && freshAddr != null) {
        FreshLookupSheet(api, instanceName.orEmpty(), freshAddr, onDone = { spent ->
            fresh = null
            if (spent) submit()
        }, onRevoked = onRevoked)
    }
}

@Composable
private fun Banner(text: String) {
    Text(
        text,
        style = MonoMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** v2 §2.2: the scanned text verbatim (mono, 6 lines until "show more") and why nothing was looked up. */
@Composable
private fun UnreadableBanner(scan: ScanOutcome.Unreadable) {
    var expanded by remember(scan) { mutableStateOf(false) }
    var overflows by remember(scan) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            scan.text,
            style = MonoMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "show less" else "show more") }
        }
        Text("${scan.reason} Nothing was looked up.", color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

private fun LazyListScope.lookupRows(r: LookupResult, hasNames: Boolean, onOpenIp: (String) -> Unit) {
    if (r.capped) {
        item { Note("More than 500 stored matches — showing the first 500.") }
    }
    if (!hasNames && r.results.isEmpty() && r.missing.isEmpty() && r.unreadable.isEmpty()) {
        item { EmptyState("Nothing stored matches.") }
    }
    items(r.results) { row ->
        IpRow(row, onClick = { onOpenIp(row.ip) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    items(r.missing) { addr ->
        ListItem(headlineContent = { Text(addr, style = MonoMedium) }, supportingContent = { Text("— not stored") })
    }
    items(r.unreadable) { line ->
        ListItem(
            headlineContent = { Text(line, style = MonoMedium) },
            supportingContent = { Text("unreadable: neither an address nor a network", color = MaterialTheme.colorScheme.error) },
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/** Lines, commas or spaces separate entries; blanks and repeats are dropped. */
fun lookupEntries(input: String): List<String> =
    input.split(Regex("[\\s,;]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
