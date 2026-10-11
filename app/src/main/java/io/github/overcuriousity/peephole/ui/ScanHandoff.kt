package io.github.overcuriousity.peephole.ui

import androidx.lifecycle.ViewModel
import io.github.overcuriousity.peephole.lookup.ScanOutcome

/**
 * Carries one scan from the scanner to the screen that acts on it. Activity
 * scoped and in memory only: a pairing code never rides in a nav route or
 * saved state.
 */
class ScanHandoff : ViewModel() {
    var pending: ScanOutcome? = null

    /** The pending scan, once; it is cleared as it is read. */
    fun take(): ScanOutcome? = pending.also { pending = null }
}
