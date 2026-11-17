package com.ingsisbry.snippet.adapter.redis

import com.ingsisbry.snippet.adapter.worker.WorkerProperties
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.port.out.job.SnippetJobPublisher
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * `XADD snippet.analysis MAXLEN ~ 100000 * ...`, one entry per job.
 *
 * Never throws: the statuses are already PENDING in the database, so a job that can't be published is found
 * and published again by the worker's sweeper. After the first failure the rest of the batch is dropped too, so
 * a Redis outage costs one timeout, not one per job.
 */
@Component
class RedisSnippetJobPublisher(
    private val redis: StringRedisTemplate,
    private val properties: WorkerProperties,
    private val clock: Clock,
) : SnippetJobPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Suppress("TooGenericExceptionCaught")
    override fun publish(jobs: List<SnippetJob>) {
        if (jobs.isEmpty()) return
        val options = XAddOptions.maxlen(properties.streamMaxLength).approximateTrimming(true)
        val stream = redis.opsForStream<String, String>()
        var published = 0
        try {
            jobs.forEach { job ->
                val fields = SnippetJobMessage.toFields(job, UUID.randomUUID(), Instant.now(clock))
                stream.add(MapRecord.create(properties.stream, fields), options)
                published++
            }
        } catch (exception: RuntimeException) {
            logger.warn(
                "Could not publish {} of {} analysis jobs (first: snippet {}); the sweeper will requeue them: {}",
                jobs.size - published,
                jobs.size,
                jobs[published].snippetId,
                exception.message,
            )
        }
    }
}
