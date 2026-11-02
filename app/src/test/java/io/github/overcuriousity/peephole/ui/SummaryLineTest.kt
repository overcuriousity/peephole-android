package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.IpSummary
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryLineTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z")

    @Test
    fun everyFactInOrder() {
        val row = IpSummary(
            ip = "203.0.113.9",
            country = "DE",
            asn = 6805,
            asnOrg = "Telefonica",
            requestCount = 12,
            isTorRelay = true,
            abuseScore = 40,
            unseen = true,
            lastSeen = "2026-10-20T10:00:00Z",
        )

        assertEquals(
            "DE · AS6805 Telefonica · 12 reqs · tor relay · abuse 40 · unseen · last seen ${ago("2026-10-20T10:00:00Z", now)}",
            summaryLine(row, now),
        )
    }

    @Test
    fun anExitIsNotAlsoCalledARelay() {
        assertEquals("1 reqs · tor exit", summaryLine(IpSummary(ip = "x", requestCount = 1, isTor = true, isTorRelay = true), now))
    }
}
