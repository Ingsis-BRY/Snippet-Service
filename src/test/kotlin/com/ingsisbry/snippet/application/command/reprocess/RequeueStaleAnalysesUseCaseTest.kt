package com.ingsisbry.snippet.application.command.reprocess

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.analysis.StaleVersion
import com.ingsisbry.snippet.application.port.out.analysis.VersionRef
import com.ingsisbry.snippet.support.FakeSnippetAnalysisRepository
import com.ingsisbry.snippet.support.FakeSnippetJobPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RequeueStaleAnalysesUseCaseTest {
    private val analysis = FakeSnippetAnalysisRepository()
    private val publisher = FakeSnippetJobPublisher()
    private val useCase =
        RequeueStaleAnalysesUseCase(
            analysis,
            publisher,
            Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC),
            Duration.ofMinutes(10),
        )

    @Test
    fun `stale versions are published again with their pending tasks and touched`() {
        val ref = VersionRef(UUID.randomUUID(), UUID.randomUUID())
        val tasks = setOf(SnippetTask.LINT, SnippetTask.TEST)
        analysis.stale = listOf(StaleVersion(ref, tasks))

        assertEquals(Result.Success(1), useCase.execute())
        assertEquals(
            listOf(SnippetJob(ref.snippetId, ref.versionId, tasks, JobReason.SWEEP)),
            publisher.published,
        )
        assertEquals(listOf(ref.versionId), analysis.touched)
    }

    @Test
    fun `nothing stale, nothing published`() {
        assertEquals(Result.Success(0), useCase.execute())
        assertTrue(publisher.published.isEmpty())
        assertTrue(analysis.touched.isEmpty())
    }
}
