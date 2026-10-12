package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import io.github.overcuriousity.peephole.net.FreshLookup
import io.github.overcuriousity.peephole.net.LookupQuote
import io.github.overcuriousity.peephole.net.LookupResult
import io.github.overcuriousity.peephole.net.PeepholeApi
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The fresh provider lookup (api-v1 §lookup/quote): a quote on open, its
 * providers and total, and a spend only on Confirm. Like the scan confirm, a
 * dead Confirm once the quote expires or a spend attempt used it; a new price
 * is only ever shown after "Quote again", never swapped in. [onDone] closes
 * the sheet; [spent] says whether anything was asked, so the caller can
 * re-read the stored data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreshLookupSheet(
    api: PeepholeApi,
    instanceName: String,
    addr: String,
    onDone: (spent: Boolean) -> Unit,
    onRevoked: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var quote by remember { mutableStateOf<ApiResult<LookupQuote>?>(null) }
    var deadline by remember { mutableStateOf(Instant.EPOCH) }
    var now by remember { mutableStateOf(Instant.now()) }
    var spending by remember { mutableStateOf(false) }
    var spendError by remember { mutableStateOf<ApiResult<*>?>(null) }
    var outcome by remember { mutableStateOf<FreshLookup?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(attempt) {
        quote = null
        spendError = null
        val r = withContext(Dispatchers.IO) { api.lookupQuote(addr) }
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
    RevokedEffect(spendError, onRevoked)

    val q = (quote as? ApiResult.Ok)?.value
    val left = Duration.between(now, deadline).seconds
    val expired = q != null && left <= 0
    val canConfirm = q != null && !expired && !spending && spendError == null && outcome == null

    ModalBottomSheet(onDismissRequest = { onDone(outcome != null) }) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Fresh lookup", style = MaterialTheme.typography.titleLarge)
            Text(addr, style = MonoMedium)
            Spacer(Modifier.height(Spacing.md))
            val r = quote
            val done = outcome
            when {
                done != null -> freshOutcome(done).forEach { Text(it) }
                r == null -> SkeletonRows(3)
                r !is ApiResult.Ok -> {
                    Text(freshLookupProblem(r, instanceName), color = MaterialTheme.colorScheme.error)
                    if (canQuoteAgain(r)) OutlinedButton(onClick = { attempt++ }) { Text("Quote again") }
                }
                spendError != null -> {
                    Text(freshLookupProblem(spendError!!, instanceName), color = MaterialTheme.colorScheme.error)
                    if (canQuoteAgain(spendError!!)) OutlinedButton(onClick = { attempt++ }) { Text("Quote again") }
                }
                else -> {
                    q!!.providers.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${p.label.ifBlank { p.provider }} · ${p.node}")
                            Text(if (p.own) "free" else "${p.credits} credits")
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    FieldLabel("Total, at most")
                    Text("${q.credits} credits", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(Spacing.sm))
                    if (expired) {
                        Text("Quote expired — quote again.", color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { attempt++ }) { Text("Quote again") }
                    } else {
                        Text("Quote expires in ${left / 60}:${(left % 60).toString().padStart(2, '0')}")
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                "A fresh lookup asks the providers now. A provider that declines or already has a fresh " +
                    "answer is not charged. The stored-data lookup stays free.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(Spacing.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (done != null) {
                    Button(onClick = { onDone(true) }, modifier = Modifier.weight(1f)) { Text("Done") }
                    return@Row
                }
                OutlinedButton(onClick = { onDone(false) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = onClick@{
                        if (spending) return@onClick
                        val held = q ?: return@onClick
                        spending = true
                        scope.launch {
                            when (val s = withContext(Dispatchers.IO) { api.freshLookup(addr, held.quoteId) }) {
                                is ApiResult.Ok -> outcome = s.value
                                // The quote is spent before the checks run, so
                                // any failure here needs a new one.
                                else -> spendError = s
                            }
                            spending = false
                        }
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                ) { Text(if (spending) "Asking…" else "Confirm" + (q?.let { " · ${it.credits}" } ?: "")) }
            }
        }
    }
}

/** The one address a lookup result is about, if it is exactly one (a network or a list is not). */
fun freshTarget(r: LookupResult): String? = (r.results.map { it.ip } + r.missing).singleOrNull()?.takeIf { '/' !in it }

/**
 * A failed quote or spend in plain words (api-v1 §lookup). Every `409` of a
 * spend charged nothing; a `404` is a server older than fresh lookups (an
 * address outside the dataset can still be quoted).
 */
fun freshLookupProblem(r: ApiResult<*>, instanceName: String): String = when {
    r == ApiResult.NoAct -> "This device can't spend credits on $instanceName: its pairing is read-only."
    r is ApiResult.Failed && r.status == 404 -> "$instanceName is too old for fresh lookups. The stored-data lookup still works."
    r is ApiResult.Failed && r.status == 409 -> when (r.code) {
        "no_provider" -> "No provider is offered for this address right now."
        "not_offered" -> "A provider asked for is not offered right now. Nothing was charged; quote again."
        "quote_invalid" -> "This quote expired or was already used. Nothing was charged; quote again."
        "quote_mismatch" -> "This quote is for another address. Nothing was charged; quote again."
        else -> "Price changed, quote again. Nothing was charged."
    }
    else -> r.describe(instanceName)
}

/** Whether "Quote again" can help: not for a read-only pairing, an old server or a revoked one. */
fun canQuoteAgain(r: ApiResult<*>): Boolean = when {
    r == ApiResult.NoAct || r == ApiResult.Revoked || r == ApiResult.PinMismatch -> false
    r is ApiResult.Failed && (r.status == 404 || r.code == "no_provider") -> false
    else -> true
}

/** What a spent lookup did, one line each: the charge, who was asked, who already had an answer, who declined. */
fun freshOutcome(r: FreshLookup): List<String> = buildList {
    add("Charged ${r.credits.ifBlank { showCredits(r.creditsMc) }} credits.")
    val asked = r.intel.filterValues { it.source == "fresh" }.keys
    val stored = r.intel.filterValues { it.source != "fresh" }.keys
    if (asked.isNotEmpty()) add("Asked now: ${asked.joinToString(", ")}")
    if (stored.isNotEmpty()) add("Already fresh: ${stored.joinToString(", ")}")
    if (r.declined.isNotEmpty()) add("Declined: " + r.declined.joinToString(", ") { if (it.why.isBlank()) it.provider else "${it.provider} (${it.why})" })
    add(if (r.kept) "Kept in the dataset." else "Not kept in the dataset.")
}
