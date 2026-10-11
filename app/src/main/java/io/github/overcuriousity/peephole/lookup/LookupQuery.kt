package io.github.overcuriousity.peephole.lookup

import io.github.overcuriousity.peephole.pairing.PairingPayload
import java.util.Locale

/**
 * One typed or scanned lookup entry, classified on the phone the way the
 * server's `host_of` (peephole `src/intel/dns.rs`) reduces it. Pure: no
 * resolution, no I/O, so a hostile QR costs nothing but a scan.
 */
sealed interface Entry {
    /** An address; [extracted] when it was read out of a URL or `[v6]`. Sent in `ips`. */
    data class Ip(val ip: String, val extracted: Boolean) : Entry

    /** A network. Sent in `ips`. */
    data class Cidr(val cidr: String) : Entry

    /** A host name, lower case and ASCII (punycode). Sent in `names`. */
    data class Name(val host: String, val extracted: Boolean) : Entry

    /** No host can be read. Never sent; shown as unreadable on the phone. */
    data class Invalid(val text: String) : Entry
}

/** What a scanned QR is (v2 §2.1). */
sealed interface ScanOutcome {
    data class Pairing(val payload: PairingPayload) : ScanOutcome

    /** [shownFrom]: the scanned text with userinfo masked, null when it is the [query] itself. */
    data class LookupTarget(val query: String, val shownFrom: String?) : ScanOutcome

    data class Unreadable(val text: String, val reason: String) : ScanOutcome
}

/** Longest entry read; anything longer is [Entry.Invalid]. */
const val MAX_ENTRY = 2048

const val UNREADABLE_REASON = "This isn't a web address, IP address, host name, or pairing code."

/**
 * Classifies one entry. In order: too long or with whitespace → invalid; an
 * address or network as typed; else a URL (`scheme://[user@]host[:port]…`,
 * the scheme optional) reduced to its host, userinfo dropped.
 */
fun parseEntry(text: String): Entry {
    val t = text.trim()
    if (t.isEmpty() || t.length > MAX_ENTRY || t.any { it.isWhitespace() || it.isISOControl() }) return Entry.Invalid(t)
    if (isIp(t)) return Entry.Ip(t, extracted = false)
    val slash = t.lastIndexOf('/')
    if (slash > 0 && isIp(t.substring(0, slash))) {
        val prefix = t.substring(slash + 1)
        if (prefix.length in 1..3 && prefix.all { it in '0'..'9' }) {
            val max = if (isIpv4(t.substring(0, slash))) 32 else 128
            return if (prefix.toInt() <= max) Entry.Cidr(t) else Entry.Invalid(t)
        }
    }
    val authority = authorityOf(t) ?: return Entry.Invalid(t)
    val hostPort = authority.substringAfterLast('@')
    if (hostPort.startsWith('[')) {
        val close = hostPort.indexOf(']')
        if (close < 0) return Entry.Invalid(t)
        val addr = hostPort.substring(1, close)
        val port = hostPort.substring(close + 1)
        val portOk = port.isEmpty() || (port.startsWith(':') && isPort(port.substring(1)))
        return if (isIpv6(addr) && portOk) Entry.Ip(addr, extracted = true) else Entry.Invalid(t)
    }
    if (isIp(hostPort)) return Entry.Ip(hostPort, extracted = hostPort != t)
    val colon = hostPort.lastIndexOf(':')
    val host = when {
        colon < 0 -> hostPort
        isPort(hostPort.substring(colon + 1)) -> hostPort.substring(0, colon)
        else -> return Entry.Invalid(t)
    }
    if (isIpv4(host)) return Entry.Ip(host, extracted = true)
    val name = validName(host) ?: return Entry.Invalid(t)
    return Entry.Name(name, extracted = name != t)
}

/** A pairing code first, else [parseEntry]: an address, network or host is looked up. */
fun classifyScan(text: String): ScanOutcome {
    PairingPayload.parse(text)?.let { return ScanOutcome.Pairing(it) }
    val shown = maskUserinfo(text.trim())
    return when (val e = parseEntry(text)) {
        is Entry.Ip -> ScanOutcome.LookupTarget(e.ip, shown.takeIf { e.extracted })
        is Entry.Name -> ScanOutcome.LookupTarget(e.host, shown.takeIf { e.extracted })
        is Entry.Cidr -> ScanOutcome.LookupTarget(e.cidr, null)
        is Entry.Invalid -> ScanOutcome.Unreadable(shown, UNREADABLE_REASON)
    }
}

/** `text` with any userinfo in its authority replaced by `•••`, so a password is never shown. */
fun maskUserinfo(text: String): String {
    val sep = text.indexOf("://")
    val start = if (sep > 0 && isScheme(text.substring(0, sep))) sep + 3 else 0
    val end = text.indexOfAny(charArrayOf('/', '?', '#'), start).let { if (it < 0) text.length else it }
    val at = text.lastIndexOf('@', end - 1)
    if (at < start) return text
    return text.substring(0, start) + "•••" + text.substring(at)
}

