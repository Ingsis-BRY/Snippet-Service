package com.ingsisbry.snippet.adapter.redis

import com.ingsisbry.snippet.adapter.worker.JobStream
import com.ingsisbry.snippet.adapter.worker.StreamEntry
import com.ingsisbry.snippet.adapter.worker.WorkerProperties
import org.springframework.context.annotation.Profile
import org.springframework.dao.DataAccessException
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** [JobStream] on Redis Streams. Blocking reads get a dedicated Lettuce connection, so publishing never waits. */
@Component
@Profile("worker")
class RedisJobStream(
    private val redis: StringRedisTemplate,
    private val properties: WorkerProperties,
    private val clock: Clock,
) : JobStream {
    private val consumer = Consumer.from(properties.group, properties.consumer)

    private fun ops(): StreamOperations<String, String, String> = redis.opsForStream()

    override fun ensureGroup() {
        try {
            // offset 0: jobs published before the group existed are processed too; createGroup sends MKSTREAM
            ops().createGroup(properties.stream, ReadOffset.from("0"), properties.group)
        } catch (exception: DataAccessException) {
            if (!exception.mentions(BUSY_GROUP)) throw exception
        }
    }

    override fun readOwnPending(
        afterId: String,
        count: Int,
    ): List<StreamEntry> =
        ops()
            .read(
                consumer,
                StreamReadOptions.empty().count(count.toLong()),
                StreamOffset.create(properties.stream, ReadOffset.from(afterId)),
            ).orEmpty()
            .map { it.toEntry() }

    override fun readNew(
        count: Int,
        block: Duration,
    ): List<StreamEntry> =
        ops()
            .read(
                consumer,
                StreamReadOptions.empty().count(count.toLong()).block(block),
                StreamOffset.create(properties.stream, ReadOffset.lastConsumed()),
            ).orEmpty()
            .map { it.toEntry() }

    override fun ack(id: String) {
        ops().acknowledge(properties.stream, properties.group, id)
    }

    override fun claimIdle(
        minIdle: Duration,
        count: Int,
    ): List<StreamEntry> {
        // XPENDING ... IDLE: only the entries idle long enough, whichever consumer holds them
        val pending =
            ops()
                .pending(properties.stream, properties.group, Range.unbounded<String>(), count.toLong(), minIdle)
                .toList()
        if (pending.isEmpty()) return emptyList()
        val deliveries = pending.associate { it.idAsString to it.totalDeliveryCount }
        val ids = pending.map { it.id }.toTypedArray()
        // XCLAIM drops entries the stream no longer has (trimmed), and counts one more delivery for the rest
        return ops()
            .claim(properties.stream, properties.group, properties.consumer, minIdle, *ids)
            .orEmpty()
            .map { it.toEntry(deliveries = (deliveries[it.id.value] ?: 0) + 1) }
    }

    override fun deadLetter(
        fields: Map<String, String>,
        error: String,
    ) {
        val entry = fields + mapOf(FIELD_ERROR to error, FIELD_FAILED_AT to Instant.now(clock).toString())
        ops().add(
            MapRecord.create(properties.deadLetterStream, entry),
            XAddOptions.maxlen(properties.streamMaxLength).approximateTrimming(true),
        )
    }

    private fun MapRecord<String, String, String>.toEntry(deliveries: Long = 1): StreamEntry =
        StreamEntry(id = id.value, fields = value.orEmpty(), deliveries = deliveries)

    private fun Throwable.mentions(text: String): Boolean {
        val chain = generateSequence(this) { it.cause }
        return chain.any { it.message.orEmpty().contains(text) }
    }

    companion object {
        const val FIELD_ERROR = "error"
        const val FIELD_FAILED_AT = "failedAt"
        private const val BUSY_GROUP = "BUSYGROUP"
    }
}
