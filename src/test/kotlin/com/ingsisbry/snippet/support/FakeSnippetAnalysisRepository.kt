package com.ingsisbry.snippet.support

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.FormatStatus
import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.SnippetAnalysis
import com.ingsisbry.snippet.application.analysis.ValidationStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetCorrupted
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.analysis.SnippetAnalysisRepository
import com.ingsisbry.snippet.application.port.out.analysis.StaleVersion
import com.ingsisbry.snippet.application.port.out.analysis.VersionRef
import java.time.Instant
import java.util.UUID

/** In-memory analyses; [owners] says whose snippet each current version is. */
@Suppress("TooManyFunctions")
class FakeSnippetAnalysisRepository : SnippetAnalysisRepository {
    val analyses = mutableMapOf<UUID, SnippetAnalysis>()
    val current = linkedMapOf<UUID, UUID>()
    val owners = mutableMapOf<UUID, String>()
    val pendingMarks = mutableListOf<Triple<String, Set<SnippetTask>, Instant>>()
    val allPendingMarks = mutableListOf<Set<SnippetTask>>()
    val validationErrors = mutableMapOf<UUID, CodeIssue?>()
    val formattedKeys = mutableMapOf<UUID, String>()
    val touched = mutableListOf<UUID>()
    var stale: List<StaleVersion> = emptyList()
    var failure: SnippetError? = null

    /** Makes every save fail with this error, as a database outage would. */
    var saveFailure: SnippetError? = null

    fun addSnippet(
        snippetId: UUID,
        versionId: UUID,
        owner: String,
        analysis: SnippetAnalysis = SnippetAnalysis.NEW,
    ) {
        current[snippetId] = versionId
        owners[snippetId] = owner
        analyses[versionId] = analysis
    }

    override fun find(versionId: UUID): Result<SnippetError, SnippetAnalysis> =
        analyses[versionId]?.let { Result.Success(it) }
            ?: Result.Failure(SnippetCorrupted(versionId, "no version"))

    override fun findAll(versionIds: Collection<UUID>): Map<UUID, SnippetAnalysis> =
        versionIds.mapNotNull { id -> analyses[id]?.let { id to it } }.toMap()

    override fun markOwnerPending(
        owner: String,
        tasks: Set<SnippetTask>,
        at: Instant,
    ): Result<SnippetError, List<VersionRef>> {
        failure?.let { return Result.Failure(it) }
        pendingMarks += Triple(owner, tasks, at)
        val refs =
            current
                .filterKeys { owners[it] == owner }
                .map { (snippetId, versionId) -> VersionRef(snippetId, versionId) }
        refs.forEach { ref -> analyses[ref.versionId] = pending(analyses.getValue(ref.versionId), tasks) }
        return Result.Success(refs)
    }

    override fun markAllPending(
        tasks: Set<SnippetTask>,
        at: Instant,
    ): Result<SnippetError, Unit> {
        failure?.let { return Result.Failure(it) }
        allPendingMarks += tasks
        current.values.forEach { versionId -> analyses[versionId]?.let { analyses[versionId] = pending(it, tasks) } }
        return Result.Success(Unit)
    }

    /** Keyset paging by snippet id, like the database. */
    override fun currentVersions(
        afterSnippetId: UUID?,
        limit: Int,
    ): List<VersionRef> =
        current
            .map { (snippetId, versionId) -> VersionRef(snippetId, versionId) }
            .sortedBy { it.snippetId }
            .filter { afterSnippetId == null || it.snippetId > afterSnippetId }
            .take(limit)

    override fun currentVersionId(snippetId: UUID): UUID? = current[snippetId]

    override fun saveValidation(
        versionId: UUID,
        status: ValidationStatus,
        error: CodeIssue?,
    ): Result<SnippetError, Unit> =
        save(versionId) {
            validationErrors[versionId] = error
            it.copy(validationStatus = status, validationError = error)
        }

    override fun saveLint(
        versionId: UUID,
        status: LintStatus,
        issues: List<CodeIssue>,
    ): Result<SnippetError, Unit> = save(versionId) { it.copy(lintStatus = status, lintIssues = issues) }

    override fun saveFormat(
        versionId: UUID,
        status: FormatStatus,
        formattedContentKey: String?,
    ): Result<SnippetError, Unit> =
        save(versionId) {
            formattedContentKey?.let { key -> formattedKeys[versionId] = key }
            it.copy(formatStatus = status)
        }

    override fun touchRequested(
        versionIds: Collection<UUID>,
        at: Instant,
    ): Result<SnippetError, Unit> {
        touched += versionIds
        return Result.Success(Unit)
    }

    override fun findStale(
        before: Instant,
        limit: Int,
    ): List<StaleVersion> = stale.take(limit)

    private fun save(
        versionId: UUID,
        change: (SnippetAnalysis) -> SnippetAnalysis,
    ): Result<SnippetError, Unit> {
        saveFailure?.let { return Result.Failure(it) }
        analyses[versionId] = change(analyses[versionId] ?: SnippetAnalysis.NEW)
        return Result.Success(Unit)
    }

    private fun pending(
        analysis: SnippetAnalysis,
        tasks: Set<SnippetTask>,
    ): SnippetAnalysis =
        analysis.copy(
            validationStatus =
                if (SnippetTask.VALIDATE in tasks) ValidationStatus.PENDING else analysis.validationStatus,
            lintStatus = if (SnippetTask.LINT in tasks) LintStatus.PENDING else analysis.lintStatus,
            formatStatus = if (SnippetTask.FORMAT in tasks) FormatStatus.PENDING else analysis.formatStatus,
        )
}
