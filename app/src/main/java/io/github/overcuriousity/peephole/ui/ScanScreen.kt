package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.lookup.ScanOutcome
import io.github.overcuriousity.peephole.lookup.classifyScan

/**
 * v2 §2.2: Home's full-screen scanner. Any QR is classified on decode and
 * handed over through [handoff]: a pairing goes to [onPairing], anything
 * else back to Home via [onLookup]. No confirm step: lookups cost nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(handoff: ScanHandoff, onPairing: () -> Unit, onLookup: () -> Unit, onClose: () -> Unit) {
    // The analyzer can report a second code before this screen leaves.
    var done by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Scan a QR code") }, navigationIcon = { BackButton(onClose) }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Viewfinder(
                onScanned = { text ->
                    if (!done) {
                        done = true
                        val outcome = classifyScan(text)
                        handoff.pending = outcome
                        if (outcome is ScanOutcome.Pairing) onPairing() else onLookup()
                    }
                },
                hint = "Point the camera at a pairing QR from an instance's Devices page, " +
                    "or at any QR with a web address, IP address or host name on it.",
                deniedText = "peephole needs the camera only to read QR codes. Nothing is recorded or sent.",
            )
        }
    }
}
