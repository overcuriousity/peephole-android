package io.github.overcuriousity.peephole.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.R
import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.Job
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.store.JobLog
import io.github.overcuriousity.peephole.store.JobRef
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** First poll after 2 s (api-v1 asks for 2 s or more), backing off to 15 s. */
fun nextPollDelayMs(previousMs: Long): Long = (previousMs * 3 / 2).coerceIn(2_000, 15_000)

fun jobTitle(kind: String, level: Int, ip: String): String =
    (if (kind == "scan") "Scan" + (if (level > 0) " L$level" else "") else "Probe") + " · $ip"

/**
 * Design §6.8 and v2 §4.1: one job, polled while the screen is in the
 * foreground and the job not finished. A status header on top, the log of
 * every fact seen so far below it.
 */
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
    var rows by remember { mutableStateOf(emptyList<LogRow>()) }
    // When the first poll that saw the job finished came in; null while it runs.
    var finishedSeenAtMs by remember { mutableStateOf<Long?>(null) }
    // A job already finished at its first poll ran for an unknown time.
    var finishedAtFirstPoll by remember { mutableStateOf(false) }
    val resumed = isResumed()
    RevokedEffect(error, onRevoked)

    LaunchedEffect(jobId, resumed, attempt) {
        if (!resumed) return@LaunchedEffect
        var wait = 0L
        while (job?.finished != true) {
            delay(wait)
            when (val r = withContext(Dispatchers.IO) { api.job(jobId) }) {
                is ApiResult.Ok -> {
                    val now = System.currentTimeMillis()
                    if (r.value.finished) {
                        finishedAtFirstPoll = job == null
                        finishedSeenAtMs = now
                    }
                    rows = merge(rows, r.value, now)
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
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(
                        enabled = rows.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Job log", copyText(title, rows))))
                                snackbar.showSnackbar("Copied")
                            }
                        },
                    ) { Icon(painterResource(R.drawable.ic_content_copy), "Copy log") }
                    IconButton(
                        enabled = rows.isNotEmpty(),
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, copyText(title, rows))
                            context.startActivity(Intent.createChooser(send, null))
                        },
                    ) { Icon(painterResource(R.drawable.ic_share), "Share log") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val pollError = error?.let { e ->
                if (e is ApiResult.Failed && e.status == 404) "$instanceName doesn't know this job any more." else e.describe(instanceName)
            }
            if (current == null) {
                pollError?.let { ErrorBanner(it, { attempt++ }, EdgeBanner) } ?: SkeletonRows(3)
                return@Column
            }
            StatusHeader(current, ref, live = error == null, finishedSeenAtMs, finishedAtFirstPoll, resumed)
            pollError?.let { ErrorBanner(it, { attempt++ }, EdgeBanner) }
            if (current.status == "failed") ErrorBanner("This ${current.kind.ifBlank { "job" }} failed.", modifier = EdgeBanner)
            LogList(
                rows,
                empty = when {
                    current.kind == "scan" -> if (current.finished) "No open ports." else "Waiting for a scanner…"
                    else -> "Waiting for the vantages…"
                },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { onOpenIp(current.ip) },
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            ) { Text("Open ${current.ip}") }
        }
    }
}

/** v2 §4.1: badge, progress, elapsed time, and the bar while the job runs. */
@Composable
private fun StatusHeader(job: Job, ref: JobRef?, live: Boolean, finishedSeenAtMs: Long?, finishedAtFirstPoll: Boolean, resumed: Boolean) {
    val createdAtMs = parseInstant(job.createdAt)?.toEpochMilli()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(job.finished, resumed) {
        while (!job.finished && resumed) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val progress = progress(job)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(job.status)
                Spacer(Modifier.width(Spacing.sm))
                val count = when {
                    job.kind == "scan" -> job.scan()?.let { "${it.ports.size} open ports" }
                    progress != null -> "${progress.first} of ${progress.second} vantages"
                    else -> "starting"
                }
                count?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (createdAtMs != null && !finishedAtFirstPoll) {
                val ms = elapsedMs(createdAtMs, finishedSeenAtMs, nowMs)
                Text("elapsed ${elapsedText(ms)}", style = MonoMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("started ${utcTime(job.createdAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ref?.credits?.takeIf { it.isNotBlank() }?.let {
                Text("${if (job.kind == "scan") "from" else "up to"} $it credits", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // No animated progress without a live poll behind it (design §6.8, offline).
            if (!job.finished && live) {
                Spacer(Modifier.height(Spacing.sm))
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress.first.toFloat() / progress.second }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * v2 §4.1's log: one mono row per fact. It follows new rows while its last
 * row is on screen; once scrolled up it stays put and offers "↓ N new".
 */
@Composable
private fun LogList(rows: List<LogRow>, empty: String, modifier: Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Rows the reader has been shown the end of; the rest are "new".
    var seen by remember { mutableIntStateOf(0) }
    val lastVisible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 } }
    LaunchedEffect(rows.size) {
        if (rows.size > seen && lastVisible >= seen - 1) {
            listState.scrollToItem(rows.lastIndex)
            seen = rows.size
        }
    }
    LaunchedEffect(lastVisible) { if (lastVisible >= rows.lastIndex) seen = rows.size }

    Box(modifier.fillMaxWidth()) {
        if (rows.isEmpty()) {
            Text(empty, Modifier.padding(Spacing.md), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = Spacing.md)) {
            items(rows, key = { it.key }) { LogLine(it) }
        }
        val fresh = rows.size - seen
        if (fresh > 0) {
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(rows.lastIndex)
                        seen = rows.size
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.sm),
            ) {
                Icon(painterResource(R.drawable.ic_arrow_downward), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(Spacing.sm))
                Text("$fresh new")
            }
        }
    }
}

@Composable
private fun LogLine(row: LogRow) {
    val color = when (row.level) {
        Level.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        Level.OK -> Success
        Level.WARN -> Warning
        Level.ERROR -> MaterialTheme.colorScheme.error
    }
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(logTime(row.atMs), style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.sm))
        Text(row.text, style = MonoSmall, color = color)
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
