package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.Port
import io.github.overcuriousity.peephole.net.Props
import io.github.overcuriousity.peephole.net.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpSectionsTest {
    @Test
    fun propChipsInOrder() {
        val props = Props(tor = true, net = "cloud", scanner = true, fleet = false, agent = true, scanned = false, scanPending = false)

        assertEquals(
            listOf("Tor exit", "cloud hosting", "scanner", "agent traces", "not scanned", "added by an operator"),
            propChips(props, unseen = true, isTorRelay = true),
        )
    }

    @Test
    fun propChipsWithoutProps() {
        assertEquals(listOf("Tor relay"), propChips(null, unseen = false, isTorRelay = true))
        assertTrue(propChips(null, unseen = false, isTorRelay = false).isEmpty())
        assertEquals(listOf("added by an operator"), propChips(null, unseen = true, isTorRelay = false))
    }

    @Test
    fun netKindsReadAsTheWebsAndUnknownStaysVerbatim() {
        fun net(kind: String) = propChips(Props(net = kind, scanned = true), unseen = false, isTorRelay = false)

        assertEquals(listOf("hosting"), net("hosting"))
        assertEquals(listOf("scanner network"), net("scanner"))
        assertEquals(listOf("eyeball"), net("fixed"))
        assertEquals(listOf("mobile"), net("mobile"))
        assertEquals(listOf("satellite"), net("satellite"))
    }

    @Test
    fun scanPendingReplacesNotScanned() {
        val chips = propChips(Props(scanned = false, scanPending = true, fleet = true), unseen = false, isTorRelay = false)

        assertEquals(listOf("fleet", "scan pending"), chips)
        assertFalse("not scanned" in chips)
    }

    @Test
    fun newestOpenPorts() {
        val older = ScanResult("2026-10-01T00:00:00Z", listOf(Port(22)))
        val newer = ScanResult("2026-10-10T00:00:00Z", listOf(Port(22), Port(80), Port(443)))

        assertEquals(3, newestOpenPorts(listOf(older, newer)))
        assertEquals(3, newestOpenPorts(listOf(newer, older)))
        assertNull(newestOpenPorts(emptyList()))
    }
}
