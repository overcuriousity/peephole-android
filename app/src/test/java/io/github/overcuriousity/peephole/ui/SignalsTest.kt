package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.Intel
import io.github.overcuriousity.peephole.net.IpDetail
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** peephole `src/admin/signals.rs`, read from the raw provider answers api-v1 carries. */
class SignalsTest {
    private fun ip(isTor: Boolean = false, isTorRelay: Boolean = false, vararg intel: Pair<String, String?>) = IpDetail(
        ip = "203.0.113.9",
        isTor = isTor,
        isTorRelay = isTorRelay,
        intel = intel.associate { (k, v) -> k to v?.let { Intel(fetchedAt = "2026-10-20T00:00:00Z", data = Json.parseToJsonElement(it)) } },
    )

    private fun shown(s: List<Signal>) = s.map { "${it.label}=${it.value} ${it.tone}" }

    @Test
    fun serverCases() {
        val s = signalsOf(
            ip(
                false,
                false,
                "maxmind-geolite2" to """{"country":"DE"}""",
                "rdap" to """{"name":"MSFT","type":"DIRECT ALLOCATION","fresh":true}""",
                "shodan-internetdb" to """{"ports":[22,80,443],"vulns":["CVE-1","CVE-2"]}""",
                "shodan" to """{"ports":null,"os":null}""",
                "abuseipdb" to """{"score":87}""",
            ),
        )

        assertEquals(
            listOf(
                "Tor exit=no Ok",
                "Abuse=87/100 Bad",
                "Shodan=nothing known Ok",
                "InternetDB=3 open ports Info",
                "CVEs=2 Bad",
                "Registry=MSFT · DIRECT ALLOCATION Info",
                "Block=new, under 90 days Warn",
            ),
            shown(s),
        )
        assertEquals(87, s[1].meter)
    }

    @Test
    fun lowScoreAndMissing() {
        val s = signalsOf(ip(false, false, "abuseipdb" to """{"score":0}""", "shodan" to null, "someday-provider" to """{"x":1}"""))

        assertEquals(listOf("Tor exit=no Ok", "Abuse=0/100 Ok"), shown(s))
    }

    @Test
    fun torExitAndRelay() {
        assertEquals("Tor exit=listed Bad", shown(signalsOf(ip(isTor = true, isTorRelay = true))).first())
        assertEquals(listOf("Tor relay=listed Warn"), shown(signalsOf(ip(isTor = false, isTorRelay = true))))
    }

    @Test
    fun abuseBoundaries() {
        fun abuse(score: Int) = signalsOf(ip(false, false, "abuseipdb" to """{"score":$score}"""))[1]

        assertEquals(Tone.Ok, abuse(24).tone)
        assertEquals(Tone.Warn, abuse(25).tone)
        assertEquals(Tone.Warn, abuse(74).tone)
        assertEquals(Tone.Bad, abuse(75).tone)
        assertEquals(100, abuse(150).meter)
    }

    @Test
    fun singularPortAndNoCves() {
        val s = signalsOf(ip(false, false, "shodan" to """{"ports":[22],"vulns":[]}"""))

        assertEquals(listOf("Tor exit=no Ok", "Shodan=1 open port Info"), shown(s))
    }

    @Test
    fun rdapWithoutHolderOrFreshAddsNothing() {
        assertEquals(listOf("Tor exit=no Ok"), shown(signalsOf(ip(false, false, "rdap" to """{"range":"x","fresh":false}"""))))
    }
}
