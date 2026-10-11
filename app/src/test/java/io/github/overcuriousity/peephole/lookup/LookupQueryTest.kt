package io.github.overcuriousity.peephole.lookup

import io.github.overcuriousity.peephole.ui.lookupEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plan's Decision 1 table, ported from the server's `urls_are_reduced_to_their_host`. */
class LookupQueryTest {
    private fun ip(a: String, extracted: Boolean) = Entry.Ip(a, extracted)
    private fun name(h: String, extracted: Boolean) = Entry.Name(h, extracted)

    @Test
    fun parsesTable() {
        val rows = listOf(
            "203.0.113.9" to ip("203.0.113.9", false),
            "2001:db8::1" to ip("2001:db8::1", false),
            "198.51.100.0/24" to Entry.Cidr("198.51.100.0/24"),
            "2001:db8::/32" to Entry.Cidr("2001:db8::/32"),
            "[2001:db8::1]" to ip("2001:db8::1", true),
            "http://[2001:db8::1]:8443/x" to ip("2001:db8::1", true),
            "https://u:p@[2001:db8::1]/" to ip("2001:db8::1", true),
            "http://203.0.113.9:8080/x" to ip("203.0.113.9", true),
            "203.0.113.9/login" to ip("203.0.113.9", true),
            "example.com" to name("example.com", false),
            " WWW.Example.COM. " to name("www.example.com", true),
            "https://example.com" to name("example.com", true),
            "https://example.com/" to name("example.com", true),
            "http://example.com:8080/a/b?q=1#f" to name("example.com", true),
            "example.com/path?q" to name("example.com", true),
            "example.com:443" to name("example.com", true),
            "HTTPS://Example.com?q=a" to name("example.com", true),
            "https://user:pw@example.com/x" to name("example.com", true),
            "ftp://anon@files.example.org" to name("files.example.org", true),
            "https://bücher.example/" to name("xn--bcher-kva.example", true),
            // Not java.net.IDN: no IDNA2003 nameprep, so ß is not rewritten to ss (as on the server).
            "faß.de" to name("xn--fa-hia.de", true),
            "例え.jp" to name("xn--r8jz45g.jp", true),
            "::ffff:203.0.113.9" to ip("::ffff:203.0.113.9", false),
            "" to Entry.Invalid(""),
            "localhost" to Entry.Invalid("localhost"),
            "http://" to Entry.Invalid("http://"),
            "https://example.com:http/" to Entry.Invalid("https://example.com:http/"),
            "http://[2001:db8::1" to Entry.Invalid("http://[2001:db8::1"),
            "http://[example.com]/" to Entry.Invalid("http://[example.com]/"),
            "ht tp://example.com" to Entry.Invalid("ht tp://example.com"),
            "not a host" to Entry.Invalid("not a host"),
            "1.2.3" to Entry.Invalid("1.2.3"),
            "a".repeat(2049) to Entry.Invalid("a".repeat(2049)),
            "\u0000abc" to Entry.Invalid("\u0000abc"),
            "203.0.113.9/33" to Entry.Invalid("203.0.113.9/33"),
            "a..com" to Entry.Invalid("a..com"),
            "-bad.example" to Entry.Invalid("-bad.example"),
            "1:2:3:4:5:6:7:8:9" to Entry.Invalid("1:2:3:4:5:6:7:8:9"),
            "2001:db8:::1" to Entry.Invalid("2001:db8:::1"),
            "01.2.3.4" to Entry.Invalid("01.2.3.4"),
        )
        for ((input, want) in rows) assertEquals(input.take(40), want, parseEntry(input))
    }

    @Test
    fun scanPairingWinsFirst() {
        val qr = """{"v":1,"origin":"https://peephole.example","code":"Zv9abcdefghijklmnop"}"""
        val r = classifyScan(qr)
        assertTrue(r.toString(), r is ScanOutcome.Pairing)
        assertEquals("https://peephole.example", (r as ScanOutcome.Pairing).payload.originText)
    }

    @Test
    fun scanMasksUserinfoInShownFrom() {
        assertEquals(
            ScanOutcome.LookupTarget("example.com", "https://•••@example.com/x"),
            classifyScan("https://user:pw@example.com/x"),
        )
        assertEquals(
            ScanOutcome.LookupTarget("2001:db8::1", "https://•••@[2001:db8::1]/"),
            classifyScan("https://u:p@[2001:db8::1]/"),
        )
        assertEquals(
            ScanOutcome.LookupTarget("example.com", "https://example.com:8443/path?x=1"),
            classifyScan("https://example.com:8443/path?x=1"),
        )
    }

    @Test
    fun scanBareHostHasNoShownFrom() {
        assertNull((classifyScan("example.com") as ScanOutcome.LookupTarget).shownFrom)
        assertEquals(ScanOutcome.LookupTarget("198.51.100.0/24", null), classifyScan("198.51.100.0/24"))
    }

    @Test
    fun scanGarbageIsUnreadableWithV2Reason() {
        assertEquals(
            ScanOutcome.Unreadable("hello world", "This isn't a web address, IP address, host name, or pairing code."),
            classifyScan("hello world"),
        )
        // Even unreadable, a scanned password is not shown.
        assertEquals("https://•••@example.com:http/", (classifyScan("https://u:pw@example.com:http/") as ScanOutcome.Unreadable).text)
    }

    @Test
    fun scanTooLongIsUnreadable() {
        assertTrue(classifyScan("a".repeat(2049)) is ScanOutcome.Unreadable)
        assertTrue(classifyScan("\u0000abc") is ScanOutcome.Unreadable)
    }

    @Test
    fun commaInUrlSplitsIntoAnInvalidTail() {
        assertEquals(
            listOf(Entry.Name("example.com", true), Entry.Invalid("b")),
            lookupEntries("https://example.com/?q=a,b").map(::parseEntry),
        )
    }
}
