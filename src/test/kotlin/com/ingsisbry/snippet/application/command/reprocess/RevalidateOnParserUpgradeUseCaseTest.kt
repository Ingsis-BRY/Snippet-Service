package com.ingsisbry.snippet.application.command.reprocess

import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.ValidationStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.language.LanguageCatalog
import com.ingsisbry.snippet.application.port.out.language.LanguageInfo
import com.ingsisbry.snippet.application.port.out.transaction.TransactionRunner
import com.ingsisbry.snippet.application.port.out.worker.WorkerStateRepository
import com.ingsisbry.snippet.support.FakeSnippetAnalysisRepository
import com.ingsisbry.snippet.support.FakeSnippetJobPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RevalidateOnParserUpgradeUseCaseTest {
    private val analysis = FakeSnippetAnalysisRepository()
    private val publisher = FakeSnippetJobPublisher()
    private val state = FakeWorkerState()
    private var libraryVersion: String? = "1.1.3"
    private var catalogAnswer: Result<SnippetError, List<LanguageInfo>>? = null

    private val catalog =
        LanguageCatalog {
            catalogAnswer
                ?: Result.Success(listOf(printscript()))
        }
    private val transactions = RecordingTransactions()

    private val useCase =
        RevalidateOnParserUpgradeUseCase(
            catalog,
            state,
            analysis,
            transactions,
            publisher,
            Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC),
        )

    @Test
    fun `nothing happens while the library version is the one already seen`() {
        state.values[KEY] = "1.1.3"
        addSnippets(3)

        assertEquals(Result.Success(ParserCheck.UNCHANGED), useCase.execute())
        assertTrue(publisher.published.isEmpty())
        assertTrue(analysis.allPendingMarks.isEmpty())
    }

    @Test
    fun `a new version marks every snippet pending for validation, lint, format and tests and requeues them`() {
        state.values[KEY] = "1.1.2"
        val refs = addSnippets(3)

        assertEquals(Result.Success(ParserCheck.REQUEUED), useCase.execute())

        assertEquals("1.1.3", state.values[KEY])
        assertEquals(listOf(UPGRADE), analysis.allPendingMarks)
        assertEquals(1, transactions.runs)
        assertEquals(
            refs.map { (snippetId, versionId) ->
                SnippetJob(snippetId, versionId, UPGRADE, JobReason.PARSER_UPGRADED)
            },
            publisher.published.sortedBy { it.snippetId },
        )
        val anyVersion = refs.first().second
        assertEquals(ValidationStatus.PENDING, analysis.analyses.getValue(anyVersion).validationStatus)
        assertEquals(LintStatus.PENDING, analysis.analyses.getValue(anyVersion).lintStatus)
    }

    @Test
    fun `the first run does the same full check, back-filling snippets created before the background analysis`() {
        val refs = addSnippets(2)

        assertEquals(Result.Success(ParserCheck.REQUEUED), useCase.execute())

        val firstRun = UPGRADE
        assertEquals(listOf(firstRun), analysis.allPendingMarks)
        assertEquals(refs.size, publisher.published.size)
        assertTrue(publisher.published.all { it.tasks == firstRun })
    }

    @Test
    fun `every snippet is requeued even when there are more than one page of them`() {
        state.values[KEY] = "1.1.2"
        addSnippets(RevalidateOnParserUpgradeUseCase.PAGE_SIZE + 3)

        useCase.execute()

        assertEquals(
            RevalidateOnParserUpgradeUseCase.PAGE_SIZE + 3,
            publisher.published
                .map { it.snippetId }
                .toSet()
                .size,
        )
    }

    @Test
    fun `when another worker stored the version first, nothing is marked or published`() {
        state.values[KEY] = "1.1.2"
        state.loseNextRace = true
        addSnippets(2)

        assertEquals(Result.Success(ParserCheck.TAKEN_BY_ANOTHER_WORKER), useCase.execute())
        assertTrue(analysis.allPendingMarks.isEmpty())
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    fun `an engine without a library version or an engine that is down changes nothing`() {
        libraryVersion = null
        assertEquals(Result.Success(ParserCheck.UNKNOWN), useCase.execute())

        catalogAnswer = Result.Failure(LanguageServiceUnavailable("down"))
        assertEquals(Result.Failure(LanguageServiceUnavailable("down")), useCase.execute())

        assertTrue(state.values.isEmpty())
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    fun `when marking pending fails the transaction is rolled back and nothing is published`() {
        state.values[KEY] = "1.1.2"
        analysis.failure = LanguageServiceUnavailable("db")
        addSnippets(1)

        val result = useCase.execute()

        assertEquals(Result.Failure(LanguageServiceUnavailable("db")), result)
        assertEquals("1.1.2", state.values[KEY])
        assertTrue(publisher.published.isEmpty())
    }

    private fun printscript(): LanguageInfo {
        val versions = listOf("1.0", "1.1")
        return LanguageInfo("printscript", listOf("prs"), versions, "1.0", libraryVersion)
    }

    private fun addSnippets(count: Int): List<Pair<UUID, UUID>> =
        List(count) { UUID.randomUUID() to UUID.randomUUID() }
            .onEach { (snippetId, versionId) -> analysis.addSnippet(snippetId, versionId, "auth0|owner") }
            .sortedBy { it.first }

    /** Compare-and-set on a map; [loseNextRace] plays another worker that got there first. */
    private class FakeWorkerState : WorkerStateRepository {
        val values = mutableMapOf<String, String>()
        var loseNextRace = false

        override fun get(key: String): Result<SnippetError, String?> = Result.Success(values[key])

        override fun compareAndSet(
            key: String,
            expected: String?,
            new: String,
        ): Result<SnippetError, Boolean> {
            if (loseNextRace || values[key] != expected) return Result.Success(false)
            values[key] = new
            return Result.Success(true)
        }
    }

    /** Restores the state map on a failure, as a database rollback would. */
    private inner class RecordingTransactions : TransactionRunner {
        var runs = 0

        override fun <T> inTransaction(block: () -> Result<SnippetError, T>): Result<SnippetError, T> {
            runs++
            val before = state.values.toMap()
            return block().also { result ->
                if (result is Result.Failure) {
                    state.values.clear()
                    state.values.putAll(before)
                }
            }
        }
    }

    private companion object {
        const val KEY = "engine.library-version.printscript"
        val UPGRADE = setOf(SnippetTask.VALIDATE, SnippetTask.LINT, SnippetTask.FORMAT, SnippetTask.TEST)
    }
}
