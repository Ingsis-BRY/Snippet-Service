package com.ingsisbry.snippet.adapter.redis

import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.util.UUID

class SnippetJobMessageTest {
    private val job =
        SnippetJob(
            snippetId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            versionId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            tasks = setOf(SnippetTask.TEST, SnippetTask.LINT, SnippetTask.FORMAT),
            reason = JobReason.UPDATED,
        )
    private val jobId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val now = Instant.parse("2026-11-17T10:00:00Z")

    @Test
    fun `a job becomes the string fields of the stream contract`() {
        assertEquals(
            mapOf(
                "type" to "snippet.analysis.v1",
                "jobId" to jobId.toString(),
                "snippetId" to job.snippetId.toString(),
                "versionId" to job.versionId.toString(),
                "tasks" to "LINT,FORMAT,TEST",
                "reason" to "UPDATED",
                "testIds" to "",
                "enqueuedAt" to "2026-11-17T10:00:00Z",
            ),
            SnippetJobMessage.toFields(job, jobId, now),
        )
    }

    @Test
    fun `fields read back as the same job, test ids included`() {
        val testIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        val withTests = job.copy(testIds = testIds, reason = JobReason.TESTS_CHANGED)

        assertEquals(job, SnippetJobMessage.fromFields(SnippetJobMessage.toFields(job, jobId, now)))
        assertEquals(withTests, SnippetJobMessage.fromFields(SnippetJobMessage.toFields(withTests, jobId, now)))
    }

    @Test
    fun `the publishing request's trace id travels as an optional field`() {
        val fields = SnippetJobMessage.toFields(job, jobId, now, TRACE_ID)

        assertEquals(TRACE_ID, fields["traceId"])
        assertEquals(TRACE_ID, SnippetJobMessage.traceId(fields))
        assertEquals(job, SnippetJobMessage.fromFields(fields))
    }

    @Test
    fun `an entry without a trace id, from before the field existed, still reads as a job`() {
        val fields = SnippetJobMessage.toFields(job, jobId, now)

        assertNull(SnippetJobMessage.traceId(fields))
        assertEquals(job, SnippetJobMessage.fromFields(fields))
    }

    @ParameterizedTest
    @MethodSource("badTraceIds")
    fun `a trace id that is not a hex trace id is ignored, the job is not`(traceId: String) {
        val fields = SnippetJobMessage.toFields(job, jobId, now) + ("traceId" to traceId)

        assertNull(SnippetJobMessage.traceId(fields))
        assertEquals(job, SnippetJobMessage.fromFields(fields))
    }

    @ParameterizedTest
    @MethodSource("malformed")
    fun `malformed fields are rejected`(change: Map<String, String?>) {
        val fields = SnippetJobMessage.toFields(job, jobId, now).toMutableMap()
        change.forEach { (key, value) -> if (value == null) fields.remove(key) else fields[key] = value }

        assertNull(SnippetJobMessage.fromFields(fields))
    }

    companion object {
        const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"

        @JvmStatic
        fun badTraceIds(): List<String> = listOf("", "xyz", "4BF92F3577B34DA6A3CE929D0E0E4736", "abc\ninjected")

        @JvmStatic
        fun malformed(): List<Map<String, String?>> =
            listOf(
                mapOf("type" to "snippet.analysis.v2"),
                mapOf("type" to null),
                mapOf("snippetId" to "not-a-uuid"),
                mapOf("versionId" to null),
                mapOf("tasks" to ""),
                mapOf("tasks" to "LINT,DANCE"),
                mapOf("reason" to "BORED"),
                mapOf("reason" to null),
                mapOf("testIds" to "nope"),
            )
    }
}
