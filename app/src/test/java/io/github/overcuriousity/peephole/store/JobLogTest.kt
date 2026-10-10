package io.github.overcuriousity.peephole.store

import org.junit.Assert.assertEquals
import org.junit.Test

class JobLogTest {
    private class MapKeyValue : KeyValue {
        val map = mutableMapOf<String, String>()

        override fun get(key: String) = map[key]

        override fun put(vararg entries: Pair<String, String?>) {
            for ((k, v) in entries) if (v == null) map.remove(k) else map[k] = v
        }
    }

    private fun job(instance: String, id: String) = JobRef(instance, id, "scan", "203.0.113.9", 3, "1.28", 0L)

    @Test
    fun newestFirstAndSurvivesARestart() {
        val kv = MapKeyValue()
        val log = JobLog(kv)
        log.add(job("a", "s_1"))
        log.add(job("a", "s_2"))

        assertEquals(listOf("s_2", "s_1"), JobLog(kv).jobs.value.map { it.jobId })
    }

    @Test
    fun unpairDropsOnlyThatInstancesJobs() {
        val log = JobLog(MapKeyValue())
        log.add(job("a", "s_1"))
        log.add(job("b", "p_1"))

        log.removeInstance("a")

        assertEquals(listOf("p_1"), log.jobs.value.map { it.jobId })
    }
}
