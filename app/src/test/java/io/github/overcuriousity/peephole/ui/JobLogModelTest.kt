package io.github.overcuriousity.peephole.ui

import io.github.overcuriousity.peephole.net.Job
import io.github.overcuriousity.peephole.net.PeepholeApi
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobLogModelTest {
    private fun result(json: String): JsonElement = PeepholeApi.json.parseToJsonElement(json)

    private fun probe(status: String, vantages: String) =
        Job(jobId = "j1", kind = "probe", ip = "203.0.113.7", status = status, result = result("""{"vantages":[$vantages]}"""))

    private val usRunning = """{"node":"n1","name":"node-us","state":"running"}"""
    private val usDone =
        """{"node":"n1","name":"node-us","state":"done","rtt_ms":110,"ports":[{"port":22,"protocol":"tcp","outcome":"open"}]}"""
    private val euLapsed = """{"node":"n2","name":"node-eu","state":"lapsed"}"""
    private val apFailed = """{"node":"n3","name":"node-ap","state":"failed","why":"no route"}"""

    @Test
    fun mergeAppendsOnlyNewFacts() {
        val first = merge(emptyList(), probe("running", usRunning), 1_000)
        assertEquals(listOf("vantage:n1:state:running"), first.map { it.key })

        val second = merge(first, probe("done", "$usDone,$euLapsed"), 5_000)
        assertEquals(first, second.take(first.size))
        assertEquals(
            listOf(
                "vantage:n1:state:running",
                "vantage:n1:state:done",
                "vantage:n1:rtt",
                "vantage:n1:port:22/tcp",
                "vantage:n2:state:lapsed",
                "vantage:n2:why",
                "job:status",
            ),
            second.map { it.key },
        )
        assertTrue(second.drop(first.size).all { it.atMs == 5_000L })

        // The same poll again adds nothing.
        assertEquals(second, merge(second, probe("done", "$usDone,$euLapsed"), 9_000))
    }

    @Test
    fun mergeKeepsPartialRowsOnFailure() {
        val before = merge(emptyList(), probe("running", "$usDone,$euLapsed"), 1_000)
        // A failed job whose result no longer decodes still keeps every row it had.
        val failed = Job(jobId = "j1", kind = "probe", ip = "203.0.113.7", status = "failed")
        val after = merge(before, failed, 2_000)
        assertEquals(before, after.dropLast(1))
        assertEquals("job:status", after.last().key)
        assertEquals(Level.ERROR, after.last().level)
    }

    @Test
    fun levelsFollowStateWords() {
        val rows = merge(emptyList(), probe("failed", "$usDone,$euLapsed,$apFailed"), 1_000).associateBy { it.key }
        fun check(key: String, level: Level, word: String) {
            val row = rows.getValue(key)
            assertEquals(key, level, row.level)
            assertTrue("'${row.text}' lacks '$word'", row.text.contains(word))
        }
        check("vantage:n1:port:22/tcp", Level.OK, "open")
        check("vantage:n1:state:done", Level.OK, "done")
        check("vantage:n2:state:lapsed", Level.WARN, "lapsed")
        check("vantage:n3:state:failed", Level.ERROR, "failed")
        check("vantage:n1:rtt", Level.INFO, "110 ms")
        check("job:status", Level.ERROR, "failed")

        val scan = Job(
            jobId = "j2", kind = "scan", ip = "203.0.113.7", status = "done",
            result = result("""{"ports":[{"port":443,"service":"https"}]}"""),
        )
        val scanRow = merge(emptyList(), scan, 1_000).first()
        assertEquals("scan:port:443", scanRow.key)
        assertEquals(Level.OK, scanRow.level)
        assertTrue(scanRow.text.contains("open"))
    }

    @Test
    fun progressCountsFinishedVantages() {
        assertEquals(2 to 4, progress(probe("running", "$usDone,$euLapsed,$usRunning,${usRunning.replace("n1", "n4")}")))
        assertNull(progress(probe("queued", "")))
        assertNull(progress(Job(jobId = "j2", kind = "scan", status = "running")))
    }

    @Test
    fun elapsedFreezesOnceFinished() {
        assertEquals(42_000L, elapsedMs(createdAtMs = 1_000, finishedSeenAtMs = null, nowMs = 43_000))
        // Reopened a day later: still the time to the first poll that saw it finished.
        assertEquals(60_000L, elapsedMs(createdAtMs = 1_000, finishedSeenAtMs = 61_000, nowMs = 86_400_000))
        // A phone clock behind the server never shows a negative time.
        assertEquals(0L, elapsedMs(createdAtMs = 5_000, finishedSeenAtMs = null, nowMs = 1_000))
        assertEquals("00:42", elapsedText(42_000))
        assertEquals("1:02:03", elapsedText(3_723_000))
    }

    @Test
    fun copyTextHasHeaderAndTimes() {
        val rows = listOf(
            LogRow("a", 1_791_602_042_000, "node-us  done", Level.OK), // 2026-10-10T03:14:02Z
            LogRow("b", 1_791_602_045_000, "node-eu  lapsed", Level.WARN),
        )
        assertEquals(
            "Probe · 203.0.113.7\n" +
                "Times are when this phone saw each update (UTC).\n" +
                "03:14:02  node-us  done\n" +
                "03:14:05  node-eu  lapsed",
            copyText("Probe · 203.0.113.7", rows),
        )
    }
}
