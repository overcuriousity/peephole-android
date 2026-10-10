package io.github.overcuriousity.peephole.pairing

import io.github.overcuriousity.peephole.net.Secret
import io.github.overcuriousity.peephole.net.isBareHttpsOrigin
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The contents of a peephole pairing QR (api-v1 §Pairing), validated. */
class PairingPayload(
    val origin: HttpUrl,
    val code: Secret,
    /** Base64 SHA-256 of the origin's leaf SPKI, or null for plain PKI. */
    val spkiSha256: String?,
) {
    /** `https://host[:port]` as shown to the user and stored. */
    val originText: String get() = origin.toString().removeSuffix("/")

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val base64Url = Regex("^[A-Za-z0-9_-]{16,128}$")

        /** Parses the QR text, or null if it isn't a v1 peephole pairing code. */
        fun parse(text: String): PairingPayload? {
            val raw = runCatching { json.decodeFromString<Raw>(text.trim()) }.getOrNull() ?: return null
            if (raw.v != 1) return null
            return of(raw.origin ?: return null, raw.code ?: return null, raw.tlsSpkiSha256)
        }

        /** Builds a payload from separately entered fields (manual entry). */
        fun of(origin: String, code: String, spkiSha256: String?): PairingPayload? {
            val url = origin.trim().toHttpUrlOrNull()?.takeIf { it.isBareHttpsOrigin() } ?: return null
            val c = code.trim().takeIf { base64Url.matches(it) } ?: return null
            val pin = spkiSha256?.trim()?.takeIf { it.isNotEmpty() }
            if (pin != null && !isSha256Base64(pin)) return null
            return PairingPayload(url, Secret(c), pin)
        }

        private fun isSha256Base64(s: String): Boolean =
            runCatching { Base64.getDecoder().decode(s).size == 32 }.getOrDefault(false)
    }

    @Serializable
    private class Raw(
        val v: Int? = null,
        val origin: String? = null,
        val code: String? = null,
        @SerialName("tls_spki_sha256") val tlsSpkiSha256: String? = null,
    )
}
