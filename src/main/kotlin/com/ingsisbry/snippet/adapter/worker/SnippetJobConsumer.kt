package com.ingsisbry.snippet.adapter.worker

import com.ingsisbry.snippet.adapter.redis.SnippetJobMessage
import com.ingsisbry.snippet.application.command.process.FailSnippetJob
import com.ingsisbry.snippet.application.command.process.ProcessSnippetJob
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.isTransient
import com.ingsisbry.snippet.application.job.SnippetJob
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Consumes `snippet.analysis` in the `snippet-workers` group on one background thread.
 *
 * - Success: acknowledged.
 * - Transient failure (engine, database, blob storage down): left unacknowledged. Once it has been idle for
 *   `claim-min-idle`, the periodic claim takes it over and runs it again.
 * - Permanent failure, a malformed entry, or more than `max-deliveries` deliveries: its pending tasks are marked
 *   failed, the entry is copied to the dead-letter stream and acknowledged, and the worker moves on.
 * - On start, entries this consumer had received but not acknowledged before it stopped are replayed first.
 */
@Component
@Profile("worker")
class SnippetJobConsumer(
    private val stream: JobStream,
    private val processSnippetJob: ProcessSnippetJob,
    private val failSnippetJob: FailSnippetJob,
    private val properties: WorkerProperties,
    private val clock: Clock,
) : SmartLifecycle {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var nextClaimAt: Instant = Instant.MIN

    override fun start() {
        running = true
        // not a daemon: it is what keeps the worker process alive
        thread = Thread(::consume, "snippet-job-consumer").apply { start() }
    }

    /** The loop sees the flag after its current read, which blocks for at most `worker.block`. */
    override fun stop() {
        running = false
        thread?.join(properties.block.toMillis() + STOP_MARGIN_MS)
        thread = null
    }

    override fun isRunning(): Boolean = running

    @Suppress("TooGenericExceptionCaught")
    private fun consume() {
        var ready = false
        while (running && !ready) {
            ready = prepare()
            if (!ready) pause()
        }
        while (running) {
            try {
                pollOnce()
            } catch (exception: RuntimeException) {
                logger.warn("Reading the job stream failed; retrying: {}", exception.message)
                pause()
            }
        }
        logger.info("Job consumer stopped")
    }

    /** Creates the group and replays this consumer's unacknowledged entries; false while Redis is unreachable. */
    @Suppress("TooGenericExceptionCaught")
    fun prepare(): Boolean =
        try {
            stream.ensureGroup()
            replayOwnPending()
            logger.info("Consuming {} as {} in group {}", properties.stream, properties.consumer, properties.group)
            true
        } catch (exception: RuntimeException) {
            logger.warn("The job stream is not reachable yet: {}", exception.message)
            false
        }

    fun replayOwnPending() {
        var afterId = FROM_START
        do {
            val entries = stream.readOwnPending(afterId, properties.batchSize)
            entries.forEach(::handle)
            afterId = entries.lastOrNull()?.id ?: afterId
        } while (entries.isNotEmpty())
    }

    /** One turn of the loop: claim idle entries when it is time, then read (and wait for) new ones. */
    fun pollOnce() {
        val now = Instant.now(clock)
        if (!now.isBefore(nextClaimAt)) {
            nextClaimAt = now.plus(properties.claimInterval)
            claimIdle()
        }
        stream.readNew(properties.batchSize, properties.block).forEach(::handle)
    }

    fun claimIdle() {
        var round = 0
        do {
            val claimed = stream.claimIdle(properties.claimMinIdle, CLAIM_BATCH)
            claimed.forEach { entry ->
                if (entry.deliveries > properties.maxDeliveries) {
                    giveUp(entry, SnippetJobMessage.fromFields(entry.fields), MAX_DELIVERIES_EXCEEDED)
                } else {
                    handle(entry)
                }
            }
            round++
        } while (claimed.isNotEmpty() && round < MAX_CLAIM_ROUNDS)
    }

    @Suppress("TooGenericExceptionCaught")
    fun handle(entry: StreamEntry) {
        val job = SnippetJobMessage.fromFields(entry.fields)
        if (job == null) {
            giveUp(entry, null, MALFORMED)
            return
        }
        try {
            when (val result = processSnippetJob.execute(job)) {
                is Result.Success -> stream.ack(entry.id)
                is Result.Failure ->
                    if (result.error.isTransient()) {
                        logger.warn("Job {} will be retried: {}", jobId(entry), result.error)
                    } else {
                        giveUp(entry, job, result.error.toString())
                    }
            }
        } catch (exception: RuntimeException) {
            // left unacknowledged: the claim retries it, and max-deliveries ends it if it keeps failing
            logger.error("Job {} for snippet {} failed unexpectedly", jobId(entry), job.snippetId, exception)
        }
    }

    /**
     * Marks the job's pending tasks failed, then dead-letters and acknowledges it. If the marks can't be saved
     * the entry stays pending, so it is given up again on a later claim instead of staying PENDING forever.
     */
    private fun giveUp(
        entry: StreamEntry,
        job: SnippetJob?,
        reason: String,
    ) {
        val marked =
            job == null ||
                failSnippetJob.execute(job, reason).fold(
                    onSuccess = { true },
                    onFailure = { error ->
                        logger.warn("Could not mark job {} failed, will retry: {}", jobId(entry), error)
                        false
                    },
                )
        if (marked) {
            logger.warn("Giving up job {} (snippet {}): {}", jobId(entry), job?.snippetId, reason)
            stream.deadLetter(entry.fields, reason)
            stream.ack(entry.id)
        }
    }

    private fun pause() {
        try {
            Thread.sleep(RETRY_PAUSE_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            running = false
        }
    }

    companion object {
        /** For the logs: the producer's job id, or the stream id of a malformed entry. */
        private fun jobId(entry: StreamEntry): String = entry.fields[SnippetJobMessage.FIELD_JOB_ID] ?: entry.id

        const val MALFORMED = "malformed message"
        const val MAX_DELIVERIES_EXCEEDED = "max deliveries exceeded"
        private const val FROM_START = "0"
        private const val CLAIM_BATCH = 50
        private const val MAX_CLAIM_ROUNDS = 20
        private const val RETRY_PAUSE_MS = 5_000L
        private const val STOP_MARGIN_MS = 5_000L
    }
}
