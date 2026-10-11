package io.github.overcuriousity.peephole.lookup

import io.github.overcuriousity.peephole.net.AddrVote
import io.github.overcuriousity.peephole.net.EntryError
import io.github.overcuriousity.peephole.net.LookupResult
import io.github.overcuriousity.peephole.net.NameLookup
import io.github.overcuriousity.peephole.net.Resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupPlanTest {
    @Test
    fun mixedPasteRoutesIpsCidrsAndNames() {
        val plan = planLookup(listOf("203.0.113.9", "198.51.100.0/24", "https://u:p@example.com/x", "example.com", "junk!"))

        assertEquals(listOf("203.0.113.9", "198.51.100.0/24"), plan.ips)
        assertEquals(listOf("example.com"), plan.names)
        assertEquals(listOf("junk!"), plan.invalid)
        assertFalse(plan.toString().contains("u:p"))
    }

    @Test
    fun invalidWithUserinfoIsNeverSent() {
        val raw = "https://u:pw@example.com:http/"
        val plan = planLookup(listOf(raw))

        assertEquals(listOf(raw), plan.invalid)
        assertTrue(plan.ips.isEmpty() && plan.names.isEmpty())
        assertTrue(plan.sendsNothing)
        assertFalse((plan.ips + plan.names).any { "u:pw" in it })
    }

    @Test
    fun sameHostTypedTwiceIsSentOnce() {
        val plan = planLookup(listOf("example.com", "https://Example.com/a", "http://203.0.113.9/", "203.0.113.9"))

        assertEquals(listOf("example.com"), plan.names)
        assertEquals(listOf("203.0.113.9"), plan.ips)
        assertFalse(plan.sendsNothing)
    }

    @Test
    fun elevenNamesAreAllSentServerCaps() {
        val hosts = (1..11).map { "h$it.example.com" }
        val plan = planLookup(hosts)
        assertEquals(11, plan.names.size)

        val answer = LookupResult(
            names = List(10) { NameLookup(host = hosts[it], resolve = agreed()) } +
                NameLookup(error = EntryError("capped", "past the 10th name; not resolved")),
        )
        val rows = nameRows(plan.names, answer)

        assertEquals(11, rows.size)
        assertTrue(rows.take(10).all { it is NameRow.Resolved })
        assertEquals(NameRow.Failed("h11.example.com", "capped", "past the 10th name; not resolved"), rows[10])
    }

    @Test
    fun oldServerMakesEveryNameTooOld() {
        val rows = nameRows(listOf("example.com", "www.example.com"), LookupResult())

        assertEquals(listOf(NameRow.TooOld("example.com"), NameRow.TooOld("www.example.com")), rows)
    }

    @Test
    fun invalidEntryIsLabelledWithTheSentHost() {
        val answer = LookupResult(names = listOf(NameLookup(error = EntryError("invalid", "not an IP address, host name or URL"))))

        assertEquals(
            listOf(NameRow.Failed("example.com", "invalid", "not an IP address, host name or URL")),
            nameRows(listOf("example.com"), answer),
        )
    }

    @Test
    fun aShortAnswerLeavesTheRestUnresolved() {
        val rows = nameRows(listOf("example.com", "www.example.com"), LookupResult(names = listOf(NameLookup(resolve = agreed()))))

        assertEquals("example.com", (rows[0] as NameRow.Resolved).host)
        assertEquals(NameRow.Failed("www.example.com", "unresolved", "No answer for this name."), rows[1])
    }

    @Test
    fun agreementLineUsesNoteElseCounts() {
        assertEquals("unverified — single resolver", agreementLine(Resolve(note = "unverified — single resolver")))
        assertEquals("agreed by 1 of 3 resolvers", agreementLine(agreed()))
    }

    @Test
    fun disputedWhenNoAgreedVote() {
        assertTrue(isDisputed(Resolve(votes = listOf(AddrVote("203.0.113.9", agreed = false)), resolvers = listOf("a", "b"))))
        assertTrue(isDisputed(agreed().copy(note = "disputed: resolvers differ")))
        assertFalse(isDisputed(agreed()))
    }

    private fun agreed() = Resolve(
        votes = listOf(AddrVote("203.0.113.9", agreed = true, verdict = "agreed by 1 of 3"), AddrVote("203.0.113.10")),
        resolvers = listOf("alice (DE)", "bob (NL)", "carol (FR)"),
    )
}
