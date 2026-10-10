package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.ScanQuote
import io.github.overcuriousity.peephole.net.VantageOffer
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActLogicTest {
    private val received = Instant.parse("2026-10-10T16:00:00Z")

    @Test
    fun quoteDeadlineIsTwoMinutesLessMarginOnThePhoneClock() {
        assertEquals(Instant.parse("2026-10-10T16:01:55Z"), quoteDeadline(received))
    }

    @Test
    fun aPhoneClockFiveMinutesFastStillGetsALiveQuote() {
        // Instance time 16:00, quote expires_at 16:02; the phone reads 16:05.
        val phoneNow = Instant.parse("2026-10-10T16:05:00Z")
        val deadline = quoteDeadline(phoneNow)
        assertTrue(deadline.isAfter(phoneNow))
        assertEquals(115L, java.time.Duration.between(phoneNow, deadline).seconds)
    }

    @Test
    fun costShowsTheRangeOnlyWhenThereIsOne() {
        val q = ScanQuote("q", "1.28", 1280, "2.56", 2560, "2026-10-10T16:02:00Z")
        assertEquals("1.28–2.56 credits", costText(q))
        assertEquals("1.28 credits", costText(q.copy(creditsMax = "1.28")))
        assertEquals("0.00 credits", costText(q.copy(credits = "0.00", creditsMax = null)))
    }

    @Test
    fun theProbeButtonTotalsTheTickedVantagesAsTheInstancePrintsCredits() {
        val offers = listOf(
            VantageOffer("a", creditsMc = 20, default = true),
            VantageOffer("b", creditsMc = 30),
            VantageOffer("c", creditsMc = 1255, default = true),
        )
        assertEquals("Probe · 1.28 credits", probeButtonText(offers, setOf("a", "c")))
        assertEquals("Probe · 0.03 credits", probeButtonText(offers, setOf("b")))
        assertEquals("Probe · 0.00 credits", probeButtonText(offers, emptySet()))
        assertEquals("12.35", showCredits(12_345))
    }

    @Test
    fun freshConflictNamesWhenTheScanRan() {
        val e = ApiResult.Failed(
            409,
            "fresh",
            "x",
            JsonObject(mapOf("scanned_at" to JsonPrimitive("2026-10-10T08:00:00Z"))),
        )
        assertTrue(scanConflict(e), scanConflict(e).contains("2026-10-10 08:00 UTC"))
    }

    @Test
    fun unknownConflictKeepsTheInstanceMessage() {
        assertEquals("something new", scanConflict(ApiResult.Failed(409, "new_code", "something new")))
    }

    @Test
    fun pollingBacksOffFromTwoToFifteenSeconds() {
        assertEquals(2_000L, nextPollDelayMs(0))
        assertEquals(3_000L, nextPollDelayMs(2_000))
        assertEquals(15_000L, nextPollDelayMs(14_000))
    }

    @Test
    fun lookupSplitsOnLinesCommasAndSpaces() {
        assertEquals(
            listOf("203.0.113.9", "2001:db8::1", "198.51.100.0/24"),
            lookupEntries(" 203.0.113.9\n2001:db8::1, 198.51.100.0/24\n\n203.0.113.9 "),
        )
    }
}
