package com.ingsisbry.snippet.application.command.reprocess

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.analysis.SnippetAnalysisRepository
import com.ingsisbry.snippet.application.port.out.job.SnippetJobPublisher
import com.ingsisbry.snippet.application.port.out.language.LanguageCatalog
import com.ingsisbry.snippet.application.port.out.transaction.TransactionRunner
import com.ingsisbry.snippet.application.port.out.worker.WorkerStateRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * The state row and the pending marks change in one transaction, so a crash can't leave the new version stored
 * with the snippets unmarked. Publishing happens after the commit; if it is cut short, the rows are already
 * pending and the sweeper requeues them.
 */
@Service
class RevalidateOnParserUpgradeUseCase(
    private val catalog: LanguageCatalog,
    private val state: WorkerStateRepository,
    private val analysis: SnippetAnalysisRepository,
    private val transactions: TransactionRunner,
    private val publisher: SnippetJobPublisher,
    private val clock: Clock,
) : RevalidateOnParserUpgrade {
    override fun execute(): Result<SnippetError, ParserCheck> =
        catalog.languages().flatMap { languages ->
            val libraryVersion = languages.firstOrNull { it.language == LANGUAGE }?.libraryVersion
            if (libraryVersion == null) {
                Result.Success(ParserCheck.UNKNOWN)
            } else {
                state.get(STATE_KEY).flatMap { seen ->
                    if (seen == libraryVersion) Result.Success(ParserCheck.UNCHANGED) else upgrade(seen, libraryVersion)
                }
            }
        }

    private fun upgrade(
        seen: String?,
        libraryVersion: String,
    ): Result<SnippetError, ParserCheck> {
        // the first run also analyses the snippets created before the background analysis existed
        val tasks = if (seen == null) FIRST_RUN_TASKS else UPGRADE_TASKS
        return transactions
            .inTransaction {
                state.compareAndSet(STATE_KEY, seen, libraryVersion).flatMap { won ->
                    if (won) analysis.markAllPending(tasks, Instant.now(clock)).map { true } else Result.Success(false)
                }
            }.map { won ->
                if (won) {
                    requeueAll(tasks)
                    ParserCheck.REQUEUED
                } else {
                    ParserCheck.TAKEN_BY_ANOTHER_WORKER
                }
            }
    }

    private fun requeueAll(tasks: Set<SnippetTask>) {
        var page = analysis.currentVersions(null, PAGE_SIZE)
        while (page.isNotEmpty()) {
            publisher.publish(page.map { SnippetJob(it.snippetId, it.versionId, tasks, JobReason.PARSER_UPGRADED) })
            val last = page.last().snippetId
            page = if (page.size < PAGE_SIZE) emptyList() else analysis.currentVersions(last, PAGE_SIZE)
        }
    }

    companion object {
        const val LANGUAGE = "printscript"
        const val STATE_KEY = "engine.library-version.$LANGUAGE"
        const val PAGE_SIZE = 500
        val UPGRADE_TASKS: Set<SnippetTask> =
            setOf(SnippetTask.VALIDATE, SnippetTask.LINT, SnippetTask.FORMAT, SnippetTask.TEST)
        val FIRST_RUN_TASKS: Set<SnippetTask> = UPGRADE_TASKS
    }
}
