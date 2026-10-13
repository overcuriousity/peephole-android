package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.Declined
import io.github.overcuriousity.peephole.net.FreshIntel
import io.github.overcuriousity.peephole.net.FreshLookup
import io.github.overcuriousity.peephole.net.IpSummary
import io.github.overcuriousity.peephole.net.LookupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshLookupTest {
    private fun failed(status: Int, code: String) = ApiResult.Failed(status, code, "server says $code")

    @Test
    fun everySpend409SaysQuoteAgainAndNothingWasCharged() {
        for (code in listOf("price_changed", "quote_invalid", "quote_mismatch", "something_new")) {
            val text = freshLookupProblem(failed(409, code), "lab")
            assertTrue(text, text.contains("quote again"))
            assertTrue(text, text.contains("Nothing was charged"))
        }
        assertTrue(freshLookupProblem(failed(409, "price_changed"), "lab").startsWith("Price changed"))
    }

    @Test
    fun aReadOnlyDeviceIsToldItCannotSpend() {
        assertTrue(freshLookupProblem(ApiResult.NoAct, "lab").contains("can't spend"))
        assertFalse(canQuoteAgain(ApiResult.NoAct))
    }

    @Test
    fun anOldServerIsToldApartFromAMissingAddress() {
        val text = freshLookupProblem(failed(404, "not_found"), "lab")
        assertTrue(text, text.contains("too old"))
        assertFalse(canQuoteAgain(failed(404, "not_found")))
    }

    @Test
    fun nothingToBuyIsSaidPlainly() {
        assertEquals("No provider is offered for this address right now.", freshLookupProblem(failed(409, "no_provider"), "lab"))
        assertTrue(canQuoteAgain(failed(409, "price_changed")))
        assertTrue(canQuoteAgain(ApiResult.Unreachable("timeout")))
    }

    @Test
    fun onlyASingleExactAddressOffersAFreshLookup() {
        assertEquals("203.0.113.9", freshTarget(LookupResult(missing = listOf("203.0.113.9"))))
        assertEquals("203.0.113.9", freshTarget(LookupResult(results = listOf(IpSummary(ip = "203.0.113.9")))))
        assertNull(freshTarget(LookupResult(missing = listOf("203.0.113.9", "203.0.113.10"))))
        assertNull(freshTarget(LookupResult(missing = listOf("203.0.113.0/24"))))
        assertNull(freshTarget(LookupResult()))
    }

    @Test
    fun theOutcomeListsWhatWasAskedAndCharged() {
        val r = FreshLookup(
            ip = "203.0.113.9",
            credits = "0.10",
            intel = mapOf("abuseipdb" to FreshIntel("fresh"), "maxmind" to FreshIntel("stored")),
            declined = listOf(Declined("shodan", "bob", "no key")),
            kept = true,
        )
        assertEquals(
            listOf("Charged 0.10 credits.", "Asked now: abuseipdb", "Already fresh: maxmind", "Declined: shodan (no key)", "Kept in the dataset."),
            freshOutcome(r),
        )
    }
}
