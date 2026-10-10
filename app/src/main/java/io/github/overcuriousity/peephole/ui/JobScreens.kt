package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.Job
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.net.ProbeVantage
import io.github.overcuriousity.peephole.store.JobLog
import io.github.overcuriousity.peephole.store.JobRef
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** First poll after 2 s (api-v1 asks for 2 s or more), backing off to 15 s. */
fun nextPollDelayMs(previousMs: Long): Long = (previousMs * 3 / 2).coerceIn(2_000, 15_000)

fun jobTitle(kind: String, level: Int, ip: String): String =
    (if (kind == "scan") "Scan" + (if (level > 0) " L$level" else "") else "Probe") + " · $ip"

/** Design §6.8: one job, polled while the screen is in the foreground and the job not finished. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobScreen(
    api: PeepholeApi,
    instanceName: String,
    jobId: String,
    ref: JobRef?,
    onBack: () -> Unit,
    onOpenIp: (String) -> Unit,
    onRevoked: () -> Unit,
) {
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<ApiResult<*>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val resumed = isResumed()
    RevokedEffect(error, onRevoked)

    LaunchedEffect(jobId, resumed, attempt) {
        if (!resumed) return@LaunchedEffect
        var wait = 0L
        while (job?.finished != true) {
            delay(wait)
            when (val r = withContext(Dispatchers.IO) { api.job(jobId) }) {
                is ApiResult.Ok -> {
                    job = r.value
                    error = null
                }
                else -> {
                    // Stop on any failure; the banner's Retry starts again.
                    error = r
                    break
                }
            }
            wait = nextPollDelayMs(wait)
        }
    }

    val current = job
    val title = current?.let { jobTitle(it.kind, ref?.level ?: 0, it.ip) }
        ?: ref?.let { jobTitle(it.kind, it.level, it.ip) } ?: "Job"
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            error?.let { e ->
                val message = if (e is ApiResult.Failed && e.status == 404) {
                    "$instanceName doesn't know this job any more."
                } else {
                    e.describe(instanceName)
                }
                ErrorBanner(message) { attempt++ }
            }
            if (current == null) {
                if (error == null) SkeletonRows(3)
                return@Column
            }
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(current.status)
                    Spacer(Modifier.width(8.dp))
                    Text("started ${utcTime(current.createdAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ref?.credits?.takeIf { it.isNotBlank() }?.let {
                    Text("${if (current.kind == "scan") "from" else "up to"} $it credits", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // No animated progress without a live poll behind it (design §6.8, offline).
                if (!current.finished && error == null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(16.dp))
                if (current.kind == "scan") {
                    current.scan()?.let { ScanBlock(it) }
                        ?: Text(if (current.status == "failed") "The scan failed." else "Waiting for a scanner…")
                } else {
                    val vantages = current.probe()?.vantages.orEmpty()
                    if (vantages.isEmpty()) Text("Waiting for the vantages…")
                    vantages.forEach { VantageBlock(it) }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { onOpenIp(current.ip) }, modifier = Modifier.fillMaxWidth()) { Text("Open ${current.ip}") }
            }
        }
    }
}

@Composable
private fun VantageBlock(v: ProbeVantage) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.name.ifBlank { "this node" }, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            StatusBadge(v.state)
            v.rttMs?.let {
                Spacer(Modifier.width(8.dp))
                Text("$it ms", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        v.why?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (v.state == "lapsed") Text("No result in 15 minutes; nothing charged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        v.ports.forEach { p -> Text("${p.port}/${p.protocol}  ${p.outcome}", style = Mono) }
    }
}

/**
 * Design §6.8, the Jobs tab: the jobs this device started on this instance
 * (api-v1 has no job list), with unfinished ones refreshed every 10 s while
 * the tab is in the foreground.
 */
@Composable
fun JobsTab(api: PeepholeApi, instanceId: String, jobs: JobLog, onOpenJob: (String) -> Unit, onRevoked: () -> Unit) {
    val all by jobs.jobs.collectAsState()
    val refs = all.filter { it.instanceId == instanceId }
    val status = remember { mutableStateMapOf<String, String>() }
    var revoked by remember { mutableStateOf<ApiResult<*>?>(null) }
    val resumed = isResumed()
    RevokedEffect(revoked, onRevoked)

    LaunchedEffect(refs.map { it.jobId }, resumed) {
        if (!resumed) return@LaunchedEffect
        while (true) {
            val open = refs.filter { status[it.jobId] !in FINISHED }
            if (open.isEmpty()) break
            for (ref in open) {
                when (val r = withContext(Dispatchers.IO) { api.job(ref.jobId) }) {
                    is ApiResult.Ok -> status[ref.jobId] = r.value.status
                    ApiResult.Revoked -> {
                        revoked = r
                        return@LaunchedEffect
                    }
                    is ApiResult.Failed -> if (r.status == 404) status[ref.jobId] = "unknown"
                    else -> {}
                }
            }
            delay(10_000)
        }
    }

    if (refs.isEmpty()) {
        EmptyState("No scans or probes yet.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(refs, key = { it.jobId }) { ref ->
            ListItem(
                modifier = Modifier.clickable { onOpenJob(ref.jobId) },
                headlineContent = { Text(jobTitle(ref.kind, ref.level, ref.ip)) },
                supportingContent = {
                    Text(
                        "started ${ago(Instant.ofEpochMilli(ref.createdAtEpochMs).toString())}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = { StatusBadge(status[ref.jobId] ?: "…") },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private val FINISHED = setOf("done", "failed", "unknown")
