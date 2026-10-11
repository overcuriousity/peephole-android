package io.github.overcuriousity.peephole.pairing

import io.github.overcuriousity.peephole.lookup.ScanOutcome
import io.github.overcuriousity.peephole.store.InstanceStore
import io.github.overcuriousity.peephole.store.KeyValue
import io.github.overcuriousity.peephole.store.TokenCipher
import io.github.overcuriousity.peephole.ui.ScanHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The scan → pair handoff (v2 §2.2): in memory only, straight to Confirm. */
class PairViewModelTest {
    private class MapKeyValue : KeyValue {
        val map = mutableMapOf<String, String>()

        override fun get(key: String) = map[key]

        override fun put(vararg entries: Pair<String, String?>) {
            for ((k, v) in entries) if (v == null) map.remove(k) else map[k] = v
        }
    }

    private object FakeCipher : TokenCipher {
        override fun encrypt(plain: String) = plain

        override fun decrypt(sealed: String) = sealed
    }

    private val payload = PairingPayload.of("https://203.0.113.5", "abcdefghijklmnop0123", null)!!

    @Test
    fun confirmScannedGoesStraightToConfirm() {
        val vm = PairViewModel(PairingClient(), InstanceStore(MapKeyValue(), FakeCipher), now = { 42L })

        vm.confirmScanned(payload)

        val state = vm.state.value as PairState.Confirm
        assertSame(payload, state.payload)
        assertEquals("https://203.0.113.5", state.payload.originText)
        assertEquals(42L, state.scannedAtMs)
    }

    @Test
    fun handoffIsReadOnce() {
        val handoff = ScanHandoff()
        val scanned = ScanOutcome.Pairing(payload)
        handoff.pending = scanned

        assertSame(scanned, handoff.take())
        assertNull(handoff.take())
    }
}