/** Everything up to the first `/?#` after an optional scheme, or null for a malformed scheme. */
private fun authorityOf(t: String): String? {
    val sep = t.indexOf("://")
    val rest = when {
        sep < 0 -> t
        isScheme(t.substring(0, sep)) -> t.substring(sep + 3)
        else -> return null
    }
    return rest.substring(0, rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it })
}

private fun isScheme(s: String): Boolean =
    s.isNotEmpty() && s.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '+' || it == '-' || it == '.' }

private fun isPort(s: String): Boolean = s.length in 1..5 && s.all { it in '0'..'9' } && s.toInt() <= 65535

private fun isIp(s: String): Boolean = isIpv4(s) || isIpv6(s)

/** Four decimal octets 0–255, no leading zeros (as Rust's `Ipv4Addr` reads them). */
internal fun isIpv4(s: String): Boolean {
    val parts = s.split('.')
    return parts.size == 4 && parts.all { p ->
        p.length in 1..3 && p.all { it in '0'..'9' } && (p.length == 1 || p[0] != '0') && p.toInt() <= 255
    }
}

/** Hex groups with at most one `::` and an optional dotted IPv4 tail; no zone. */
internal fun isIpv6(s: String): Boolean {
    if (':' !in s) return false
    var text = s
    if ('.' in s) {
        val last = s.lastIndexOf(':')
        if (!isIpv4(s.substring(last + 1))) return false
        text = s.substring(0, last + 1) + "0:0"
    }
    fun groups(part: String): Int? =
        if (part.isEmpty()) {
            0
        } else {
            part.split(':').takeIf { gs -> gs.all { g -> g.length in 1..4 && g.all { it.isHex() } } }?.size
        }
    val gap = text.indexOf("::")
    if (gap < 0) return groups(text) == 8
    if (text.indexOf("::", gap + 1) >= 0) return false
    val head = groups(text.substring(0, gap)) ?: return false
    val tail = groups(text.substring(gap + 2)) ?: return false
    return head + tail <= 7
}

private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/**
 * The server's `valid_name`: lower case, no trailing dot, at least two
 * labels of `[a-z0-9-]` (1–63, no hyphen at either end), ≤253 in all, the
 * last not all digits. A non-ASCII label becomes `xn--` + its punycode.
 */
internal fun validName(input: String): String? {
    val lower = input.trimEnd('.').lowercase(Locale.ROOT)
    if (lower.isEmpty() || '.' !in lower || isIp(lower)) return null
    val labels = lower.split('.').map { l ->
        if (l.all { it.code < 128 }) l else "xn--" + (punycode(l) ?: return null)
    }
    val name = labels.joinToString(".")
    val ok = name.length <= 253 &&
        !labels.last().all { it in '0'..'9' } &&
        labels.all { l ->
            l.length in 1..63 && !l.startsWith('-') && !l.endsWith('-') &&
                l.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
        }
    return name.takeIf { ok }
}

/**
 * RFC 3492 §6.3, ported from the server's `punycode` so both sides encode a
 * label to the same bytes (`java.net.IDN` would first apply IDNA2003
 * nameprep, which rewrites e.g. `ß` to `ss`). Null on overflow.
 */
private fun punycode(label: String): String? {
    val base = 36L
    val tMin = 1L
    val tMax = 26L
    val limit = 0xFFFFFFFFL
    val input = label.codePoints().toArray().map { it.toLong() }
    fun digit(d: Long): Char = if (d < 26) ('a' + d.toInt()) else ('0' + (d - 26).toInt())
    fun adapt(delta0: Long, points: Long, first: Boolean): Long {
        var delta = if (first) delta0 / 700 else delta0 / 2
        delta += delta / points
        var k = 0L
        while (delta > ((base - tMin) * tMax) / 2) {
            delta /= base - tMin
            k += base
        }
        return k + (base - tMin + 1) * delta / (delta + 38)
    }
    val out = StringBuilder()
    input.filter { it < 128 }.forEach { out.appendCodePoint(it.toInt()) }
    val basic = out.length.toLong()
    var handled = basic
    if (basic > 0) out.append('-')
    var n = 128L
    var delta = 0L
    var bias = 72L
    while (handled < input.size) {
        val m = input.filter { it >= n }.minOrNull() ?: return null
        delta += (m - n) * (handled + 1)
        if (delta > limit) return null
        n = m
        for (c in input) {
            if (c < n && ++delta > limit) return null
            if (c == n) {
                var q = delta
                var k = base
                while (true) {
                    val t = when {
                        k <= bias -> tMin
                        k >= bias + tMax -> tMax
                        else -> k - bias
                    }
                    if (q < t) break
                    out.append(digit(t + (q - t) % (base - t)))
                    q = (q - t) / (base - t)
                    k += base
                }
                out.append(digit(q))
                bias = adapt(delta, handled + 1, handled == basic)
                delta = 0
                handled++
            }
        }
        delta++
        n++
    }
    return out.toString()
}
