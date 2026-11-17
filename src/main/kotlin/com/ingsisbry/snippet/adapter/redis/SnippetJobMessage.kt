package com.ingsisbry.snippet.adapter.redis

import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import java.time.Instant
import java.util.UUID

/**
 * The fields of one entry of the `snippet.analysis` stream. Every value is a string; lists are comma separated
 * (`tasks` = `LINT,FORMAT`, `testIds` = `""` for all the snippet's tests).
 */
object SnippetJobMessage {
    const val TYPE = "snippet.analysis.v1"

    const val FIELD_TYPE = "type"
    const val FIELD_JOB_ID = "jobId"
    const val FIELD_SNIPPET_ID = "snippetId"
    const val FIELD_VERSION_ID = "versionId"
    const val FIELD_TASKS = "tasks"
    const val FIELD_REASON = "reason"
    const val FIELD_TEST_IDS = "testIds"
    const val FIELD_ENQUEUED_AT = "enqueuedAt"

    fun toFields(
        job: SnippetJob,
        jobId: UUID,
        now: Instant,
    ): Map<String, String> =
        linkedMapOf(
            FIELD_TYPE to TYPE,
            FIELD_JOB_ID to jobId.toString(),
            FIELD_SNIPPET_ID to job.snippetId.toString(),
            FIELD_VERSION_ID to job.versionId.toString(),
            // enum order, so the same job always reads the same in the stream
            FIELD_TASKS to job.tasks.sorted().joinToString(SEPARATOR) { it.name },
            FIELD_REASON to job.reason.name,
            FIELD_TEST_IDS to job.testIds.joinToString(SEPARATOR),
            FIELD_ENQUEUED_AT to now.toString(),
        )

    /** Null for anything that is not a well-formed job of this version of the message. */
    fun fromFields(fields: Map<String, String>): SnippetJob? =
        if (fields[FIELD_TYPE] != TYPE) {
            null
        } else {
            runCatching { parse(fields) }.getOrNull()
        }

    private fun parse(fields: Map<String, String>): SnippetJob? {
        val tasks = split(fields[FIELD_TASKS]).map { SnippetTask.valueOf(it) }.toSet()
        val snippetId = fields[FIELD_SNIPPET_ID]?.let { UUID.fromString(it) }
        val versionId = fields[FIELD_VERSION_ID]?.let { UUID.fromString(it) }
        val reason = fields[FIELD_REASON]?.let { JobReason.valueOf(it) }
        return when {
            snippetId == null || versionId == null -> null
            reason == null || tasks.isEmpty() -> null
            else ->
                SnippetJob(
                    snippetId = snippetId,
                    versionId = versionId,
                    tasks = tasks,
                    reason = reason,
                    testIds = split(fields[FIELD_TEST_IDS]).map { UUID.fromString(it) },
                )
        }
    }

    private fun split(value: String?): List<String> =
        value
            .orEmpty()
            .split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private const val SEPARATOR = ","
}
