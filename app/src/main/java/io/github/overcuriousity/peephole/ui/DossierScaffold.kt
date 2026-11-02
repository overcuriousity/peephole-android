package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.Dossier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A pivot screen (ASN, campaign, request): loads [load] off the main
 * thread, shows skeletons, the error with Retry, or [content]; Share
 * sends [shareText] or the dossier's JSON.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DossierScaffold(
    title: String,
    titleStyle: TextStyle,
    instanceName: String,
    shareText: String,
    shareLabel: String,
    load: () -> ApiResult<Dossier<T>>,
    onBack: () -> Unit,
    onRevoked: () -> Unit,
    content: LazyListScope.(T) -> Unit,
) {
    var result by remember { mutableStateOf<ApiResult<Dossier<T>>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(title, attempt) {
        result = null
        result = withContext(Dispatchers.IO) { load() }
    }
    RevokedEffect(result, onRevoked)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    val raw = (result as? ApiResult.Ok)?.value?.raw
                    ShareMenu(text = { shareText }, json = raw?.let { r -> { r } }, textLabel = shareLabel)
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            when (val r = result) {
                null -> item { SkeletonRows(4) }
                is ApiResult.Ok -> content(r.value.value)
                else -> item { ErrorBanner(dossierError(r, instanceName), { attempt++ }, EdgeBanner) }
            }
        }
    }
}

/** One `label  value` line of a summary; nothing when [value] is null or blank. */
@Composable
fun Fact(label: String, value: String?, mono: Boolean = false) {
    if (value.isNullOrBlank()) return
    Row(Modifier.padding(vertical = Spacing.xs)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(value, style = if (mono) MonoMedium else MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
