package io.github.overcuriousity.peephole.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Dossiers of peephole `docs/api-v1.md` (server 0.23+): one versioned object
// per entity, everything its admin page shows. Defaults everywhere, so a
// minimal or newer dossier still decodes. Attacker text (paths, headers,
// bodies, commands, summaries) is only ever shown as plain text.

/** A decoded dossier and the response text it came from, for Share. */
data class Dossier<T>(val value: T, val raw: String)

/** `GET /ips/{addr}/dossier`: every [IpDetail] field plus the page's charts, timeline and verdicts. */
@Serializable
data class IpDossier(
    @SerialName("generated_at") val generatedAt: String? = null,
    val truncated: Map<String, Boolean> = emptyMap(),
    val ip: String = "",
    val geo: Geo = Geo(),
    @SerialName("is_tor") val isTor: Boolean = false,
    @SerialName("is_tor_relay") val isTorRelay: Boolean = false,
    val unseen: Boolean = false,
    val severity: Int? = null,
    @SerialName("first_seen") val firstSeen: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("request_count") val requestCount: Long = 0,
    val labels: List<String> = emptyList(),
    val families: List<Family> = emptyList(),
    val intel: Map<String, Intel?> = emptyMap(),
    val scans: List<ScanResult> = emptyList(),
    val names: List<HostName> = emptyList(),
    val rank: Long? = null,
    val neighbours: Neighbours? = null,
    val week: List<Bucket> = emptyList(),
    val calendar: List<CalendarDay> = emptyList(),
    val events: List<Event> = emptyList(),
    val probes: List<ProbeGroup> = emptyList(),
    val campaign: CampaignRef? = null,
    val agent: Boolean = false,
    val props: Props? = null,
    val evidence: Verdict? = null,
    val sharing: Verdict? = null,
    val uploads: List<Upload> = emptyList(),
    val commands: List<Command> = emptyList(),
    val canaries: IpCanaries = IpCanaries(),
) {
    /** The `GET /ips/{addr}` part, for the sections both shapes share. */
    fun detail(): IpDetail = IpDetail(
        ip = ip,
        geo = geo,
        isTor = isTor,
        isTorRelay = isTorRelay,
        unseen = unseen,
        severity = severity,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        requestCount = requestCount,
        labels = labels,
        families = families,
        intel = intel,
        scans = scans,
        names = names,
        rank = rank,
        neighbours = neighbours,
    )
}

/** One hour of the week chart; [bySeverity] has five counts, severity 0..4. */
@Serializable
data class Bucket(
    val ts: String = "",
    val count: Long = 0,
    @SerialName("by_severity") val bySeverity: List<Long> = emptyList(),
)

/** One day of the calendar; [day] is `YYYY-MM-DD` UTC. */
@Serializable
data class CalendarDay(
    val day: String = "",
    val count: Long = 0,
    @SerialName("max_severity") val maxSeverity: Int? = null,
)

/** One Timeline row. For `request` events [id] is the request id. */
@Serializable
data class Event(
    val kind: String = "",
    val at: String = "",
    val id: Long = 0,
    /** The member it is of (campaign dossiers only). */
    val ip: String? = null,
    val severity: Int? = null,
    val summary: String? = null,
    val reference: String? = null,
    val method: String? = null,
    val path: String? = null,
    val query: String? = null,
    val labels: List<String> = emptyList(),
    val owasp: List<String> = emptyList(),
    @SerialName("canary_served") val canaryServed: Boolean = false,
    @SerialName("canary_used") val canaryUsed: Long? = null,
    @SerialName("canary_from") val canaryFrom: Long? = null,
)

@Serializable
data class ProbeGroup(
    val group: String = "",
    @SerialName("asked_at") val askedAt: String? = null,
    val members: List<ProbeMember> = emptyList(),
)

@Serializable
data class ProbeMember(
    val vantage: String = "",
    val state: String = "",
    val why: String? = null,
    @SerialName("rtt_ms") val rttMs: Long? = null,
    @SerialName("started_at") val startedAt: String? = null,
    val ports: List<ProbePort> = emptyList(),
)

@Serializable
data class CampaignRef(val key: String = "", val num: Long = 0)

