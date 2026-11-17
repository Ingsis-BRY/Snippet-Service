package com.ingsisbry.snippet.adapter.worker

import com.ingsisbry.snippet.adapter.redis.SnippetJobMessage
import com.ingsisbry.snippet.application.command.process.FailSnippetJob
import com.ingsisbry.snippet.application.command.process.JobOutcome
import com.ingsisbry.snippet.application.command.process.ProcessSnippetJob
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.PersistenceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SnippetJobConsumerTest {
    private val stream = FakeJobStream()
    private val processed = mutableListOf<SnippetJob>()
    private val failed = mutableListOf<Pair<SnippetJob, String>>()
    private var answer: (SnippetJob) -> Result<SnippetError, JobOutcome> = { Result.Success(JobOutcome.PROCESSED) }
    private var failAnswer: Result<SnippetError, Unit> = Result.Success(Unit)
    private val properties = WorkerProperties(maxDeliveries = 5)

    private val consumer =
        SnippetJobConsumer(
            stream = stream,
            processSnippetJob =
                ProcessSnippetJob { job ->
                    processed += job
                    answer(job)
                },
            failSnippetJob =
                FailSnippetJob { job, reason ->
                    failed += job to reason
                    failAnswer
                },
            properties = properties,
            clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC),
        )

    @Test
    fun `a processed job is acknowledged`() {
        val entry = stream.publish(job())

        consumer.pollOnce()

        assertEquals(listOf(job()), processed)
        assertEquals(listOf(entry.id), stream.acked)
        assertTrue(stream.deadLetters.isEmpty())
    }

    @Test
    fun `a skipped job is acknowledged too`() {
        answer = { Result.Success(JobOutcome.SKIPPED) }
        val entry = stream.publish(job())

        consumer.pollOnce()

        assertEquals(listOf(entry.id), stream.acked)
    }

    @Test
    fun `a transient failure is left unacknowledged for a later retry`() {
        answer = { Result.Failure(LanguageServiceUnavailable("engine down")) }
        stream.publish(job())

        consumer.pollOnce()

        assertTrue(stream.acked.isEmpty())
        assertTrue(failed.isEmpty())
        assertTrue(stream.deadLetters.isEmpty())
    }

    @Test
    fun `a permanent failure marks the job failed, dead-letters it and acknowledges it`() {
        answer = { Result.Failure(InvalidInput("Unknown rule 'x'")) }
        val entry = stream.publish(job())

        consumer.pollOnce()

        assertEquals(listOf(job() to InvalidInput("Unknown rule 'x'").toString()), failed)
        assertEquals(listOf(entry.fields to InvalidInput("Unknown rule 'x'").toString()), stream.deadLetters)
        assertEquals(listOf(entry.id), stream.acked)
    }

    @Test
    fun `a malformed entry is dead-lettered and acknowledged without processing`() {
        val entry = stream.add(mapOf("type" to "something.else"))

        consumer.pollOnce()

        assertTrue(processed.isEmpty())
        assertEquals(listOf(entry.fields to SnippetJobConsumer.MALFORMED), stream.deadLetters)
        assertEquals(listOf(entry.id), stream.acked)
    }

    @Test
    fun `an exception from the use case leaves the entry pending`() {
        answer = { throw IllegalStateException("bug") }
        stream.publish(job())

        consumer.pollOnce()

        assertTrue(stream.acked.isEmpty())
    }

    @Test
    fun `a claimed entry is retried while it has deliveries left`() {
        val entry = stream.publish(job())
        stream.idle += entry.copy(deliveries = 5)

        consumer.claimIdle()

        assertEquals(listOf(job()), processed)
        assertEquals(listOf(entry.id), stream.acked)
    }

    @Test
    fun `a claimed entry past max-deliveries is given up without running it again`() {
        val entry = stream.publish(job())
        stream.idle += entry.copy(deliveries = 6)

        consumer.claimIdle()

        assertTrue(processed.isEmpty())
        assertEquals(listOf(job() to SnippetJobConsumer.MAX_DELIVERIES_EXCEEDED), failed)
        assertEquals(listOf(entry.fields to SnippetJobConsumer.MAX_DELIVERIES_EXCEEDED), stream.deadLetters)
        assertEquals(listOf(entry.id), stream.acked)
    }

    @Test
    fun `a job that can't be marked failed stays pending, so it is given up again later`() {
        failAnswer = Result.Failure(PersistenceUnavailable("db down"))
        val entry = stream.publish(job())
        stream.idle += entry.copy(deliveries = 6)

        consumer.claimIdle()

        assertTrue(stream.acked.isEmpty())
        assertTrue(stream.deadLetters.isEmpty())
    }

    @Test
    fun `on start the group is created and this consumer's own pending entries are replayed first`() {
        val first = stream.add(fieldsOf(job()))
        val second = stream.add(fieldsOf(job(SnippetTask.FORMAT)))
        stream.ownPending += listOf(first, second)
        // a pending entry that fails again stays pending; the replay still moves past it
        answer = { job ->
            val lint = SnippetTask.LINT in job.tasks
            if (lint) Result.Failure(LanguageServiceUnavailable("down")) else Result.Success(JobOutcome.PROCESSED)
        }

        assertTrue(consumer.prepare())

        assertTrue(stream.groupCreated)
        assertEquals(listOf(job(), job(SnippetTask.FORMAT)), processed)
        assertEquals(listOf(second.id), stream.acked)
    }

    @Test
    fun `start-up reports false while the stream is unreachable`() {
        stream.unreachable = true

        assertEquals(false, consumer.prepare())
    }

    @Test
    fun `the first poll claims idle entries before reading new ones`() {
        val claimed = stream.publish(job(SnippetTask.FORMAT))
        stream.idle += claimed.copy(deliveries = 2)
        stream.publish(job())

        consumer.pollOnce()

        assertEquals(listOf(job(SnippetTask.FORMAT), job()), processed)
    }

    private fun job(vararg tasks: SnippetTask = arrayOf(SnippetTask.LINT)): SnippetJob =
        SnippetJob(SNIPPET_ID, VERSION_ID, tasks.toSet(), JobReason.UPDATED)

    /** Plays the stream: [publish] queues a new entry; [ownPending] and [idle] are what Redis would hand back. */
    private class FakeJobStream : JobStream {
        private var nextId = 1
        private val fresh = ArrayDeque<StreamEntry>()
        val ownPending = mutableListOf<StreamEntry>()
        val idle = mutableListOf<StreamEntry>()
        val acked = mutableListOf<String>()
        val deadLetters = mutableListOf<Pair<Map<String, String>, String>>()
        var groupCreated = false
        var unreachable = false

        fun publish(job: SnippetJob): StreamEntry = add(fieldsOf(job))

        fun add(fields: Map<String, String>): StreamEntry = StreamEntry("${nextId++}-0", fields).also { fresh += it }

        override fun ensureGroup() {
            check(!unreachable) { "connection refused" }
            groupCreated = true
        }

        override fun readOwnPending(
            afterId: String,
            count: Int,
        ): List<StreamEntry> {
            val after = afterId.substringBefore('-').toInt()
            return ownPending.filter { it.id.substringBefore('-').toInt() > after }.take(count)
        }

        override fun readNew(
            count: Int,
            block: Duration,
        ): List<StreamEntry> = List(minOf(count, fresh.size)) { fresh.removeFirst() }

        override fun ack(id: String) {
            acked += id
            fresh.removeAll { it.id == id }
        }

        override fun claimIdle(
            minIdle: Duration,
            count: Int,
        ): List<StreamEntry> {
            val claimed = idle.take(count)
            idle.removeAll(claimed)
            fresh.removeAll { entry -> claimed.any { it.id == entry.id } }
            return claimed
        }

        override fun deadLetter(
            fields: Map<String, String>,
            error: String,
        ) {
            deadLetters += fields to error
        }
    }

    private companion object {
        fun fieldsOf(job: SnippetJob): Map<String, String> {
            val jobId = UUID.randomUUID()
            return SnippetJobMessage.toFields(job, jobId, Instant.EPOCH)
        }

        val SNIPPET_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val VERSION_ID: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    }
}
