package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.ApiResult
import io.github.overcuriousity.peephole.net.RequestInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequestScreenTest {
    @Test
    fun bodyTextPrefersText() = assertEquals("a=1", bodyText(RequestInfo(bodyText = "a=1", bodyB64 = "YT0x", bodyLen = 3)))

    @Test
    fun binaryBodyIsDescribed() =
        assertEquals("binary body, 3 bytes (not shown)", bodyText(RequestInfo(bodyText = "", bodyB64 = "AAEC", bodyLen = 3)))

    @Test
    fun noBody() = assertNull(bodyText(RequestInfo()))

    @Test
    fun truncatedBodySaysSo() =
        assertEquals("x\n… truncated by the instance", bodyText(RequestInfo(bodyText = "x", bodyTruncated = true)))

    @Test
    fun dossier404Message() =
        assertEquals("Not found on lab (needs peephole 0.23 or newer).", dossierError(ApiResult.Failed(404, "not_found", "x"), "lab"))

    @Test
    fun otherErrorsReadAsUsual() =
        assertEquals("Can't reach lab (timeout).", dossierError(ApiResult.Unreachable("timeout"), "lab"))
}
