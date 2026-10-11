package io.github.overcuriousity.peephole.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.overcuriousity.peephole.lookup.ScanOutcome
import io.github.overcuriousity.peephole.pairing.PairState
import io.github.overcuriousity.peephole.pairing.PairViewModel
import io.github.overcuriousity.peephole.pairing.PairingClient
import io.github.overcuriousity.peephole.store.InstanceStore
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(store: InstanceStore, client: PairingClient, handoff: ScanHandoff, onClose: () -> Unit) {
    val vm = viewModel { PairViewModel(client, store) }
    val state by vm.state.collectAsState()
    var manual by remember { mutableStateOf(false) }

    // A pairing QR scanned on Home (v2 §2.2) skips the camera here.
    LaunchedEffect(Unit) {
        (handoff.pending as? ScanOutcome.Pairing)?.let {
            handoff.take()
            vm.confirmScanned(it.payload)
        }
    }

    // Back from confirm returns to the scanner, not out of pairing (design §6.2).
    BackHandler(enabled = state is PairState.Confirm || manual) {
        if (manual) manual = false else vm.backToScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state is PairState.Confirm) "Confirm pairing" else "Pair an instance") },
                navigationIcon = { BackButton(onClose) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                is PairState.Scan ->
                    if (manual) {
                        ManualEntry(onSubmit = { o, c, p -> vm.manual(o, c, p).also { ok -> if (ok) manual = false } })
                    } else {
                        ScanStep(s.message, onScanned = vm::scanned, onManual = { manual = true })
                    }
                is PairState.Confirm -> ConfirmStep(s, onConfirm = vm::confirm, onCancel = vm::backToScan)
                is PairState.PinMismatch -> {
                    ErrorBanner(
                        "${s.origin} is not presenting the certificate this QR code was made for. Nothing was stored.",
                    )
                    Button(onClick = vm::backToScan) { Text("Back to scan") }
                }
                is PairState.Paired -> {
                    Text("Paired with ${s.instanceName}.", style = MaterialTheme.typography.titleMedium, color = Success)
                    Button(onClick = onClose) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun ScanStep(message: String?, onScanned: (String) -> Unit, onManual: () -> Unit) {
    message?.let { ErrorBanner(it) }
    Viewfinder(
        onScanned = onScanned,
        hint = "Point the camera at the QR code on your peephole admin's pairing page (Admin → Devices).",
        deniedText = "peephole needs the camera only to read the pairing QR code. Nothing is recorded or sent.",
        manual = "Can't scan it? Enter the code manually →" to onManual,
    )
}

/**
 * The v1 §6.2 Step 1 viewfinder: asks for the camera, then shows [QrScanner]
 * and [hint]; on a denial, [deniedText] and a way to settings. [manual] is an
 * optional way around the camera (label, action).
 */
@Composable
internal fun Viewfinder(
    onScanned: (String) -> Unit,
    hint: String,
    deniedText: String,
    manual: Pair<String, () -> Unit>? = null,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCamera(context)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    when {
        granted -> {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) { QrScanner(onScanned, Modifier.fillMaxWidth().aspectRatio(1f)) }
            Text(hint)
            manual?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
        }
        denied -> {
            Text(deniedText)
            manual?.let { (_, onClick) -> Button(onClick = onClick) { Text("Enter the code manually") } }
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text("Open settings") }
        }
        else -> Text("Starting camera…")
    }
}

@Composable
private fun ManualEntry(onSubmit: (String, String, String) -> Boolean) {
    var origin by remember { mutableStateOf("https://") }
    var code by remember { mutableStateOf("") }
    var spki by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Text("Copy these from the instance's pairing page.")
    OutlinedTextField(origin, { origin = it }, label = { Text("Origin") }, singleLine = true, textStyle = MonoMedium, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(code, { code = it }, label = { Text("Pairing code") }, singleLine = true, textStyle = MonoMedium, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        spki,
        { spki = it },
        label = { Text("TLS SPKI SHA-256 (if shown)") },
        singleLine = true,
        textStyle = MonoMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    if (invalid) ErrorBanner("That isn't a valid https origin, pairing code and (optional) pin.")
    Button(onClick = { invalid = !onSubmit(origin, code, spki) }) { Text("Continue") }
}

@Composable
private fun ConfirmStep(state: PairState.Confirm, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var deviceName by remember { mutableStateOf(defaultDeviceName(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val retryAt = state.retryAtMs
    if (retryAt != null) {
        LaunchedEffect(retryAt) {
            while (System.currentTimeMillis() < retryAt) {
                now = System.currentTimeMillis()
                delay(1000)
            }
            now = System.currentTimeMillis()
        }
    }
    val waitSeconds = retryAt?.let { ((it - now + 999) / 1000).coerceAtLeast(0) } ?: 0
    val nameOk = deviceName.trim().length in 1..80

    Text("You're about to pair with:")
    // The bare origin, never a display name: the QR is untrusted input and
    // this is where a human catches an unexpected host.
    Text(state.payload.originText, style = MonoMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
    OutlinedTextField(
        deviceName,
        { deviceName = it.take(80) },
        label = { Text("Device name shown there") },
        singleLine = true,
        isError = !nameOk,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "This instance's admin chose this pairing's permissions (read, or read + act).",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.payload.spkiSha256 != null) Text("🔒 Certificate pinned", color = Success)
    state.error?.let { ErrorBanner(it) }
    Spacer(Modifier.height(Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, enabled = !state.busy) { Text("Cancel") }
        Button(onClick = { onConfirm(deviceName.trim()) }, enabled = !state.busy && nameOk && waitSeconds == 0L) {
            Text(
                when {
                    waitSeconds > 0 -> "Retry in $waitSeconds s"
                    state.error != null -> "Retry"
                    else -> "Confirm"
                },
            )
        }
        if (state.busy) CircularProgressIndicator(Modifier.height(24.dp))
    }
}

private fun hasCamera(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun defaultDeviceName(context: Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
        ?: Build.MODEL
