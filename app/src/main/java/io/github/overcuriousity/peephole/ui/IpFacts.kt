package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.Props
import io.github.overcuriousity.peephole.net.ScanResult

/** The web's words for a network kind (`_ip_props.html`); an unknown kind shows as sent. */
private val NetKinds = mapOf(
    "hosting" to "hosting",
    "cloud" to "cloud hosting",
    "scanner" to "scanner network",
    "fixed" to "eyeball",
    "mobile" to "mobile",
)

/** The property strip in the web's order, then the unseen marker. */
fun propChips(props: Props?, unseen: Boolean, isTorRelay: Boolean): List<String> = buildList {
    val tor = props?.tor == true
    if (tor) add("Tor exit")
    if (isTorRelay && !tor) add("Tor relay")
    if (props != null) {
        props.net?.takeIf { it.isNotBlank() }?.let { add(NetKinds[it] ?: it) }
        if (props.scanner) add("scanner")
        if (props.fleet) add("fleet")
        if (props.agent) add("agent traces")
        when {
            props.scanPending -> add("scan pending")
            !props.scanned -> add("not scanned")
        }
    }
    if (unseen) add("added by an operator")
}

/** Open ports of the newest scan; null without scans. */
fun newestOpenPorts(scans: List<ScanResult>): Int? =
    scans.maxByOrNull { parseInstant(it.scannedAt)?.toEpochMilli() ?: Long.MIN_VALUE }?.ports?.size
