package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.lookup.LookupRequestPlan
import io.github.overcuriousity.peephole.lookup.planLookup
import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.LookupResult
import io.github.overcuriousity.peephole.net.PeepholeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Design §6.5: one field for a single address or a pasted list. The API
 * serves stored data only, so no provider is asked and nothing costs.
 */
@Composable
fun LookupScreen(api: PeepholeApi, instanceName: String, onOpenIp: (String) -> Unit, onRevoked: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ApiResult<LookupResult>?>(null) }
    var plan by remember { mutableStateOf<LookupRequestPlan?>(null) }
    val scope = rememberCoroutineScope()
    RevokedEffect(result, onRevoked)

    fun submit() {
        val entries = lookupEntries(input)
        if (entries.isEmpty() || busy) return
        val p = planLookup(entries)
        plan = p
        if (p.sendsNothing) {
            result = ApiResult.Ok(LookupResult())
            return
        }
        busy = true
        scope.launch {
            result = withContext(Dispatchers.IO) { api.lookup(p.ips, p.names) }
            busy = false
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Addresses or networks, one per line") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                Button(
                    onClick = ::submit,
                    enabled = !busy && lookupEntries(input).isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(if (busy) "Looking up…" else "Look up · stored data, no cost") }
            }
        }
        when (val r = result) {
            null -> {}
            // Unreadable entries are never sent; they are listed here as the server listed them in v1.
            // Name cards (nameRows over plan.names) come in S2.
            is ApiResult.Ok -> lookupRows(r.value.copy(unreadable = r.value.unreadable + plan?.invalid.orEmpty()), onOpenIp)
            else -> item { ErrorBanner(r.describe(instanceName), ::submit) }
        }
    }
}

private fun LazyListScope.lookupRows(r: LookupResult, onOpenIp: (String) -> Unit) {
    if (r.capped) {
        item { Note("More than 500 stored matches — showing the first 500.") }
    }
    if (r.results.isEmpty() && r.missing.isEmpty() && r.unreadable.isEmpty()) {
        item { EmptyState("Nothing stored matches.") }
    }
    items(r.results) { row ->
        IpRow(row, onClick = { onOpenIp(row.ip) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    items(r.missing) { addr ->
        ListItem(headlineContent = { Text(addr, style = Mono) }, supportingContent = { Text("— not stored") })
    }
    items(r.unreadable) { line ->
        ListItem(
            headlineContent = { Text(line, style = Mono) },
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
