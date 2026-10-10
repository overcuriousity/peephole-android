package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.store.Instance
import io.github.overcuriousity.peephole.store.InstanceStore
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceListScreen(store: InstanceStore, onPair: () -> Unit, onOpen: (String) -> Unit) {
    val instances by store.instances.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("peephole") }) },
        floatingActionButton = {
            if (instances.isNotEmpty()) ExtendedFloatingActionButton(onClick = onPair) { Text("+ Pair") }
        },
    ) { padding ->
        if (instances.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No instances paired yet.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onPair, modifier = Modifier.padding(top = 16.dp)) { Text("Pair an instance") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(instances, key = { it.id }) { instance ->
                    InstanceRow(instance, onClick = { onOpen(instance.id) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun InstanceRow(instance: Instance, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(instance.instanceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    instance.origin,
                    style = Mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text("${scopeLabel(instance)} · paired ${shortDate(instance.pairedAtEpochMs)}")
            }
        },
    )
}

fun scopeLabel(instance: Instance): String = if (instance.canAct) "read + act" else "read-only"

fun shortDate(epochMs: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
