package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.store.Instance
import io.github.overcuriousity.peephole.store.JobLog

private val Tabs = listOf("IPs" to "⌕", "Lookup" to "≡", "Jobs" to "◷")

/** One paired instance: IPs · Lookup · Jobs (design §2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceHome(
    api: PeepholeApi,
    instance: Instance,
    jobs: JobLog,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onOpenIp: (String) -> Unit,
    onOpenJob: (String) -> Unit,
    onRevoked: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(instance.instanceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = { TextButton(onClick = onSettings) { Text("Settings") } },
            )
        },
        bottomBar = {
            NavigationBar {
                Tabs.forEachIndexed { i, (label, glyph) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(glyph) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> IpListScreen(api, instance.instanceName, onOpenIp, onRevoked)
                1 -> LookupScreen(api, instance.instanceName, onOpenIp, onRevoked)
                else -> JobsTab(api, instance.id, jobs, onOpenJob, onRevoked)
            }
        }
    }
}
