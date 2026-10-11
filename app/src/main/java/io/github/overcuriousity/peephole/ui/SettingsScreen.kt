package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.store.InstanceStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: InstanceStore,
    id: String,
    onPairAnother: () -> Unit,
    onClose: () -> Unit,
    onUnpaired: (String) -> Unit = {},
) {
    // Captured once: after unpair the store no longer has it, but the
    // confirmation still needs its name.
    val instance = remember(id) { store.get(id) }
    var confirming by remember { mutableStateOf(false) }
    var removed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(instance?.instanceName ?: "Instance") },
                navigationIcon = { BackButton(onClose) },
            )
        },
    ) { padding ->
        if (instance == null) {
            Text("This instance is no longer paired.", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Label("Origin")
            Text(instance.origin, style = MonoMedium)
            Spacer(Modifier.height(Spacing.md))
            Label("Device name")
            Text(instance.deviceName)
            Spacer(Modifier.height(Spacing.md))
            Label("Paired")
            Text("${shortDate(instance.pairedAtEpochMs)} · ${scopeLabel(instance)}")
            if (instance.spkiSha256 != null) {
                Spacer(Modifier.height(Spacing.md))
                Label("Certificate pin (SPKI SHA-256)")
                Text(instance.spkiSha256, style = MonoMedium)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                "Permissions granted here are set by this instance's admin, not by this app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
            OutlinedButton(onClick = onPairAnother, modifier = Modifier.fillMaxWidth()) { Text("Pair another instance") }
            Spacer(Modifier.height(Spacing.sm))
            Button(
                onClick = { confirming = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Unpair device") }
        }
    }

    if (confirming && instance != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Unpair from ${instance.instanceName}?") },
            text = { Text("This deletes the pairing and its token from this phone. You can pair again later.") },
            confirmButton = {
                TextButton(onClick = {
                    store.remove(instance.id)
                    onUnpaired(instance.id)
                    confirming = false
                    removed = true
                }) { Text("Unpair") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
    if (removed && instance != null) {
        // api-v1 has no device self-revoke yet, so the server side always
        // still knows this device (design §6.9).
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Removed from this device") },
            text = {
                Text(
                    "${instance.instanceName} still lists it on its Devices page until an admin revokes it there.",
                )
            },
            confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "Back" }) { Text("←") }
}
