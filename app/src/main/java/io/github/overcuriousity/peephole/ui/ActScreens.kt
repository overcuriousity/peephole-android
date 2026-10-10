package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.net.ProbeStarted
import io.github.overcuriousity.peephole.net.ScanQuote
import io.github.overcuriousity.peephole.store.Instance
import io.github.overcuriousity.peephole.store.JobLog
import io.github.overcuriousity.peephole.store.JobRef
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Design §6.6. The probe goes out with the instance's default vantage pick
 * (api-v1 has no vantage list or probe quote); what each vantage costs is
 * shown once it starts, on the job screen this hands over to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProbeScreen(
    api: PeepholeApi,
    instance: Instance,
    addr: String,
    jobs: JobLog,
    onBack: () -> Unit,
    onStarted: (jobId: String) -> Unit,
    onRevoked: () -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiResult<ProbeStarted>?>(null) }
    val scope = rememberCoroutineScope()
    RevokedEffect(error, onRevoked)

    Scaffold(topBar = { TopAppBar(title = { Text("Probe") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            FieldLabel("Target")
            Text(addr, style = Mono)
            Spacer(Modifier.height(16.dp))
            Text(
                "${instance.instanceName} probes the address from its default vantage points, as the Actions card " +
                    "on its IP page does. Each vantage charges its probe price; one that declines or gets no " +
                    "answer in 15 minutes charges nothing. The prices show once the probe starts.",
            )
            Spacer(Modifier.height(16.dp))
            error?.let { e ->
                val message = when {
                    e is ApiResult.Failed && e.code == "refused" -> "${instance.instanceName} won't probe this address: ${e.message}"
                    e is ApiResult.Failed && e.status == 404 -> "Not recorded here: not in the dataset."
                    else -> e.describe(instance.instanceName)
                }
                ErrorBanner(message, null)
                Spacer(Modifier.height(16.dp))
            }
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        when (val r = withContext(Dispatchers.IO) { api.probe(addr) }) {
                            is ApiResult.Ok -> {
                                jobs.add(
                                    JobRef(
                                        instanceId = instance.id,
                                        jobId = r.value.jobId,
                                        kind = "probe",
                                        ip = addr,
                                        credits = r.value.credits,
                                        createdAtEpochMs = System.currentTimeMillis(),
                                    ),
                                )
                                onStarted(r.value.jobId)
                            }
                            else -> error = r
                        }
                        busy = false
                    }
                },
                enabled = instance.canAct && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Starting…" else "Probe $addr") }
            if (!instance.canAct) Text(READ_ONLY, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Design §6.7: the one money surface. A fresh quote on entry, its price on
 * the commit button, and a dead Confirm once the quote expires or a buy
 * attempt spent it; a new price is only ever shown, never swapped in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanConfirmScreen(
    api: PeepholeApi,
    instance: Instance,
    addr: String,
    level: Int,
    jobs: JobLog,
    onBack: () -> Unit,
    onBought: (jobId: String) -> Unit,
    onRevoked: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var quote by remember { mutableStateOf<ApiResult<ScanQuote>?>(null) }
    var deadline by remember { mutableStateOf(Instant.EPOCH) }
    var now by remember { mutableStateOf(Instant.now()) }
    var buying by remember { mutableStateOf(false) }
    var buyError by remember { mutableStateOf<ApiResult<*>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(attempt) {
        quote = null
        buyError = null
        val r = withContext(Dispatchers.IO) { api.scanQuote(addr, level) }
        deadline = if (r is ApiResult.Ok) quoteDeadline(Instant.now()) else Instant.EPOCH
        quote = r
    }
    LaunchedEffect(quote) {
        while (quote is ApiResult.Ok) {
            now = Instant.now()
            delay(1_000)
        }
    }
    RevokedEffect(quote, onRevoked)
    RevokedEffect(buyError, onRevoked)

    val q = (quote as? ApiResult.Ok)?.value
    val left = Duration.between(now, deadline).seconds
    val expired = q != null && left <= 0
    val canConfirm = q != null && !expired && !buying && buyError == null && instance.canAct

    Scaffold(
        topBar = { TopAppBar(title = { Text("Confirm counter-scan") }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            FieldLabel("Target")
            Text(addr, style = Mono)
            Spacer(Modifier.height(8.dp))
            FieldLabel("Level")
            Text("L$level" + (q?.offer?.about?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""))
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
                    .padding(16.dp),
            ) {
                val r = quote
                when {
                    r == null -> SkeletonRows(2)
                    r !is ApiResult.Ok -> {
                        Text(quoteProblem(r, instance.instanceName), color = MaterialTheme.colorScheme.error)
                        if (r !is ApiResult.Failed || r.status != 409) {
                            OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
                        }
                    }
                    buyError != null -> {
                        val e = buyError!!
                        Text(
                            if (e is ApiResult.Failed) scanConflict(e) else e.describe(instance.instanceName),
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = { attempt++ }) { Text("Get a new quote") }
                    }
                    expired -> {
                        Text("Quote expired — get a new one.", color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { attempt++ }) { Text("Re-quote") }
                    }
                    else -> {
                        FieldLabel("Cost")
                        Text(costText(q!!), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Quote expires in ${left / 60}:${(left % 60).toString().padStart(2, '0')}")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "A counter-scan probes the live host from a scanner. It can take several minutes and the " +
                    "result is kept in the dataset. Credits are charged when a scanner runs the job; " +
                    "whichever scanner wins it, the charge stays within the range shown.",
            )
            if (!instance.canAct) {
                Spacer(Modifier.height(8.dp))
                Text(READ_ONLY, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = onClick@{
                        if (buying) return@onClick
                        val held = q ?: return@onClick
                        buying = true
                        scope.launch {
                            when (val r = withContext(Dispatchers.IO) { api.buyScan(addr, held.quoteId) }) {
                                is ApiResult.Ok -> {
                                    jobs.add(
                                        JobRef(
                                            instanceId = instance.id,
                                            jobId = r.value.jobId,
                                            kind = "scan",
                                            ip = addr,
                                            level = level,
                                            credits = r.value.credits,
                                            createdAtEpochMs = System.currentTimeMillis(),
                                        ),
                                    )
                                    onBought(r.value.jobId)
                                }
                                // The quote is spent before the checks run, so
                                // any failure here needs a new one.
                                else -> buyError = r
                            }
                            buying = false
                        }
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                ) { Text(if (buying) "Buying…" else "Confirm" + (q?.let { " · ${it.credits}" } ?: "")) }
            }
        }
    }
}

/** `1.28 credits`, or `1.28–2.56 credits` when the winning scanner may cost more. */
fun costText(q: ScanQuote): String =
    if (q.creditsMax == null || q.creditsMax == q.credits) "${q.credits} credits" else "${q.credits}–${q.creditsMax} credits"

/**
 * When the Confirm button dies: the quote's 2-minute life, less a margin for
 * the round trip, counted on the phone's clock from when the quote arrived.
 * The instance's `expires_at` is never compared with the phone's clock: a
 * phone running fast would kill Confirm on arrival. The instance enforces
 * expiry itself, and a stale quote is a 409 that charges nothing.
 */
fun quoteDeadline(receivedAt: Instant): Instant = receivedAt.plus(QUOTE_LIFE).minus(QUOTE_MARGIN)

private val QUOTE_LIFE: Duration = Duration.ofMinutes(2)
private val QUOTE_MARGIN: Duration = Duration.ofSeconds(5)

private fun quoteProblem(r: ApiResult<*>, instanceName: String): String =
    if (r is ApiResult.Failed && r.status == 409) scanConflict(r) else r.describe(instanceName)

/** The 409s of a quote or a buy (api-v1 §Act endpoints), in plain words. Nothing was charged. */
fun scanConflict(e: ApiResult.Failed): String {
    fun field(name: String) = e.detail[name]?.jsonPrimitive?.contentOrNull
    return when (e.code) {
        "fresh" -> "A scan of this level is less than a day old" +
            (field("scanned_at")?.let { " (${utcTime(it)})" } ?: "") + "; its result is on the address's page."
        "on_its_way" -> "A scan of this level is already on its way" + (field("status")?.let { " ($it)" } ?: "") + "."
        "no_price" -> "No live scanner announces a price right now."
        "no_budget" -> "The scan budget does not cover this. Top up in the web admin."
        "quote_invalid" -> "This quote is no longer valid. Nothing was charged; get a new one."
        "quote_mismatch" -> "This quote is for another address. Nothing was charged; get a new one."
        "price_changed" -> "The price changed since this quote. Nothing was charged; get a new one to see it."
        "not_queued" -> "The scan queue did not take the job. Nothing was charged."
        else -> e.message
    }
}
