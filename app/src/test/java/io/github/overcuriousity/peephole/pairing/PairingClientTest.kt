package io.github.overcuriousity.peephole.pairing

import io.github.overcuriousity.peephole.net.PeepholeHttp
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pairing against a fake peephole over real TLS, with and without an SPKI pin. */
class PairingClientTest {
    private val rootCa = HeldCertificate.Builder().certificateAuthority(0).build()
    private val serverCert = HeldCertificate.Builder()
        .addSubjectAlternativeName("localhost")
        .addSubjectAlternativeName("127.0.0.1")
        .signedBy(rootCa)
        .build()
    private val server = MockWebServer()
    private lateinit var client: PairingClient

    @Before
    fun setUp() {
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(serverCert, rootCa.certificate).build()
        server.useHttps(serverCerts.sslSocketFactory())
        server.start()
        // Trust the test CA the way the system store trusts a public one, so
        // ordinary PKI passes and only the pin decides.
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(rootCa.certificate).build()
        client = PairingClient(
            OkHttpClient.Builder().sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager).build(),
        )
    }

    @After
    fun tearDown() = server.close()

    private fun payload(pin: String?) =
        PairingPayload.of(server.url("/").toString(), CODE, pin) ?: error("test payload invalid")

    private val goodPin get() = PeepholeHttp.spkiSha256(serverCert.certificate)

    @Test
    fun pairsWhenThePinMatches() {
        server.enqueue(MockResponse(body = OK_BODY))

        val result = client.pair(payload(goodPin), "Pixel 8")

        assertTrue(result is PairResult.Paired)
        result as PairResult.Paired
        assertEquals(TOKEN, result.token.value)
        assertEquals("8f3c2f0e", result.deviceId)
        assertEquals(setOf("read"), result.scopes)
        assertEquals("ph.example.net", result.instanceName)
        assertFalse("token must not appear in toString", result.toString().contains(TOKEN))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/pair", request.url.encodedPath)
        val sent = request.body!!.utf8()
        assertTrue(sent.contains("\"code\":\"$CODE\""))
        assertTrue(sent.contains("\"device_name\":\"Pixel 8\""))
    }

    @Test
    fun pinMismatchAbortsBeforeSendingTheCode() {
        server.enqueue(MockResponse(body = OK_BODY))
        val otherKeyPin = PeepholeHttp.spkiSha256(HeldCertificate.Builder().build().certificate)

        val result = client.pair(payload(otherKeyPin), "Pixel 8")

        assertEquals(PairResult.PinMismatch, result)
        assertEquals("no request may reach the server", 0, server.requestCount)
    }

    @Test
    fun noPinFallsBackToPki() {
        server.enqueue(MockResponse(body = OK_BODY))

        assertTrue(client.pair(payload(null), "Pixel 8") is PairResult.Paired)
    }

    @Test
    fun deadCodeIs401() {
        server.enqueue(MockResponse(code = 401, body = """{"error":{"code":"unauthorized","message":"x"}}"""))

        assertEquals(PairResult.CodeDead, client.pair(payload(goodPin), "Pixel 8"))
    }

    @Test
    fun rateLimitCarriesRetryAfter() {
        server.enqueue(MockResponse(code = 429, headers = headersOf("Retry-After", "42")))

        assertEquals(PairResult.RateLimited(42), client.pair(payload(goodPin), "Pixel 8"))
    }

    @Test
    fun otherErrorsKeepTheInstanceMessage() {
        server.enqueue(MockResponse(code = 400, body = """{"error":{"code":"invalid","message":"device_name too long"}}"""))

        assertEquals(PairResult.Rejected(400, "device_name too long"), client.pair(payload(goodPin), "Pixel 8"))
    }

    private companion object {
        const val CODE = "uJ0cAbCdEfGhIjKlMnOpQrStUvWxYz01"
        const val TOKEN = "Zv9-secret-token-value-0123456789abcdefghij"
        const val OK_BODY =
            """{"token":"$TOKEN","device_id":"8f3c2f0e","scopes":["read"],"instance_name":"ph.example.net"}"""
    }
}
