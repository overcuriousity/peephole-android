package io.github.overcuriousity.peephole.lookup

import io.github.overcuriousity.peephole.net.IpSummary
import io.github.overcuriousity.peephole.net.LookupResult
import io.github.overcuriousity.peephole.net.Resolve

/**
 * What one lookup sends: addresses and networks in [ips], reduced host
 * names in [names]. [invalid] entries are never sent (an unreadable URL can
 * still carry a password) and are shown as unreadable on the phone.
 */
data class LookupRequestPlan(val ips: List<String>, val names: List<String>, val invalid: List<String>) {
    /** Nothing for the server: no request is made. */
    val sendsNothing: Boolean get() = ips.isEmpty() && names.isEmpty()
}

/** Routes each entry by [parseEntry]; repeats (also a URL and its bare host) are sent once, in input order. */
fun planLookup(entries: List<String>): LookupRequestPlan {
    val ips = LinkedHashSet<String>()
    val names = LinkedHashSet<String>()
    val invalid = LinkedHashSet<String>()
    for (entry in entries) {
        when (val e = parseEntry(entry)) {
            is Entry.Ip -> ips += e.ip
            is Entry.Cidr -> ips += e.cidr
            is Entry.Name -> names += e.host
            is Entry.Invalid -> invalid += e.text
        }
    }
    return LookupRequestPlan(ips.toList(), names.toList(), invalid.toList())
}

/** One name's answer, labelled with the host the phone sent (the server never echoes input). */
sealed interface NameRow {
    val host: String

    data class Resolved(
        override val host: String,
        val resolve: Resolve,
        val results: List<IpSummary>,
        val missing: List<String>,
    ) : NameRow

    data class Failed(override val host: String, val code: String, val message: String) : NameRow

    /** The server predates name lookup (its answer has no `names`). */
    data class TooOld(override val host: String) : NameRow
}

/** Pairs entry *i* of [result]`.names` with the *i*-th host in [sent]. */
fun nameRows(sent: List<String>, result: LookupResult): List<NameRow> {
    val names = result.names ?: return sent.map { NameRow.TooOld(it) }
    return sent.mapIndexed { i, host ->
        val entry = names.getOrNull(i)
        val error = entry?.error
        when {
            entry == null -> NameRow.Failed(host, "unresolved", "No answer for this name.")
            error != null -> NameRow.Failed(host, error.code, error.message)
            else -> NameRow.Resolved(host, entry.resolve ?: Resolve(), entry.results, entry.missing)
        }
    }
}

/** v2 §3.1: the server's trust note, else how many resolvers agreed. */
fun agreementLine(r: Resolve): String =
    r.note ?: "agreed by ${r.votes.count { it.agreed }} of ${r.resolvers.size} resolvers"

/** v2 §3.1 warning header: no address is agreed, or the server calls the answer disputed. */
fun isDisputed(r: Resolve): Boolean = r.votes.none { it.agreed } || r.note?.contains("disputed") == true
