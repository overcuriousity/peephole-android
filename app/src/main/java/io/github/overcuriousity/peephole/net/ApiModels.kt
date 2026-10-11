package io.github.overcuriousity.peephole.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Shapes of peephole `docs/api-v1.md`. Every field the app can live without
// has a default, so an older or newer instance still decodes.

/** One row of `GET /api/v1/ips` and of `POST /api/v1/lookup`'s `results`. */
@Serializable
data class IpSummary(
    val ip: String,
    val country: String? = null,
    val asn: Long? = null,
    @SerialName("asn_org") val asnOrg: String? = null,
    @SerialName("is_tor") val isTor: Boolean = false,
    @SerialName("first_seen") val firstSeen: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("request_count") val requestCount: Long = 0,
    @SerialName("max_severity") val maxSeverity: Int? = null,
    @SerialName("abuse_score") val abuseScore: Int? = null,
)

@Serializable
data class IpPage(val items: List<IpSummary> = emptyList(), @SerialName("next_cursor") val nextCursor: String? = null)

@Serializable
data class IpDetail(
    val ip: String,
    val geo: Geo = Geo(),
    @SerialName("is_tor") val isTor: Boolean = false,
    val severity: Int? = null,
    @SerialName("first_seen") val firstSeen: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("request_count") val requestCount: Long = 0,
    val labels: List<String> = emptyList(),
    val families: List<Family> = emptyList(),
    /** Newest stored answer per provider; null for a provider that never answered. */
    val intel: Map<String, Intel?> = emptyMap(),
    val scans: List<ScanResult> = emptyList(),
    val names: List<HostName> = emptyList(),
    val rank: Long? = null,
    val neighbours: Neighbours? = null,
)

@Serializable
data class Geo(
    val country: String? = null,
    val asn: Long? = null,
    @SerialName("asn_org") val asnOrg: String? = null,
)

@Serializable
data class Family(val name: String, val count: Long = 0)

@Serializable
data class Intel(@SerialName("fetched_at") val fetchedAt: String? = null, val data: JsonElement? = null)

@Serializable
data class ScanResult(@SerialName("scanned_at") val scannedAt: String? = null, val ports: List<Port> = emptyList())

@Serializable
data class Port(val port: Int, val service: String? = null, val product: String? = null)

@Serializable
data class HostName(val name: String, val source: String = "", val agreed: Boolean = false)

@Serializable
data class Neighbours(
    val net: String? = null,
    @SerialName("other_ips") val otherIps: Long = 0,
    @SerialName("same_asn") val sameAsn: Long = 0,
)

@Serializable
data class LookupResult(
    val results: List<IpSummary> = emptyList(),
    val missing: List<String> = emptyList(),
    val unreadable: List<String> = emptyList(),
    val capped: Boolean = false,
    /** One entry per sent name, in order; null from a server older than name lookup. */
    val names: List<NameLookup>? = null,
)

/** One `names` entry. A failed one has [error]; an `invalid` one has no [host]. */
@Serializable
data class NameLookup(
    val host: String? = null,
    val resolve: Resolve? = null,
    val results: List<IpSummary> = emptyList(),
    val missing: List<String> = emptyList(),
    val error: EntryError? = null,
)

@Serializable
data class Resolve(
    val note: String? = null,
    val votes: List<AddrVote> = emptyList(),
    val resolvers: List<String> = emptyList(),
    val errors: List<ResolverError> = emptyList(),
    val also: List<String> = emptyList(),
)

@Serializable
data class AddrVote(val ip: String, val agreed: Boolean = false, val verdict: String = "")

@Serializable
data class ResolverError(val node: String = "", val why: String = "")

@Serializable
data class EntryError(val code: String = "", val message: String = "")

@Serializable
data class Vantage(
    val id: String = "",
    val name: String = "",
    val country: String? = null,
    @SerialName("credits_mc") val creditsMc: Long = 0,
)

/** A live scanner a probe would ask: its price and whether the Actions card ticks it by default. */
@Serializable
data class VantageOffer(
    val id: String,
    val name: String = "",
    val country: String? = null,
    val credits: String = "",
    @SerialName("credits_mc") val creditsMc: Long = 0,
    val default: Boolean = false,
)

@Serializable
data class VantageOffers(val vantages: List<VantageOffer> = emptyList())

@Serializable
data class ProbeStarted(
    @SerialName("job_id") val jobId: String,
    val credits: String = "",
    @SerialName("credits_mc") val creditsMc: Long = 0,
    val vantages: List<Vantage> = emptyList(),
)

@Serializable
data class ScanQuote(
    @SerialName("quote_id") val quoteId: String,
    val credits: String,
    @SerialName("credits_mc") val creditsMc: Long = 0,
    @SerialName("credits_max") val creditsMax: String? = null,
    @SerialName("credits_max_mc") val creditsMaxMc: Long? = null,
    @SerialName("expires_at") val expiresAt: String,
    val offer: Offer = Offer(),
)

@Serializable
data class Offer(val level: Int = 0, val about: String = "")

@Serializable
data class ScanBought(
    @SerialName("job_id") val jobId: String,
    val credits: String = "",
    @SerialName("credits_mc") val creditsMc: Long = 0,
)

@Serializable
data class Job(
    @SerialName("job_id") val jobId: String,
    val kind: String = "",
    val ip: String = "",
    val status: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val result: JsonElement? = null,
) {
    val finished: Boolean get() = status == "done" || status == "failed"

    /** A scan job's result once it landed. */
    fun scan(): ScanResult? = decode(ScanResult.serializer())

    /** A probe job's per-vantage results so far. */
    fun probe(): ProbeResult? = decode(ProbeResult.serializer())

    private fun <T> decode(serializer: KSerializer<T>): T? =
        (result as? JsonObject)?.let { runCatching { PeepholeApi.json.decodeFromJsonElement(serializer, it) }.getOrNull() }
}

@Serializable
data class ProbeResult(val vantages: List<ProbeVantage> = emptyList())

@Serializable
data class ProbeVantage(
    val node: String = "",
    val name: String = "",
    /** `queued`, `running`, `done`, `declined` (with [why]) or `lapsed`. */
    val state: String = "",
    val why: String? = null,
    @SerialName("rtt_ms") val rttMs: Long? = null,
    val ports: List<ProbePort> = emptyList(),
)

@Serializable
data class ProbePort(
    val port: Int,
    val protocol: String = "",
    val outcome: String = "",
    val detail: JsonElement? = null,
)
