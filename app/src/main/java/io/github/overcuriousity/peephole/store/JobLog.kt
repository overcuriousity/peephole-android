package io.github.overcuriousity.peephole.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A probe or scan this device queued; `GET /api/v1/jobs/{id}` has its status. */
@Serializable
data class JobRef(
    val instanceId: String,
    val jobId: String,
    /** `probe` or `scan`. */
    val kind: String,
    val ip: String,
    /** Scan level, 0 for a probe. */
    val level: Int = 0,
    val credits: String = "",
    val createdAtEpochMs: Long,
)

/**
 * The jobs this device started, newest first. api-v1 has no job list, so
 * the Jobs tab is built from this log. Holds no secret.
 */
class JobLog(private val kv: KeyValue) {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(JobRef.serializer())

    private val _jobs = MutableStateFlow(load())
    val jobs: StateFlow<List<JobRef>> = _jobs.asStateFlow()

    @Synchronized
    fun add(job: JobRef) = save((listOf(job) + _jobs.value.filter { it.jobId != job.jobId }).take(MAX))

    /** Drops an instance's jobs, on unpair. */
    @Synchronized
    fun removeInstance(instanceId: String) = save(_jobs.value.filter { it.instanceId != instanceId })

    private fun save(next: List<JobRef>) {
        kv.put(KEY to json.encodeToString(listSerializer, next))
        _jobs.value = next
    }

    private fun load(): List<JobRef> =
        kv[KEY]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "jobs"
        const val MAX = 200
    }
}
