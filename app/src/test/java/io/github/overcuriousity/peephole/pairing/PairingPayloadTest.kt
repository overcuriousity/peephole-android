package io.github.overcuriousity.peephole.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingPayloadTest {
    private val pin = "5LxKq1Bv6p0yX8vW3nQ2rT9uJ4mH7gF1eD0cB2aZ3Y8="

    private fun qr(origin: String = "https://ph.example.net", v: Int = 1, code: String = CODE, spki: String? = pin) =
        """{"v":$v,"origin":"$origin","code":"$code"""" +
            (spki?.let { ""","tls_spki_sha256":"$it"""" } ?: "") + "}"

    @Test
    fun parsesAValidCode() {
        val p = PairingPayload.parse(qr())!!
        assertEquals("https://ph.example.net", p.originText)
        assertEquals(CODE, p.code.value)
        assertEquals(pin, p.spkiSha256)
    }

    @Test
    fun pinIsOptional() {
        assertNull(PairingPayload.parse(qr(spki = null))!!.spkiSha256)
    }

    @Test
    fun keepsANonDefaultPort() {
        assertEquals("https://ph.example.net:8443", PairingPayload.parse(qr(origin = "https://ph.example.net:8443"))!!.originText)
    }

    @Test
    fun rejectsWhatIsNotAV1PairingCode() {
        assertNull(PairingPayload.parse("hello"))
        assertNull(PairingPayload.parse("https://ph.example.net"))
        assertNull(PairingPayload.parse(qr(v = 2)))
        assertNull(PairingPayload.parse("""{"v":1,"origin":"https://ph.example.net"}"""))
    }

    @Test
    fun rejectsANonHttpsOrNonBareOrigin() {
        assertNull(PairingPayload.parse(qr(origin = "http://ph.example.net")))
        assertNull(PairingPayload.parse(qr(origin = "https://ph.example.net/admin")))
        assertNull(PairingPayload.parse(qr(origin = "https://user:pw@ph.example.net")))
        assertNull(PairingPayload.parse(qr(origin = "https://ph.example.net/?x=1")))
    }

    @Test
    fun rejectsABadCodeOrPin() {
        assertNull(PairingPayload.parse(qr(code = "short")))
        assertNull(PairingPayload.parse(qr(code = "has spaces in it, not base64url")))
        assertNull(PairingPayload.parse(qr(spki = "AAAA")))
        assertNull(PairingPayload.parse(qr(spki = "not base64!")))
    }

    @Test
    fun codeIsRedactedInToString() {
        assertEquals("Secret(***)", PairingPayload.parse(qr())!!.code.toString())
    }

    private companion object {
        const val CODE = "uJ0cAbCdEfGhIjKlMnOpQrStUvWxYz01"
    }
}
