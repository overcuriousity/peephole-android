package io.github.overcuriousity.peephole.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light or dark, or whatever the system is set to. */
enum class ThemeMode { System, Light, Dark }

/** The appearance pick. Not secret; an unknown or missing value is [ThemeMode.System]. */
class ThemePrefs(private val kv: KeyValue) {
    private val _mode = MutableStateFlow(ThemeMode.entries.firstOrNull { it.name == kv[KEY] } ?: ThemeMode.System)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        kv.put(KEY to mode.name)
        _mode.value = mode
    }

    private companion object {
        const val KEY = "theme"
    }
}
