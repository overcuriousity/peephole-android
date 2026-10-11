package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.IpSummary
import io.github.overcuriousity.peephole.net.PeepholeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Design §6.3: paged list and search over the instance's dataset. */
@Composable
fun IpListScreen(api: PeepholeApi, instanceName: String, onOpenIp: (String) -> Unit, onRevoked: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val items = remember { mutableStateListOf<IpSummary>() }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<ApiResult<*>?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(cursor: String?) {
        loading = true
        error = null
        when (val r = withContext(Dispatchers.IO) { api.ips(submitted, cursor) }) {
            is ApiResult.Ok -> {
                if (cursor == null) items.clear()
                // Lazy keys must be unique even if pages overlap.
                val seen = items.mapTo(HashSet()) { it.ip }
                items += r.value.items.filter { seen.add(it.ip) }
                nextCursor = r.value.nextCursor
            }
            else -> error = r
        }
        loading = false
    }

    LaunchedEffect(submitted) { load(cursor = null) }
    RevokedEffect(error, onRevoked)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search IP, network or prefix") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitted = query.trim() }),
            trailingIcon = {
                if (submitted.isNotEmpty()) {
                    TextButton(onClick = { query = ""; submitted = "" }) { Text("Clear") }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        error?.let { e -> ErrorBanner(e.describe(instanceName)) { scope.launch { load(cursor = if (items.isEmpty()) null else nextCursor) } } }
        when {
            loading && items.isEmpty() -> SkeletonRows()
            !loading && error == null && items.isEmpty() ->
                if (submitted.isEmpty()) {
                    EmptyState("No addresses recorded yet.")
                } else {
                    EmptyState("Nothing matches $submitted.", "Clear search" to { query = ""; submitted = "" })
                }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.ip }) { row ->
                    IpRow(row, onClick = { onOpenIp(row.ip) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (nextCursor != null) {
                    item {
                        OutlinedButton(
                            onClick = { scope.launch { load(nextCursor) } },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) { Text(if (loading) "Loading…" else "Load more") }
                    }
                }
            }
        }
    }
}

/** Two-line row: address and severity, then country, ASN, request count, Tor, abuse score. */
@Composable
fun IpRow(row: IpSummary, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.ip, style = MonoMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.sm))
                SeverityChip(row.maxSeverity)
            }
        },
        supportingContent = { Text(summaryLine(row), color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

fun summaryLine(row: IpSummary): String = listOfNotNull(
    row.country,
    row.asn?.let { "AS$it" + (row.asnOrg?.let { org -> " $org" } ?: "") },
    "${count(row.requestCount)} reqs",
    if (row.isTor) "tor exit" else null,
    row.abuseScore?.let { "abuse $it" },
).joinToString(" · ")
