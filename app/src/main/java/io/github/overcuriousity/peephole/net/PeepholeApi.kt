package io.github.overcuriousity.peephole.net

import java.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** 401: the device was revoked on the instance. Nothing on it works until re-paired. */
    data object Revoked : ApiResult<Nothing>

    /** 403: the pairing has no `act` scope. */
    data object NoAct : ApiResult<Nothing>

    /** The served certificate is not the pinned one. Nothing was sent. */
    data object PinMismatch : ApiResult<Nothing>

    data class Unreachable(val reason: String) : ApiResult<Nothing>

    /**
     * Any other non-2xx: the instance's error [code] and [message], and the
     * whole `error` object as [detail] (a `fresh` 409 carries `scanned_at`).
     */
    data class Failed(
        val status: Int,
        val code: String,
        val message: String,
        val detail: JsonObject = JsonObject(emptyMap()),
    ) : ApiResult<Nothing>
}

/**
 * The `/api/v1` read and act endpoints of one paired instance
 * (peephole `docs/api-v1.md`). Blocking; call off the main thread.
 * The token goes only into the `Authorization` header and is never logged.
 */
class PeepholeApi(
    private val origin: HttpUrl,
    private val token: Secret,
    spkiSha256: String?,
    base: OkHttpClient = PeepholeHttp.base,
) {
    private val client = PeepholeHttp.client(spkiSha256, base)

    fun ips(q: String, cursor: String?, limit: Int = 50): ApiResult<IpPage> =
        get(IpPage.serializer(), url("ips") {
            addQueryParameter("q", q)
            if (cursor != null) addQueryParameter("cursor", cursor)
            addQueryParameter("limit", limit.toString())
        })

    fun ip(addr: String): ApiResult<IpDetail> = get(IpDetail.serializer(), url("ips", addr))

    fun lookup(entries: List<String>): ApiResult<LookupResult> =
        post(LookupResult.serializer(), url("lookup"), encode(LookupRequest.serializer(), LookupRequest(entries)))

    /** Starts a probe with the default vantage pick of the admin Actions card. */
    fun probe(addr: String): ApiResult<ProbeStarted> =
        post(ProbeStarted.serializer(), url("ips", addr, "probe"), "{}".toRequestBody(JSON))

    fun scanQuote(addr: String, level: Int): ApiResult<ScanQuote> =
        get(ScanQuote.serializer(), url("ips", addr, "scan", "quote") { addQueryParameter("level", level.toString()) })

    fun buyScan(addr: String, quoteId: String): ApiResult<ScanBought> =
        post(ScanBought.serializer(), url("ips", addr, "scan"), encode(BuyRequest.serializer(), BuyRequest(quoteId)))

    fun job(jobId: String): ApiResult<Job> = get(Job.serializer(), url("jobs", jobId))

    private fun url(vararg segments: String, query: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        origin.newBuilder()
            .encodedPath("/")
            .addPathSegments("api/v1")
            .apply { segments.forEach { addPathSegment(it) } }
            .apply(query)
            .build()

    private fun <T> encode(serializer: KSerializer<T>, value: T): RequestBody =
        json.encodeToString(serializer, value).toRequestBody(JSON)

    private fun <T> get(serializer: KSerializer<T>, url: HttpUrl): ApiResult<T> =
        call(serializer, request(url).get().build())

    private fun <T> post(serializer: KSerializer<T>, url: HttpUrl, body: RequestBody): ApiResult<T> =
        call(serializer, request(url).post(body).build())

    private fun request(url: HttpUrl) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${token.value}")
        .header("Accept", "application/json")

    private fun <T> call(serializer: KSerializer<T>, request: Request): ApiResult<T> = try {
        client.newCall(request).execute().use { response -> respond(serializer, response) }
    } catch (_: PinMismatchException) {
        ApiResult.PinMismatch
    } catch (e: IOException) {
        ApiResult.Unreachable(e.message ?: e.javaClass.simpleName)
    }

    private fun <T> respond(serializer: KSerializer<T>, response: Response): ApiResult<T> {
        val text = response.body.string()
        return when (response.code) {
            in 200..299 -> {
                val value = runCatching { json.decodeFromString(serializer, text) }.getOrElse {
                    return ApiResult.Failed(response.code, "unexpected", "Unexpected response from the instance.")
                }
                ApiResult.Ok(value)
            }
            401 -> ApiResult.Revoked
            403 -> ApiResult.NoAct
            else -> failed(response.code, text, response.header("Retry-After"))
        }
    }

    private fun failed(status: Int, text: String, retryAfter: String?): ApiResult.Failed {
        val error = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject }.getOrNull()
        val code = error?.get("code")?.jsonPrimitive?.contentOrNull.orEmpty()
        var message = error?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "HTTP $status" }
        if (status == 429 && retryAfter != null) message = "$message Try again in ${retryAfter.trim()} s."
        return ApiResult.Failed(status, code, message, error ?: JsonObject(emptyMap()))
    }

    @Serializable
    private class LookupRequest(val ips: List<String>)

    @Serializable
    private class BuyRequest(@SerialName("quote_id") val quoteId: String)

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        private val JSON = "application/json".toMediaType()
    }
}
