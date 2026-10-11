package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.R
import io.github.overcuriousity.peephole.lookup.ScanOutcome
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.store.Instance
import io.github.overcuriousity.peephole.store.InstanceStore
import io.github.overcuriousity.peephole.store.JobLog

private val Tabs = listOf("Lookup" to R.drawable.ic_search, "IPs" to R.drawable.ic_dns, "Jobs" to R.drawable.ic_history)

/**
 * Home (v2 §1): Lookup · IPs · Jobs for the active instance ([InstanceStore.lastUsedId]),
 * switched with the chip in the app bar. With nothing paired only the lookup
 * box shows, disabled, with Scan QR still working (v2 §1.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceHome(
    store: InstanceStore,
    apiFor: (String) -> PeepholeApi?,
    jobs: JobLog,
    handoff: ScanHandoff,
    onScan: () -> Unit,
    onPair: () -> Unit,
    onManage: () -> Unit,
    onSettings: (String) -> Unit,
    onOpenIp: (Instance, String) -> Unit,
    onOpenJob: (Instance, String) -> Unit,
    onRevoked: (Instance) -> Unit,
) {
    val instances by store.instances.collectAsState()
    val lastUsed by store.lastUsed.collectAsState()
    // lastUsedId resolves the stored id against the list (gone → newest).
    val active = remember(instances, lastUsed) { store.lastUsedId?.let { id -> instances.firstOrNull { it.id == id } } }
    val api = remember(active?.id) { active?.let { apiFor(it.id) } }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf<ScanOutcome?>(null) }
    LaunchedEffect(Unit) {
        handoff.take()?.let {
            scan = it
            tab = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("peephole") },
                actions = {
                    AssistChip(
                        onClick = { sheet = true },
                        label = {
                            Text(
                                active?.instanceName ?: "No instance",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp),
                            )
                        },
                        trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more), null) },
                    )
                    if (active != null) TextButton(onClick = { onSettings(active.id) }) { Text("Settings") }
                },
            )
        },
        bottomBar = {
            if (active != null) {
                NavigationBar {
                    Tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(painterResource(icon), null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Results and lists belong to one instance; a switch starts them afresh.
            key(active?.id) {
                if (active == null || api == null) {
                    LookupScreen(null, null, scan, { scan = null }, onScan, {}, {})
                } else {
                    when (tab) {
                        0 -> LookupScreen(
                            api,
                            active.instanceName,
                            scan,
                            { scan = null },
                            onScan,
                            { onOpenIp(active, it) },
                            { onRevoked(active) },
                        )
                        1 -> IpListScreen(api, active.instanceName, { onOpenIp(active, it) }, { onRevoked(active) })
                        else -> JobsTab(api, active.id, jobs, { onOpenJob(active, it) }, { onRevoked(active) })
                    }
                }
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(
                        "Instances",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                    )
                    TextButton(onClick = { sheet = false }, modifier = Modifier.align(Alignment.CenterEnd)) { Text("Close") }
                }
                instances.forEach { instance ->
                    InstanceRow(instance, selected = instance.id == active?.id) {
                        store.lastUsedId = instance.id
                        sheet = false
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                TextButton(onClick = { sheet = false; onPair() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Pair another instance")
                }
                TextButton(onClick = { sheet = false; onManage() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Manage instances →")
                }
            }
        }
    }
}
