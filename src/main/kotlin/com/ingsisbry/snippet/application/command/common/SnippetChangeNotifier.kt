package com.ingsisbry.snippet.application.command.common

import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.job.SnippetJobPublisher
import com.ingsisbry.snippet.application.port.out.tests.SnippetTestRepository
import com.ingsisbry.snippet.domain.Snippet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Starts the background work a change needs (US#12, #15, #16). Best effort: the statuses are already PENDING in
 * the database, so a lost job is found again by the worker's sweeper, and a problem here never fails the write
 * that triggered it.
 */
@Service
class SnippetChangeNotifier(
    private val testRepository: SnippetTestRepository,
    private val publisher: SnippetJobPublisher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun created(snippet: Snippet) =
        publish(
            listOf(
                SnippetJob(
                    snippetId = snippet.id,
                    versionId = snippet.currentVersion.id,
                    tasks = setOf(SnippetTask.LINT, SnippetTask.FORMAT),
                    reason = JobReason.CREATED,
                ),
            ),
        )

    /** The tests ran against the previous version, so they go back to PENDING before they run again. */
    fun updated(snippet: Snippet) {
        testRepository.markPending(snippet.id).onFailure { error ->
            logger.warn("Could not mark the tests of snippet {} pending: {}", snippet.id, error)
        }
        publish(
            listOf(
                SnippetJob(
                    snippetId = snippet.id,
                    versionId = snippet.currentVersion.id,
                    tasks = setOf(SnippetTask.LINT, SnippetTask.FORMAT, SnippetTask.TEST),
                    reason = JobReason.UPDATED,
                ),
            ),
        )
    }

    /** A test was created or changed: only that test runs, against the snippet's current version. */
    fun testChanged(
        snippetId: UUID,
        versionId: UUID,
        testId: UUID,
    ) = publish(
        listOf(SnippetJob(snippetId, versionId, setOf(SnippetTask.TEST), JobReason.TESTS_CHANGED, listOf(testId))),
    )

    @Suppress("TooGenericExceptionCaught")
    fun publish(jobs: List<SnippetJob>) {
        if (jobs.isEmpty()) return
        try {
            publisher.publish(jobs)
        } catch (exception: RuntimeException) {
            logger.warn("Could not publish {} snippet jobs ({})", jobs.size, jobs.first().reason, exception)
        }
    }
}
