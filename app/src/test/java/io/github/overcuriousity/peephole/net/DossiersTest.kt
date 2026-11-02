package io.github.overcuriousity.peephole.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dossier shapes as peephole `docs/api-v1.md` § Dossiers documents them. */
class DossiersTest {
    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, text: String): T =
        PeepholeApi.json.decodeFromString(serializer, text)

    @Test
    fun ipDossierDecodesTheDocumentedShape() {
        val d = decode(
            IpDossier.serializer(),
            """
            {"dossier_v":1,"kind":"ip","id":"203.0.113.9","generated_at":"2026-10-20T09:00:00Z",
             "node":{"version":"0.23.2","window_days":0},"page":"/ip/203.0.113.9",
             "limits":{"events":200},"truncated":{"events":true,"uploads":false},"totals":{},
             "ip":"203.0.113.9","geo":{"country":"DE","asn":6805,"asn_org":"Telefonica"},
             "is_tor":false,"is_tor_relay":true,"unseen":true,"severity":3,"request_count":12,
             "labels":["wp-login"],"intel":{"abuseipdb":{"fetched_at":"2026-10-19T00:00:00Z","data":{"score":87}},"shodan":null},
             "week":[{"ts":"2026-10-20T08:00:00Z","count":3,"by_severity":[1,0,0,2,0]},{"ts":"2026-10-20T09:00:00Z","count":1,"by_severity":[1,0,0,0,0]}],
             "calendar":[{"day":"2026-10-20","count":4,"max_severity":3}],
             "events":[{"kind":"request","at":"2026-10-20T09:01:00Z","id":991,"severity":3,"summary":null,"reference":null,
                        "method":"POST","path":"/wp-login.php","query":null,"labels":["wp-login"],"owasp":["A07"],
                        "canary_served":false,"canary_used":null,"canary_from":null}],
             "probes":[{"group":"g1","asked_at":"2026-10-19T10:00:00Z","members":[{"vantage":"fra-1","state":"done","why":null,
                        "rtt_ms":21,"started_at":"2026-10-19T10:00:01Z","ports":[{"port":22,"protocol":"tcp","outcome":"open"}]}]}],
             "campaign":{"key":"198.51.100.7","num":3},"agent":false,
             "props":{"tor":false,"net":"cloud","scanner":true,"fleet":false,"agent":false,"scanned":true,"scan_pending":false},
             "evidence":{"label":"Compromised device","sentence":"It answered as a camera.","caveat":"A reading, not proof.",
                         "lines":[{"kind":"banner","what":"Hikvision","port":80,"source":"scan","date":"2026-10-18"}]},
             "sharing":null,
             "uploads":[],"commands":[{"request_id":991,"at":"2026-10-20T09:01:00Z","command":"id"}],
             "canaries":{"used_elsewhere":1,"used_from":0,"reuses":[{"kind":"aws","place":"env","served_request_id":5,
                         "served_ip":"203.0.113.9","served_at":"2026-10-01T00:00:00Z","served_answer":"env","used_request_id":9,
                         "used_ip":"192.0.2.4","used_at":"2026-10-02T00:00:00Z","delta_s":86400,"same_source":false}]},
             "future_field":{"a":1}}
            """.trimIndent(),
        )

        assertEquals(3L, d.campaign!!.num)
        assertEquals("cloud", d.props!!.net)
        assertEquals(listOf(1L, 0L, 0L, 2L, 0L), d.week[0].bySeverity)
        assertEquals("request", d.events.single().kind)
        assertEquals(991L, d.events.single().id)
        assertEquals("fra-1", d.probes.single().members.single().vantage)
        assertEquals(80, d.evidence!!.lines.single().port)
        assertNull(d.sharing)
        assertEquals(86400L, d.canaries.reuses.single().deltaS)
        assertEquals(true, d.truncated["events"])
        assertEquals("2026-10-20T09:00:00Z", d.generatedAt)
        val detail = d.detail()
        assertTrue(detail.isTorRelay)
        assertTrue(detail.unseen)
        assertEquals(6805L, detail.geo.asn)
        assertEquals(12L, detail.requestCount)
        assertEquals(setOf("abuseipdb", "shodan"), detail.intel.keys)
    }

    @Test
    fun minimalIpDossierDecodes() {
        val d = decode(IpDossier.serializer(), """{"dossier_v":1,"kind":"ip","id":"203.0.113.9","ip":"203.0.113.9"}""")

        assertNull(d.props)
        assertNull(d.evidence)
        assertNull(d.campaign)
        assertTrue(d.events.isEmpty())
        assertTrue(d.week.isEmpty())
        assertTrue(d.canaries.reuses.isEmpty())
    }

    @Test
    fun asnCampaignAndRequestDossiersDecode() {
        val asn = decode(
            AsnDossier.serializer(),
            """{"dossier_v":1,"kind":"asn","id":"6805","asn":6805,"org":"Telefonica","ips":120,"requests":5000,"max_severity":4,
               "first_seen":"2026-01-01T00:00:00Z","last_seen":"2026-10-20T00:00:00Z","labels":[{"label":"wp-login","ips":40}],
               "open_ports":[{"port":22,"ips":7}],"campaigns":[{"key":"198.51.100.7","num":3,"ips":2}],
               "top_ips":[{"ip":"203.0.113.9","request_count":12}],"truncated":{"ips":true},"totals":{"ips":120}}""",
        )
        assertEquals("203.0.113.9", asn.topIps.single().ip)
        assertEquals(120L, asn.totals["ips"])
        assertEquals(22, asn.openPorts.single().port)

        val campaign = decode(
            CampaignDossier.serializer(),
            """{"dossier_v":1,"kind":"campaign","id":"198.51.100.7",
               "campaign":{"key":"198.51.100.7","num":3,"size":2,"first_seen":"2026-10-01T00:00:00Z","last_seen":"2026-10-20T00:00:00Z",
                           "hard_edges":1,"soft_edges":0,"v":2,"computed_at":"2026-10-20T00:00:00Z"},
               "members":[{"ip_id":1,"ip":"198.51.100.7","requests":4},{"ip_id":2,"ip":"203.0.113.9","country":"DE","requests":12}],
               "edges":[{"a":"198.51.100.7","b":"203.0.113.9","kind":"ja4"}],"shapes":[{"text":"GET /.env","members":2,"df":1,"first_seen":"x","wave":null}],
               "calendar":[],"uploads":[],"commands":[{"command":"id","count":3,"ips":2,"last_at":"2026-10-20T00:00:00Z"}],
               "events":[{"kind":"request","at":"2026-10-20T00:00:00Z","id":5,"ip":"203.0.113.9","labels":[],"owasp":[],"canary_served":false}],
               "agent":false}""",
        )
        assertEquals(2, campaign.members.size)
        assertEquals(1L, campaign.campaign.hardEdges)
        assertEquals("203.0.113.9", campaign.events.single().ip)
        assertEquals(1, campaign.edges.size)

        val request = decode(
            RequestDossier.serializer(),
            """{"dossier_v":1,"kind":"request","id":"991",
               "request":{"id":991,"at":"2026-10-20T09:01:00Z","ip":"203.0.113.9","method":"POST","target":"/wp-login.php",
                          "severity":3,"scan_level":0,"is_fp_claim":false,"labels":["wp-login"],"owasp":[],
                          "headers":[["Host","x"],["User-Agent","curl/8"]],"body_text":"log=admin","body_b64":"","body_len":9,"body_truncated":false},
               "ip":{"ip":"203.0.113.9"},"same_ip":[{"id":990,"at":"2026-10-20T09:00:00Z","ip":"203.0.113.9","method":"GET","path":"/","severity":0}],
               "same_ja4":{"ips":1,"days":7,"recent":[{"id":12,"at":"2026-10-19T00:00:00Z","ip":"192.0.2.4","method":"GET","path":"/","severity":1}]},
               "canaries":{"served":[],"reuses":[]},"uploads":[],"webshell_cmd":null,"campaign":null}""",
        )
        assertEquals(listOf("Host", "x"), request.request.headers[0])
        assertEquals(1, request.sameJa4.recent.size)
        assertEquals("log=admin", request.request.bodyText)
        assertEquals("203.0.113.9", request.ip!!.ip)
    }
}
