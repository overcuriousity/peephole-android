package io.github.overcuriousity.peephole.ui

/**
 * One screen's last good load and what it was for, so coming Back to the
 * screen shows it at once (and keeps the list where it was) instead of
 * fetching again. Errors are not kept: Retry and Back load afresh.
 */
class Kept<T> {
    private var key: String? = null
    private var value: T? = null

    fun get(key: String): T? = value.takeIf { this.key == key }

    fun put(key: String, value: T) {
        this.key = key
        this.value = value
    }
}
