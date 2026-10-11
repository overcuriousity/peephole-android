package io.github.overcuriousity.peephole.store

import io.github.overcuriousity.peephole.net.Secret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class InstanceStoreTest {
    private class MapKeyValue : KeyValue {
        val map = mutableMapOf<String, String>()

        override fun get(key: String) = map[key]

        override fun put(vararg entries: Pair<String, String?>) {
            for ((k, v) in entries) if (v == null) map.remove(k) else map[k] = v
        }
    }

    /** Stands in for the Keystore cipher, which unit tests can't reach. */
    private object FakeCipher : TokenCipher {
        override fun encrypt(plain: String) = "sealed:" + plain.reversed()

        override fun decrypt(sealed: String) = sealed.removePrefix("sealed:").reversed()
    }

    private val kv = MapKeyValue()

    private fun instance(id: String, origin: String) =
        Instance(id, origin, origin.removePrefix("https://"), "Pixel 8", setOf("read"), null, 0L)

    @Test
    fun keepsSeveralInstancesAndTheirTokens() {
        val store = InstanceStore(kv, FakeCipher)
        store.add(instance("a", "https://a.example"), Secret("token-a"))
        store.add(instance("b", "https://b.example"), Secret("token-b"))

        assertEquals(listOf("a", "b"), store.instances.value.map { it.id })
        assertEquals("token-b", store.token("b")!!.value)
        assertFalse("plaintext token at rest", kv.map.values.any { it.contains("token-a") || it.contains("token-b") })

        // Survives a restart.
        assertEquals(listOf("a", "b"), InstanceStore(kv, FakeCipher).instances.value.map { it.id })
    }

    @Test
    fun repairingAnOriginReplacesTheOldPairing() {
        val store = InstanceStore(kv, FakeCipher)
        store.add(instance("old", "https://a.example"), Secret("token-old"))
        store.add(instance("new", "https://a.example"), Secret("token-new"))

        assertEquals(listOf("new"), store.instances.value.map { it.id })
        assertNull(store.token("old"))
    }

    @Test
    fun unpairRemovesOnlyThatInstance() {
        val store = InstanceStore(kv, FakeCipher)
        store.add(instance("a", "https://a.example"), Secret("token-a"))
        store.add(instance("b", "https://b.example"), Secret("token-b"))

        store.remove("a")

        assertEquals(listOf("b"), store.instances.value.map { it.id })
        assertNull(store.token("a"))
        assertEquals("token-b", store.token("b")!!.value)
    }

    @Test
    fun lastUsedDefaultsToNewest() {
        val store = InstanceStore(kv, FakeCipher)
        assertNull(store.lastUsedId)
        store.add(instance("a", "https://a.example"), Secret("token-a"))
        store.add(instance("b", "https://b.example"), Secret("token-b"))

        assertEquals("b", store.lastUsedId)
    }

    @Test
    fun lastUsedSurvivesReload() {
        val store = InstanceStore(kv, FakeCipher)
        store.add(instance("a", "https://a.example"), Secret("token-a"))
        store.add(instance("b", "https://b.example"), Secret("token-b"))
        store.lastUsedId = "a"

        assertEquals("a", InstanceStore(kv, FakeCipher).lastUsedId)
    }

    @Test
    fun removingLastUsedFallsBack() {
        val store = InstanceStore(kv, FakeCipher)
        store.add(instance("a", "https://a.example"), Secret("token-a"))
        store.add(instance("b", "https://b.example"), Secret("token-b"))
        store.add(instance("c", "https://c.example"), Secret("token-c"))
        store.lastUsedId = "a"

        store.remove("a")

        assertEquals("c", store.lastUsedId)
        assertNull(kv.map["lastUsed"])
        store.remove("b")
        store.remove("c")
        assertNull(store.lastUsedId)
    }
}
