package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.Job
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The job log of v2 §4.1, kept free of Compose so it can be unit-tested.
 *
 * api-v1 has no event times, so a row's time is when this phone first saw
 * that fact in a poll. The copy-out header says so.
 */
enum class Level { INFO, OK, WARN, ERROR }

data class LogRow(val key: String, val atMs: Long, val text: String, val level: Level)

/**
 * Every fact a poll shows, in display order, as stable key → (text, level).
 * A vantage's state is keyed with its value, so each state it passes through
 * gets its own row.
 */
fun rowsOf(job: Job): List<Pair<String, Pair<String, Level>>> {
    val rows = mutableListOf<Pair<String, Pair<String, Level>>>()
    if (job.kind == "scan") {
        job.scan()?.ports.orEmpty().forEach { p ->
            val about = listOfNotNull(p.service, p.product).filter { it.isNotBlank() }.joinToString(" ")
            rows += "scan:port:${p.port}" to ("${p.port}  open  $about".trimEnd() to Level.OK)
        }
    } else {
        job.probe()?.vantages.orEmpty().forEach { v ->
            val id = v.node.ifBlank { v.name }
            val name = v.name.ifBlank { "this node" }
            rows += "vantage:$id:state:${v.state}" to ("$name  ${v.state}" to levelOf(v.state))
            v.rttMs?.let { rows += "vantage:$id:rtt" to ("$name  $it ms" to Level.INFO) }
            val why = v.why?.takeIf { it.isNotBlank() }
                ?: "no result in 15 minutes; nothing charged".takeIf { v.state == "lapsed" }
            why?.let { rows += "vantage:$id:why" to ("$name  $it" to Level.INFO) }
            v.ports.forEach { p ->
                val level = if (p.outcome == "open") Level.OK else Level.INFO
                rows += "vantage:$id:port:${p.port}/${p.protocol}" to ("$name  ${p.port}/${p.protocol}  ${p.outcome}" to level)
            }
        }
    }
    if (job.finished) rows += "job:status" to ("${job.kind.ifBlank { "job" }} ${job.status}" to levelOf(job.status))
    return rows
}

private fun levelOf(state: String): Level = when (state) {
    "done" -> Level.OK
    "lapsed", "declined" -> Level.WARN
    "failed" -> Level.ERROR
    else -> Level.INFO
}

/** [prev] plus the facts in [job] it lacks, stamped [nowMs]. Never reorders or drops a row. */
fun merge(prev: List<LogRow>, job: Job, nowMs: Long): List<LogRow> {
    val seen = prev.mapTo(HashSet()) { it.key }
    val added = rowsOf(job).filter { it.first !in seen }
        .map { (key, row) -> LogRow(key, nowMs, row.first, row.second) }
    return if (added.isEmpty()) prev else prev + added
}

/** Finished vantages of all vantages for a probe; null for a scan or a probe with no vantages yet. */
fun progress(job: Job): Pair<Int, Int>? {
    if (job.kind == "scan") return null
    val vantages = job.probe()?.vantages.orEmpty()
    if (vantages.isEmpty()) return null
    return vantages.count { it.state in FINISHED_VANTAGE } to vantages.size
}

private val FINISHED_VANTAGE = setOf("done", "declined", "lapsed")

/** Time since [createdAtMs], stopped at the poll that first saw the job finished. */
fun elapsedMs(createdAtMs: Long, finishedSeenAtMs: Long?, nowMs: Long): Long =
    ((finishedSeenAtMs ?: nowMs) - createdAtMs).coerceAtLeast(0)

/** `00:42`, or `1:02:03` past an hour. */
fun elapsedText(ms: Long): String {
    val s = ms / 1_000
    return if (s >= 3_600) "%d:%02d:%02d".format(Locale.ROOT, s / 3_600, s / 60 % 60, s % 60) else "%02d:%02d".format(Locale.ROOT, s / 60, s % 60)
}

private val LogTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC)

/** `03:14:02`, UTC. */
fun logTime(atMs: Long): String = LogTimeFormat.format(Instant.ofEpochMilli(atMs))

/** The log as plain text for Copy and Share: [title], the time caveat, one line per row. */
fun copyText(title: String, rows: List<LogRow>): String =
    (listOf(title, "Times are when this phone saw each update (UTC).") + rows.map { "${logTime(it.atMs)}  ${it.text}" })
        .joinToString("\n")
