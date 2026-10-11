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

    private val _lastUsed = MutableStateFlow(kv[LAST_USED_KEY])

    /** The stored [lastUsedId] as written; observe it, then read [lastUsedId]. */
    val lastUsed: StateFlow<String?> = _lastUsed.asStateFlow()

    /**
     * The instance Home looks things up on (v2 §1): the one last chosen if it
     * is still paired, else the most recently paired, else null.
     */
    var lastUsedId: String?
        get() {
            val list = _instances.value
            return _lastUsed.value?.takeIf { id -> list.any { it.id == id } } ?: list.lastOrNull()?.id
        }

        @Synchronized
        set(id) {
            kv.put(LAST_USED_KEY to id)
            _lastUsed.value = id
        }

    fun get(id: String): Instance? = _instances.value.firstOrNull { it.id == id }

    fun token(id: String): Secret? = kv[tokenKey(id)]?.let { Secret(cipher.decrypt(it)) }

    /**
     * Adds a pairing and makes it the [lastUsedId]. Pairing an origin again
     * (e.g. after a revoke) replaces the old entry and drops its token.
     */
    @Synchronized
    fun add(instance: Instance, token: Secret) {
        val replaced = _instances.value.filter { it.origin == instance.origin || it.id == instance.id }
        val next = _instances.value - replaced.toSet() + instance
        val drops = replaced.filter { it.id != instance.id }.map { tokenKey(it.id) to null }
        kv.put(
            tokenKey(instance.id) to cipher.encrypt(token.value),
            LIST_KEY to json.encodeToString(listSerializer, next),
            LAST_USED_KEY to instance.id,
            *drops.toTypedArray(),
        )
        _instances.value = next
        _lastUsed.value = instance.id
    }

    /** Forgets an instance and its token on this device. */
    @Synchronized
    fun remove(id: String) {
        val next = _instances.value.filter { it.id != id }
        val clearsLastUsed = _lastUsed.value == id
        val writes = mutableListOf<Pair<String, String?>>(tokenKey(id) to null, LIST_KEY to json.encodeToString(listSerializer, next))
        if (clearsLastUsed) writes += LAST_USED_KEY to null
        kv.put(*writes.toTypedArray())
        _instances.value = next
        if (clearsLastUsed) _lastUsed.value = null
    }

    private fun load(): List<Instance> =
        kv[LIST_KEY]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()

    private fun tokenKey(id: String) = "token.$id"

    private companion object {
        const val LIST_KEY = "instances"
        const val LAST_USED_KEY = "lastUsed"
    }
}
