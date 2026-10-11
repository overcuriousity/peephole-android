package io.github.overcuriousity.peephole.net

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The /api/v1 client against a fake peephole over real TLS. */
class PeepholeApiTest {
    private val rootCa = HeldCertificate.Builder().certificateAuthority(0).build()
    private val serverCert = HeldCertificate.Builder()
        .addSubjectAlternativeName("localhost")
        .addSubjectAlternativeName("127.0.0.1")
        .signedBy(rootCa)
        .build()
    private val server = MockWebServer()
    private lateinit var base: OkHttpClient

    @Before
    fun setUp() {
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(serverCert, rootCa.certificate).build()
        server.useHttps(serverCerts.sslSocketFactory())
        server.start()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(rootCa.certificate).build()
        base = OkHttpClient.Builder().sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager).build()
    }

    @After
    fun tearDown() = server.close()

    private fun api(pin: String? = PeepholeHttp.spkiSha256(serverCert.certificate)) =
        PeepholeApi(server.url("/"), Secret(TOKEN), pin, base)

    @Test
    fun listSendsTheBearerTokenAndDecodesAPage() {
        server.enqueue(
            MockResponse(
                body = """{"items":[{"ip":"203.0.113.9","country":"DE","asn":6805,"asn_org":"Telefonica",""" +
                    """"is_tor":false,"first_seen":"2026-10-03T14:02:11Z","last_seen":"2026-10-10T08:41:57Z",""" +
                    """"request_count":1284,"max_severity":4,"abuse_score":null,"future_field":1}],"next_cursor":"eyJ"}""",
            ),
        )

        val r = api().ips("203.0.113.0/24", cursor = "c1")

        assertTrue(r is ApiResult.Ok)
        val page = (r as ApiResult.Ok).value
        assertEquals("eyJ", page.nextCursor)
        assertEquals(1284L, page.items.single().requestCount)
        assertEquals(4, page.items.single().maxSeverity)
        assertNull(page.items.single().abuseScore)
        val request = server.takeRequest()
        assertEquals("Bearer $TOKEN", request.headers["Authorization"])
        assertEquals("/api/v1/ips", request.url.encodedPath)
        assertEquals("203.0.113.0/24", request.url.queryParameter("q"))
        assertEquals("c1", request.url.queryParameter("cursor"))
        assertFalse("token must not appear in toString", r.toString().contains(TOKEN))
    }

    @Test
    fun anIpv6AddressIsOnePathSegment() {
        server.enqueue(MockResponse(code = 404, body = """{"error":{"code":"not_found","message":"no such address in the dataset"}}"""))

        val r = api().ip("2001:db8::1")

        assertEquals(ApiResult.Failed(404, "not_found", "no such address in the dataset", (r as ApiResult.Failed).detail), r)
        assertEquals(listOf("api", "v1", "ips", "2001:db8::1"), server.takeRequest().url.pathSegments)
    }

    @Test
    fun revokedAndReadOnlyAreTheirOwnResults() {
        server.enqueue(MockResponse(code = 401, body = """{"error":{"code":"unauthorized","message":"x"}}"""))
        server.enqueue(MockResponse(code = 403, body = """{"error":{"code":"forbidden","message":"x"}}"""))

        assertEquals(ApiResult.Revoked, api().ip("203.0.113.9"))
        assertEquals(ApiResult.NoAct, api().probe("203.0.113.9"))
    }

    @Test
    fun aFreshScanConflictKeepsItsDetail() {
        server.enqueue(
            MockResponse(
                code = 409,
                body = """{"error":{"code":"fresh","message":"a scan of this level is less than a day old",""" +
                    """"scan_id":7,"scanned_at":"2026-10-10T08:00:00Z"}}""",
            ),
        )

        val r = api().scanQuote("203.0.113.9", 3) as ApiResult.Failed

        assertEquals("fresh", r.code)
        assertEquals("\"2026-10-10T08:00:00Z\"", r.detail["scanned_at"].toString())
        assertEquals("3", server.takeRequest().url.queryParameter("level"))
    }

    @Test
    fun buyingSendsTheQuoteId() {
        server.enqueue(MockResponse(code = 202, body = """{"job_id":"s_42","credits":"1.28","credits_mc":1280}"""))

        val r = api().buyScan("203.0.113.9", "q_abc")

        assertEquals(ScanBought("s_42", "1.28", 1280), (r as ApiResult.Ok).value)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/ips/203.0.113.9/scan", request.url.encodedPath)
        assertEquals("""{"quote_id":"q_abc"}""", request.body!!.utf8())
    }

    @Test
    fun aProbeJobDecodesItsVantages() {
        server.enqueue(
            MockResponse(
                body = """{"job_id":"p_1","kind":"probe","ip":"203.0.113.9","status":"running","created_at":"2026-10-10T16:03:12Z",""" +
                    """"result":{"vantages":[{"node":"n1","name":"fra-1","state":"done","why":null,"rtt_ms":12,""" +
                    """"ports":[{"port":22,"protocol":"tcp","outcome":"open","detail":{"banner":"SSH-2.0"}}]},""" +
                    """{"node":"n2","name":"nyc-1","state":"declined","why":"busy","rtt_ms":null,"ports":[]}]}}""",
            ),
        )

        val job = (api().job("p_1") as ApiResult.Ok).value

        assertFalse(job.finished)
        assertNull(job.scan()?.scannedAt)
        val vantages = job.probe()!!.vantages
        assertEquals(listOf("done", "declined"), vantages.map { it.state })
        assertEquals(12L, vantages[0].rttMs)
        assertEquals("open", vantages[0].ports.single().outcome)
        assertEquals("busy", vantages[1].why)
    }

    @Test
    fun aScanJobWithoutAResultIsStillDecoded() {
        server.enqueue(MockResponse(body = """{"job_id":"s_1","kind":"scan","ip":"203.0.113.9","status":"queued","created_at":"x","result":null}"""))

        val job = (api().job("s_1") as ApiResult.Ok).value

        assertEquals("queued", job.status)
        assertNull(job.scan())
    }

    @Test
    fun pinMismatchSendsNoTokenAtAll() {
        server.enqueue(MockResponse(body = "{}"))
        val otherKeyPin = PeepholeHttp.spkiSha256(HeldCertificate.Builder().build().certificate)

        assertEquals(ApiResult.PinMismatch, api(otherKeyPin).ips("", null))
        assertEquals("no request may reach the server", 0, server.requestCount)
    }

    @Test
    fun lookupIpsOnlyBodyIsUnchanged() {
        server.enqueue(MockResponse(body = OLD_SERVER_LOOKUP))

        api().lookup(listOf("203.0.113.9"))

        assertEquals("""{"ips":["203.0.113.9"]}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun lookupWithNamesSendsBothKeys() {
        server.enqueue(MockResponse(body = OLD_SERVER_LOOKUP))

        api().lookup(ips = emptyList(), names = listOf("example.com"))

        assertEquals("""{"ips":[],"names":["example.com"]}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun lookupDecodesNameEntries() {
        // The `names` example of docs/api-v1.md §"Host names and URLs" (peephole PR #74), verbatim.
        server.enqueue(
            MockResponse(
                body = """{"results":[],"missing":[],"unreadable":[],"capped":false,
"names": [
  { "host": "example.com",
    "resolve": {
      "note": null,
      "votes": [ { "ip": "203.0.113.9", "agreed": true, "verdict": "agreed by 3 of 3" } ],
      "resolvers": ["alice (DE)", "bob (NL)", "carol (FR)"],
      "errors": [],
      "also": []
    },
    "results": [ { "ip": "203.0.113.9", "…": "the per-IP summary" } ],
    "missing": [] },
  { "host": "2001:db8::1", "results": [], "missing": ["2001:db8::1"] },
  { "host": "gone.example", "resolve": { "…": "…" },
    "error": { "code": "unresolved", "message": "No resolver answered; nothing was stored." } },
  { "error": { "code": "invalid", "message": "not an IP address, host name or URL" } }
]}""",
            ),
        )

        val names = (api().lookup(emptyList(), listOf("example.com")) as ApiResult.Ok).value.names!!

        assertEquals(4, names.size)
        assertTrue(names[0].resolve!!.votes[0].agreed)
        assertEquals("203.0.113.9", names[0].results.single().ip)
        assertEquals(listOf("2001:db8::1"), names[1].missing)
        assertEquals("unresolved", names[2].error!!.code)
        assertNull(names[3].host)
        assertEquals("invalid", names[3].error!!.code)
    }

    @Test
    fun oldServerResponseHasNullNames() {
        server.enqueue(MockResponse(body = OLD_SERVER_LOOKUP))

        assertNull((api().lookup(listOf("203.0.113.9"), listOf("example.com")) as ApiResult.Ok).value.names)
    }

    @Test
    fun newServerIpsOnlyHasEmptyNames() {
        server.enqueue(MockResponse(body = OLD_SERVER_LOOKUP.removeSuffix("}") + ""","names":[]}"""))

        assertEquals(emptyList<NameLookup>(), (api().lookup(listOf("203.0.113.9")) as ApiResult.Ok).value.names)
    }

    private companion object {
        const val TOKEN = "Zv9-secret-token-value-0123456789abcdefghij"
        const val OLD_SERVER_LOOKUP = """{"results":[],"missing":[],"unreadable":[],"capped":false}"""
    }
}
