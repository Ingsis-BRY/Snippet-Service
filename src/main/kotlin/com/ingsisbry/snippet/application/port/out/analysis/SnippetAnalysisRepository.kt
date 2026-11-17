package com.ingsisbry.snippet.application.port.out.analysis

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.FormatStatus
import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.SnippetAnalysis
import com.ingsisbry.snippet.application.analysis.ValidationStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.SnippetTask
import java.time.Instant
import java.util.UUID

data class VersionRef(
    val snippetId: UUID,
    val versionId: UUID,
)

data class StaleVersion(
    val ref: VersionRef,
    val tasks: Set<SnippetTask>,
)

/** The validation, lint and format results of each snippet version. */
@Suppress("TooManyFunctions")
interface SnippetAnalysisRepository {
    fun find(versionId: UUID): Result<SnippetError, SnippetAnalysis>

    fun findAll(versionIds: Collection<UUID>): Map<UUID, SnippetAnalysis>

    /**
     * Sets the given tasks' statuses to PENDING (TEST is ignored here) and analysis_requested_at = at,
     * for the owner's current versions.
     */
    fun markOwnerPending(
        owner: String,
        tasks: Set<SnippetTask>,
        at: Instant,
    ): Result<SnippetError, List<VersionRef>>

    /** Same for every current version of every snippet. */
    fun markAllPending(
        tasks: Set<SnippetTask>,
        at: Instant,
    ): Result<SnippetError, Unit>

    /** Current versions ordered by snippet id, keyset paging. */
    fun currentVersions(
        afterSnippetId: UUID?,
        limit: Int,
    ): List<VersionRef>

    /** Null when the snippet is gone. */
    fun currentVersionId(snippetId: UUID): UUID?

    fun saveValidation(
        versionId: UUID,
        status: ValidationStatus,
        error: CodeIssue?,
    ): Result<SnippetError, Unit>

    fun saveLint(
        versionId: UUID,
        status: LintStatus,
        issues: List<CodeIssue>,
    ): Result<SnippetError, Unit>

    /** A null [formattedContentKey] keeps the key already stored. */
    fun saveFormat(
        versionId: UUID,
        status: FormatStatus,
        formattedContentKey: String?,
    ): Result<SnippetError, Unit>

    fun touchRequested(
        versionIds: Collection<UUID>,
        at: Instant,
    ): Result<SnippetError, Unit>

    /**
     * Current versions with any PENDING status (or a PENDING test) whose analysis_requested_at is null or
     * before [before].
     */
    fun findStale(
        before: Instant,
        limit: Int,
    ): List<StaleVersion>
}
