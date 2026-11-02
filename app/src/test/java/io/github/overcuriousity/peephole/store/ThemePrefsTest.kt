package io.github.overcuriousity.peephole.store

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePrefsTest {
    private class MapKv(val map: MutableMap<String, String> = mutableMapOf()) : KeyValue {
        override fun get(key: String) = map[key]

        override fun put(vararg entries: Pair<String, String?>) {
            for ((k, v) in entries) if (v == null) map.remove(k) else map[k] = v
        }
    }

    @Test
    fun defaultIsSystem() = assertEquals(ThemeMode.System, ThemePrefs(MapKv()).mode.value)

    @Test
    fun aPickIsKept() {
        val kv = MapKv()
        ThemePrefs(kv).set(ThemeMode.Dark)

        assertEquals("Dark", kv.map["theme"])
        assertEquals(ThemeMode.Dark, ThemePrefs(kv).mode.value)
    }

    @Test
    fun aPickIsObservable() {
        val prefs = ThemePrefs(MapKv())
        prefs.set(ThemeMode.Light)

        assertEquals(ThemeMode.Light, prefs.mode.value)
    }

    @Test
    fun anUnknownValueReadsAsSystem() =
        assertEquals(ThemeMode.System, ThemePrefs(MapKv(mutableMapOf("theme" to "Purple"))).mode.value)
}
