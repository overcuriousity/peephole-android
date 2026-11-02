package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.RequestInfo

/**
 * A request body as the screen shows it: the text, a line for a binary
 * body (never decoded or rendered), or null without a body.
 */
fun bodyText(r: RequestInfo): String? {
    val shown = when {
        r.bodyText.isNotEmpty() -> r.bodyText
        r.bodyB64.isNotEmpty() -> "binary body, ${r.bodyLen} bytes (not shown)"
        else -> return null
    }
    return if (r.bodyTruncated) "$shown\n… truncated by the instance" else shown
}

/** A dossier failure; a 404 can also be an instance older than 0.23. */
fun dossierError(r: ApiResult<*>, instanceName: String): String =
    if (r is ApiResult.Failed && r.status == 404) "Not found on $instanceName (needs peephole 0.23 or newer)." else r.describe(instanceName)
