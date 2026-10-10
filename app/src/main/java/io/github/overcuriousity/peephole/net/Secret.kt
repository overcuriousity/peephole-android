package io.github.overcuriousity.peephole.net

/**
 * A credential (device token or pairing code). Its [toString] never reveals
 * the value, so it can't leak through string templates, data-class
 * printing, or a crash report.
 */
class Secret(val value: String) {
    override fun toString(): String = "Secret(***)"

    override fun equals(other: Any?): Boolean = other is Secret && other.value == value

    override fun hashCode(): Int = value.hashCode()
}