/** The property strip. [net] is hosting, cloud, scanner, fixed or mobile. */
@Serializable
data class Props(
    val tor: Boolean = false,
    val net: String? = null,
    val scanner: Boolean = false,
    val fleet: Boolean = false,
    val agent: Boolean = false,
    val scanned: Boolean = false,
    @SerialName("scan_pending") val scanPending: Boolean = false,
)

/** The evidence verdict or the shared-address reading. */
@Serializable
data class Verdict(
    val label: String = "",
    val sentence: String = "",
    val note: String? = null,
    val caveat: String? = null,
    val lines: List<VerdictLine> = emptyList(),
)

@Serializable
data class VerdictLine(
    val kind: String = "",
    val what: String = "",
    val port: Int? = null,
    val source: String = "",
    val date: String = "",
)

/** Upload metadata; the contents never leave the instance. */
@Serializable
data class Upload(
    val uid: String = "",
    val at: String? = null,
    val ip: String = "",
    val kind: String = "",
    val filename: String? = null,
    @SerialName("sniffed_type") val sniffedType: String = "",
    val size: Long = 0,
    val truncated: Boolean = false,
    val sha256: String = "",
    @SerialName("answered_path") val answeredPath: String = "",
    @SerialName("content_state") val contentState: String = "",
    @SerialName("request_id") val requestId: Long? = null,
)

/** A command sent to a dropped webshell. */
@Serializable
data class Command(
    @SerialName("request_id") val requestId: Long = 0,
    val at: String? = null,
    val command: String = "",
)

@Serializable
data class IpCanaries(
    @SerialName("used_elsewhere") val usedElsewhere: Long = 0,
    @SerialName("used_from") val usedFrom: Long = 0,
    val reuses: List<Reuse> = emptyList(),
)

@Serializable
data class Reuse(
    val kind: String = "",
    val place: String = "",
    @SerialName("served_request_id") val servedRequestId: Long? = null,
    @SerialName("served_ip") val servedIp: String = "",
    @SerialName("served_at") val servedAt: String? = null,
    @SerialName("used_request_id") val usedRequestId: Long = 0,
    @SerialName("used_ip") val usedIp: String = "",
    @SerialName("used_at") val usedAt: String? = null,
    @SerialName("delta_s") val deltaS: Long = 0,
    @SerialName("same_source") val sameSource: Boolean = false,
)

/** `GET /asns/{n}/dossier`. */
@Serializable
data class AsnDossier(
    val asn: Long = 0,
    val org: String? = null,
    val ips: Long = 0,
    val requests: Long = 0,
    @SerialName("max_severity") val maxSeverity: Int? = null,
    @SerialName("first_seen") val firstSeen: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    val labels: List<LabelCount> = emptyList(),
    @SerialName("open_ports") val openPorts: List<PortCount> = emptyList(),
    val campaigns: List<CampaignCount> = emptyList(),
    @SerialName("top_ips") val topIps: List<IpSummary> = emptyList(),
    val truncated: Map<String, Boolean> = emptyMap(),
    val totals: Map<String, Long> = emptyMap(),
)

@Serializable
data class LabelCount(val label: String = "", val ips: Long = 0)

@Serializable
data class PortCount(val port: Int = 0, val ips: Long = 0)

@Serializable
data class CampaignCount(val key: String = "", val num: Long = 0, val ips: Long = 0)

/** `GET /campaigns/{key}/dossier`. */
@Serializable
data class CampaignDossier(
    @SerialName("generated_at") val generatedAt: String? = null,
    val campaign: CampaignInfo = CampaignInfo(),
    val members: List<Member> = emptyList(),
    /** Only counted; the app draws no graph. */
    val edges: List<JsonElement> = emptyList(),
    val shapes: List<Shape> = emptyList(),
    val calendar: List<CalendarDay> = emptyList(),
    val uploads: List<Upload> = emptyList(),
    val commands: List<CampaignCommand> = emptyList(),
    val events: List<Event> = emptyList(),
    val agent: Boolean = false,
    val truncated: Map<String, Boolean> = emptyMap(),
    val totals: Map<String, Long> = emptyMap(),
)

