package com.ingsisbry.snippet.application.job

import java.util.UUID

enum class SnippetTask { VALIDATE, LINT, FORMAT, TEST }

enum class JobReason {
    CREATED,
    UPDATED,
    LINT_RULES_CHANGED,
    FORMAT_RULES_CHANGED,
    TESTS_CHANGED,
    PARSER_UPGRADED,
    SWEEP,
}

/** Background work on one snippet version. */
data class SnippetJob(
    val snippetId: UUID,
    val versionId: UUID,
    val tasks: Set<SnippetTask>,
    val reason: JobReason,
    /** Empty = all tests of the snippet. */
    val testIds: List<UUID> = emptyList(),
)
