package io.github.overcuriousity.peephole.pairing

import io.github.overcuriousity.peephole.net.PeepholeHttp
import io.github.overcuriousity.peephole.net.PinMismatchException
import io.github.overcuriousity.peephole.net.Secret
import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface PairResult {
    class Paired(
        val token: Secret,
        val deviceId: String,
        val scopes: Set<String>,
        val instanceName: String,
    ) : PairResult

    /** 401: the code expired, was already used, or is unknown. It is dead either way. */
    data object CodeDead : PairResult

    data class RateLimited(val retryAfterSeconds: Long) : PairResult

    /** The origin served a certificate other than the one the QR pinned. Nothing was sent. */
    data object PinMismatch : PairResult

    data class Unreachable(val reason: String) : PairResult

    /** Any other non-2xx, with the instance's own message when it sent one. */
    data class Rejected(val status: Int, val message: String?) : PairResult
}

/** Redeems a pairing code: `POST /api/v1/pair`. Blocking; call off the main thread. */
class PairingClient(private val base: OkHttpClient = PeepholeHttp.base) {

    fun pair(payload: PairingPayload, deviceName: String): PairResult {
        val body = json.encodeToString(PairRequest.serializer(), PairRequest(payload.code.value, deviceName))
        val request = Request.Builder()
            .url(payload.origin.resolve("/api/v1/pair")!!)
            .post(body.toRequestBody(JSON))
            .build()
        val client = PeepholeHttp.client(payload.spkiSha256, base)
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                when (response.code) {
                    200 -> {
                        val ok = runCatching { json.decodeFromString(PairResponse.serializer(), text) }.getOrNull()
                            ?: return PairResult.Rejected(200, "unexpected response from the instance")
                        PairResult.Paired(Secret(ok.token), ok.deviceId, ok.scopes.toSet(), ok.instanceName)
                    }
                    401 -> PairResult.CodeDead
                    429 -> PairResult.RateLimited(response.header("Retry-After")?.trim()?.toLongOrNull() ?: 60)
                    else -> PairResult.Rejected(response.code, errorMessage(text))
                }
            }
        } catch (_: PinMismatchException) {
            PairResult.PinMismatch
        } catch (e: IOException) {
            PairResult.Unreachable(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun errorMessage(text: String): String? =
        runCatching { json.decodeFromString(ErrorBody.serializer(), text).error.message }.getOrNull()

    @Serializable
    private class PairRequest(val code: String, @SerialName("device_name") val deviceName: String)

    @Serializable
    private class PairResponse(
        val token: String,
        @SerialName("device_id") val deviceId: String,
        val scopes: List<String>,
        @SerialName("instance_name") val instanceName: String,
    )

    @Serializable
    private class ErrorBody(val error: Detail) {
        @Serializable
        class Detail(val code: String = "", val message: String = "")
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val JSON = "application/json".toMediaType()
    }
}
