package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.ScanQuote
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActLogicTest {
    private val received = Instant.parse("2026-10-10T16:00:00Z")

    @Test
    fun quoteDeadlineIsTheStatedExpiry() {
        assertEquals(Instant.parse("2026-10-10T16:01:30Z"), quoteDeadline("2026-10-10T16:01:30Z", received))
    }

    @Test
    fun aSlowPhoneClockCannotStretchAQuote() {
        // The instance's clock says the quote lives an hour from our "now".
        assertEquals(Instant.parse("2026-10-10T16:02:00Z"), quoteDeadline("2026-10-10T17:00:00Z", received))
    }

    @Test
    fun anUnreadableExpiryIsAlreadyExpired() {
        assertEquals(received, quoteDeadline("soon", received))
    }

    @Test
    fun costShowsTheRangeOnlyWhenThereIsOne() {
        val q = ScanQuote("q", "1.28", 1280, "2.56", 2560, "2026-10-10T16:02:00Z")
        assertEquals("1.28–2.56 credits", costText(q))
        assertEquals("1.28 credits", costText(q.copy(creditsMax = "1.28")))
        assertEquals("0.00 credits", costText(q.copy(credits = "0.00", creditsMax = null)))
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
