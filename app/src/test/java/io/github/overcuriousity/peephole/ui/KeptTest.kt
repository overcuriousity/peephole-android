package io.github.overcuriousity.peephole.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A screen's last load, kept while it sits on the back stack. */
class KeptTest {
    @Test
    fun nothingIsKeptAtFirst() = assertNull(Kept<String>().get("203.0.113.9"))

    @Test
    fun theSameKeyGetsItsValueBack() {
        val kept = Kept<String>()
        kept.put("203.0.113.9", "dossier")

        assertEquals("dossier", kept.get("203.0.113.9"))
    }

    @Test
    fun anotherKeyGetsNothingAndANewPutReplaces() {
        val kept = Kept<String>()
        kept.put("203.0.113.9", "a")

        assertNull(kept.get("2001:db8::1"))
        kept.put("2001:db8::1", "b")
        assertNull(kept.get("203.0.113.9"))
        assertEquals("b", kept.get("2001:db8::1"))
    }
}
