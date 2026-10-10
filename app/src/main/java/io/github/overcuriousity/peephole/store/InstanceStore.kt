package io.github.overcuriousity.peephole.store

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.overcuriousity.peephole.net.Secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One paired peephole instance. Holds no secret; the token is stored apart, encrypted. */
@Serializable
data class Instance(
    /** The server's `device_id` for this pairing. */
    val id: String,
    val origin: String,
    val instanceName: String,
    val deviceName: String,
    val scopes: Set<String>,
    val spkiSha256: String? = null,
    val pairedAtEpochMs: Long,
) {
    val canAct: Boolean get() = "act" in scopes
}

/** String key/value storage; SharedPreferences in the app, a map in tests. */
interface KeyValue {
    operator fun get(key: String): String?

    /** Writes synchronously; a null value removes the key. */
    fun put(vararg entries: Pair<String, String?>)
}

class PrefsKeyValue(private val prefs: SharedPreferences) : KeyValue {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(vararg entries: Pair<String, String?>) {
        prefs.edit(commit = true) {
            for ((k, v) in entries) if (v == null) remove(k) else putString(k, v)
        }
    }
}

/**
 * Paired instances and their device tokens. Tokens are encrypted with
 * [cipher] before they touch storage and are only handed out as [Secret].
 */
class InstanceStore(private val kv: KeyValue, private val cipher: TokenCipher) {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(Instance.serializer())

    private val _instances = MutableStateFlow(load())
    val instances: StateFlow<List<Instance>> = _instances.asStateFlow()

    fun get(id: String): Instance? = _instances.value.firstOrNull { it.id == id }

    fun token(id: String): Secret? = kv[tokenKey(id)]?.let { Secret(cipher.decrypt(it)) }

    /**
     * Adds a pairing. Pairing an origin again (e.g. after a revoke) replaces
     * the old entry and drops its token.
     */
    @Synchronized
    fun add(instance: Instance, token: Secret) {
        val replaced = _instances.value.filter { it.origin == instance.origin || it.id == instance.id }
        val next = _instances.value - replaced.toSet() + instance
        val drops = replaced.filter { it.id != instance.id }.map { tokenKey(it.id) to null }
        kv.put(
            tokenKey(instance.id) to cipher.encrypt(token.value),
            LIST_KEY to json.encodeToString(listSerializer, next),
            *drops.toTypedArray(),
        )
        _instances.value = next
    }

    /** Forgets an instance and its token on this device. */
    @Synchronized
    fun remove(id: String) {
        val next = _instances.value.filter { it.id != id }
        kv.put(tokenKey(id) to null, LIST_KEY to json.encodeToString(listSerializer, next))
        _instances.value = next
    }

    private fun load(): List<Instance> =
        kv[LIST_KEY]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()

    private fun tokenKey(id: String) = "token.$id"

    private companion object {
        const val LIST_KEY = "instances"
    }
}
