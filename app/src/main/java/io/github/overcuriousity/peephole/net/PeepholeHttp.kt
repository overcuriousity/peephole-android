package io.github.overcuriousity.peephole.net

import java.io.IOException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.ByteString.Companion.toByteString

/** The served leaf certificate doesn't carry the SPKI the pairing QR named. */
class PinMismatchException(host: String) :
    IOException("$host is not presenting the pinned certificate")

object PeepholeHttp {
    /** Shared base: one connection pool, no redirects (a bearer token never follows one). */
    val base: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * A client for one instance. With [spkiSha256] (base64 SHA-256 of the
     * leaf certificate's SubjectPublicKeyInfo) every connection is checked
     * against it after the normal PKI and hostname checks and before any
     * request byte is written, so a mismatch never sends the pairing code
     * or the token.
     */
    fun client(spkiSha256: String?, base: OkHttpClient = this.base): OkHttpClient {
        if (spkiSha256 == null) return base
        return base.newBuilder().addNetworkInterceptor(SpkiPin(spkiSha256)).build()
    }

    fun spkiSha256(cert: X509Certificate): String =
        cert.publicKey.encoded.toByteString().sha256().base64()

    private class SpkiPin(private val expected: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val host = chain.request().url.host
            // Only the leaf: it is the certificate PKI and hostname
            // verification vouched for. Extra chain certificates are
            // attacker-chosen and must not satisfy the pin.
            val leaf = chain.connection()?.handshake()?.peerCertificates?.firstOrNull()
                as? X509Certificate ?: throw PinMismatchException(host)
            if (spkiSha256(leaf) != expected) throw PinMismatchException(host)
            return chain.proceed(chain.request())
        }
    }
}

/** `https://host[:port]` with no path, query, fragment or userinfo. */
fun HttpUrl.isBareHttpsOrigin(): Boolean =
    scheme == "https" && encodedPath == "/" && query == null && fragment == null &&
        username.isEmpty() && password.isEmpty()
