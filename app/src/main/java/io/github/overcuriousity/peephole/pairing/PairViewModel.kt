package io.github.overcuriousity.peephole.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.overcuriousity.peephole.store.Instance
import io.github.overcuriousity.peephole.store.InstanceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PairState {
    /** Scanning (or manual entry); [message] explains why we came back here. */
    data class Scan(val message: String? = null) : PairState

    /**
     * A scanned payload held for confirmation. It is kept across retries so a
     * network failure doesn't mean re-scanning, until the code's 5-minute life ends.
     */
    data class Confirm(
        val payload: PairingPayload,
        val scannedAtMs: Long,
        val busy: Boolean = false,
        val error: String? = null,
        val retryAtMs: Long? = null,
    ) : PairState

    /** TLS pin mismatch: a hard stop, no way to continue with this code. */
    data class PinMismatch(val origin: String) : PairState

    data class Paired(val instanceName: String) : PairState
}

class PairViewModel(
    private val client: PairingClient,
    private val store: InstanceStore,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow<PairState>(PairState.Scan())
    val state: StateFlow<PairState> = _state.asStateFlow()

    fun scanned(text: String) {
        if (_state.value !is PairState.Scan) return
        val payload = PairingPayload.parse(text)
        _state.value = if (payload == null) {
            PairState.Scan(NOT_A_PAIRING_CODE)
        } else {
            PairState.Confirm(payload, now())
        }
    }

    /** A payload Home's scanner already parsed (v2 §2.2): straight to Confirm. */
    fun confirmScanned(payload: PairingPayload) {
        _state.value = PairState.Confirm(payload, now())
    }

    fun manual(origin: String, code: String, spki: String): Boolean {
        val payload = PairingPayload.of(origin, code, spki) ?: return false
        _state.value = PairState.Confirm(payload, now())
        return true
    }

    fun backToScan() {
        _state.value = PairState.Scan()
    }

    fun confirm(deviceName: String) {
        val held = _state.value as? PairState.Confirm ?: return
        if (held.busy) return
        val origin = held.payload.originText
        if (now() - held.scannedAtMs > CODE_TTL_MS) {
            _state.value = PairState.Scan(codeDead(origin))
            return
        }
        _state.value = held.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { client.pair(held.payload, deviceName) }
            _state.value = when (result) {
                is PairResult.Paired -> {
                    withContext(Dispatchers.IO) {
                        store.add(
                            Instance(
                                id = result.deviceId,
                                origin = origin,
                                instanceName = result.instanceName,
                                deviceName = deviceName,
                                scopes = result.scopes,
                                spkiSha256 = held.payload.spkiSha256,
                                pairedAtEpochMs = now(),
                            ),
                            result.token,
                        )
                    }
                    PairState.Paired(result.instanceName)
                }
                PairResult.CodeDead -> PairState.Scan(codeDead(origin))
                PairResult.PinMismatch -> PairState.PinMismatch(origin)
                is PairResult.RateLimited -> held.copy(
                    error = "Too many pairing attempts. Try again in ${result.retryAfterSeconds} s.",
                    retryAtMs = now() + result.retryAfterSeconds * 1000,
                )
                is PairResult.Unreachable -> held.copy(error = "Can't reach $origin to complete pairing (${result.reason}).")
                is PairResult.Rejected -> held.copy(
                    error = "$origin refused the pairing (HTTP ${result.status})" +
                        (result.message?.let { ": $it" } ?: "."),
                )
            }
        }
    }

    companion object {
        const val CODE_TTL_MS = 5 * 60 * 1000L
        const val NOT_A_PAIRING_CODE = "This QR code isn't a peephole pairing code."

        fun codeDead(origin: String) =
            "This pairing code has expired or was already used. Make a new one on $origin's Devices page."
    }
}
