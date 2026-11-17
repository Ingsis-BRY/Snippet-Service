package com.ingsisbry.snippet.application.command.reprocess

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.port.out.analysis.SnippetAnalysisRepository
import com.ingsisbry.snippet.application.port.out.job.SnippetJobPublisher
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * [staleAfter] must be longer than a job normally waits in the stream, or a job still queued would be
 * published twice (harmless, since jobs are idempotent, but wasted work).
 *
 * Not a Spring bean on its own: the worker configuration builds it with the configured `stale-after`.
 */
class RequeueStaleAnalysesUseCase(
    private val analysis: SnippetAnalysisRepository,
    private val publisher: SnippetJobPublisher,
    private val clock: Clock,
    private val staleAfter: Duration,
) : RequeueStaleAnalyses {
    override fun execute(): Result<SnippetError, Int> {
        val now = Instant.now(clock)
        val stale = analysis.findStale(now.minus(staleAfter), BATCH_SIZE)
        if (stale.isEmpty()) return Result.Success(0)
        publisher.publish(stale.map { SnippetJob(it.ref.snippetId, it.ref.versionId, it.tasks, JobReason.SWEEP) })
        // the next sweep leaves them alone until they are stale again
        return analysis.touchRequested(stale.map { it.ref.versionId }, now).map { stale.size }
    }

    companion object {
        const val BATCH_SIZE = 200
    }
}
