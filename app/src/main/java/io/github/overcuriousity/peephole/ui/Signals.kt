package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.IpDetail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** How a signal reads; the web's `ok`, `warn`, `bad` and `info` pill colours. */
enum class Tone { Ok, Warn, Bad, Info }

/** One pill of the strip: a short label, its value, its tone and, for the abuse score, a 0..100 meter. */
data class Signal(val label: String, val value: String, val tone: Tone, val meter: Int? = null)

/** The server's card order for the providers that have a reading. */
private val Providers = listOf("abuseipdb", "shodan", "shodan-internetdb", "rdap")

/**
 * The web's signals strip (peephole `src/admin/signals.rs`), read from the
 * raw stored answers. api-v1 leaves `tor-exits` out of `intel`, so the Tor
 * reading is [IpDetail.isTor]. A provider with no stored answer, or one
 * without a reading here, adds nothing.
 */
fun signalsOf(ip: IpDetail): List<Signal> {
    val out = mutableListOf<Signal>()
    when {
        ip.isTor -> out += Signal("Tor exit", "listed", Tone.Bad)
        ip.isTorRelay -> out += Signal("Tor relay", "listed", Tone.Warn)
        else -> out += Signal("Tor exit", "no", Tone.Ok)
    }
    for (provider in Providers) {
        val data = ip.intel[provider]?.data as? JsonObject ?: continue
        when (provider) {
            "abuseipdb" -> (data["score"] as? JsonPrimitive)?.intOrNull?.let { score ->
                val tone = when {
                    score >= 75 -> Tone.Bad
                    score >= 25 -> Tone.Warn
                    else -> Tone.Ok
                }
                out += Signal("Abuse", "$score/100", tone, meter = score.coerceIn(0, 100))
            }
            "shodan", "shodan-internetdb" -> {
                val label = if (provider == "shodan") "Shodan" else "InternetDB"
                val ports = items(data["ports"])
                when {
                    ports.isNotEmpty() -> out += Signal(label, plural(ports.size, "open port"), Tone.Info)
                    data.values.none(::present) -> out += Signal(label, "nothing known", Tone.Ok)
                }
                val cves = items(data["vulns"])
                if (cves.isNotEmpty()) out += Signal("CVEs", cves.size.toString(), Tone.Bad)
            }
            "rdap" -> {
                val holder = listOf("name", "type").mapNotNull { text(data[it]) }
                if (holder.isNotEmpty()) out += Signal("Registry", holder.joinToString(" · "), Tone.Info)
                if ((data["fresh"] as? JsonPrimitive)?.booleanOrNull == true) {
                    out += Signal("Block", "new, under 90 days", Tone.Warn)
                }
            }
        }
    }
    return out
}

private fun items(v: JsonElement?): List<JsonElement> = (v as? JsonArray).orEmpty()

private fun text(v: JsonElement?): String? =
    (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }

/** A value that would show as a fact on the web: not null, not an empty list, text, or object. */
private fun present(v: JsonElement): Boolean = when (v) {
    is JsonNull -> false
    is JsonArray -> v.isNotEmpty()
    is JsonObject -> v.isNotEmpty()
    is JsonPrimitive -> v.content.isNotBlank()
}

private fun plural(n: Int, one: String) = "$n $one${if (n == 1) "" else "s"}"