@Serializable
data class CampaignInfo(
    val key: String = "",
    val num: Long = 0,
    val size: Long = 0,
    @SerialName("first_seen") val firstSeen: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("hard_edges") val hardEdges: Long = 0,
    @SerialName("soft_edges") val softEdges: Long = 0,
)

@Serializable
data class Member(
    val ip: String = "",
    val country: String? = null,
    val asn: Long? = null,
    @SerialName("asn_org") val asnOrg: String? = null,
    @SerialName("first_seen") val firstSeen: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    val requests: Long = 0,
)

@Serializable
data class Shape(val text: String = "", val members: Long = 0, @SerialName("first_seen") val firstSeen: String? = null)

@Serializable
data class CampaignCommand(
    val command: String = "",
    val count: Long = 0,
    val ips: Long = 0,
    @SerialName("last_at") val lastAt: String? = null,
)

/** `GET /requests/{id}/dossier`. */
@Serializable
data class RequestDossier(
    val request: RequestInfo = RequestInfo(),
    val ip: IpSummary? = null,
    @SerialName("same_ip") val sameIp: List<Related> = emptyList(),
    @SerialName("same_ja4") val sameJa4: SameJa4 = SameJa4(),
    val uploads: List<Upload> = emptyList(),
    @SerialName("webshell_cmd") val webshellCmd: String? = null,
    val campaign: CampaignRef? = null,
    val truncated: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class RequestInfo(
    val id: Long = 0,
    val at: String? = null,
    val ip: String = "",
    val method: String = "",
    val target: String = "",
    val host: String? = null,
    val answer: String? = null,
    val decoy: String? = null,
    val status: Int? = null,
    @SerialName("held_ms") val heldMs: Long? = null,
    val persona: String? = null,
    val transport: String? = null,
    @SerialName("via_proxy") val viaProxy: Boolean? = null,
    val severity: Int = 0,
    val labels: List<String> = emptyList(),
    val owasp: List<String> = emptyList(),
    val ja4: String? = null,
    val ja4h: String? = null,
    @SerialName("user_agent") val userAgent: String? = null,
    /** `[name, value]` pairs in received order. */
    val headers: List<List<String>> = emptyList(),
    @SerialName("body_text") val bodyText: String = "",
    @SerialName("body_b64") val bodyB64: String = "",
    @SerialName("body_len") val bodyLen: Long = 0,
    @SerialName("body_truncated") val bodyTruncated: Boolean = false,
)

/** Another request: of the same address, or with the same JA4. */
@Serializable
data class Related(
    val id: Long = 0,
    val at: String? = null,
    val ip: String = "",
    val method: String = "",
    val path: String = "",
    val severity: Int? = null,
)

@Serializable
data class SameJa4(val ips: Long = 0, val days: Long = 0, val recent: List<Related> = emptyList())

/** What the IP page can show for an address. */
sealed interface IpLoad {
    data class Full(val dossier: Dossier<IpDossier>) : IpLoad

    /** A server older than 0.23: no dossier route, the plain record only. */
    data class Plain(val detail: IpDetail) : IpLoad

    data object NotStored : IpLoad

    data class Error(val result: ApiResult<Nothing>) : IpLoad
}

/**
 * The dossier, or on its 404 the plain record: a second 404 means the
 * address is not in the dataset, an answer means an older server.
 */
fun ipLoad(dossier: ApiResult<Dossier<IpDossier>>, plain: () -> ApiResult<IpDetail>): IpLoad = when (dossier) {
    is ApiResult.Ok -> IpLoad.Full(dossier.value)
    is ApiResult.Failed if dossier.status == 404 -> when (val p = plain()) {
        is ApiResult.Ok -> IpLoad.Plain(p.value)
        is ApiResult.Failed if p.status == 404 -> IpLoad.NotStored
        else -> IpLoad.Error(p.nothing())
    }
    else -> IpLoad.Error(dossier.nothing())
}

/** Blocking; call off the main thread. */
fun PeepholeApi.loadIp(addr: String): IpLoad = ipLoad(ipDossier(addr)) { ip(addr) }

/** A non-Ok result, retyped. */
@Suppress("UNCHECKED_CAST")
private fun ApiResult<*>.nothing(): ApiResult<Nothing> {
    check(this !is ApiResult.Ok) { "Ok has a value" }
    return this as ApiResult<Nothing>
}
