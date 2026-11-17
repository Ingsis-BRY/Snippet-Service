package com.ingsisbry.snippet.adapter.persistence.analysis

import com.ingsisbry.snippet.adapter.persistence.json.PersistenceJson
import com.ingsisbry.snippet.adapter.persistence.json.inResult
import com.ingsisbry.snippet.adapter.persistence.snippet.CurrentVersionRow
import com.ingsisbry.snippet.adapter.persistence.snippet.JpaSnippetRepository
import com.ingsisbry.snippet.adapter.persistence.snippetversion.JpaSnippetVersionRepository
import com.ingsisbry.snippet.adapter.persistence.snippetversion.SnippetVersionEntity
import com.ingsisbry.snippet.adapter.persistence.tests.JpaSnippetTestRepository
import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.FormatStatus
import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.SnippetAnalysis
import com.ingsisbry.snippet.application.analysis.TestStatus
import com.ingsisbry.snippet.application.analysis.ValidationStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetCorrupted
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.analysis.SnippetAnalysisRepository
import com.ingsisbry.snippet.application.port.out.analysis.StaleVersion
import com.ingsisbry.snippet.application.port.out.analysis.VersionRef
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@Repository
@Suppress("TooManyFunctions")
class JpaSnippetAnalysisRepositoryAdapter(
    private val snippetRepository: JpaSnippetRepository,
    private val versionRepository: JpaSnippetVersionRepository,
    private val testRepository: JpaSnippetTestRepository,
    private val transactionTemplate: TransactionTemplate,
) : SnippetAnalysisRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun find(versionId: UUID): Result<SnippetError, SnippetAnalysis> {
        val version = versionRepository.findById(versionId).orElse(null)
        return if (version == null) {
            Result.Failure(SnippetCorrupted(versionId, "Version $versionId does not exist"))
        } else {
            Result.Success(version.toAnalysis())
        }
    }

    override fun findAll(versionIds: Collection<UUID>): Map<UUID, SnippetAnalysis> =
        if (versionIds.isEmpty()) {
            emptyMap()
        } else {
            versionRepository.findAllById(versionIds).associate { it.id to it.toAnalysis() }
        }

    override fun markOwnerPending(
        owner: String,
        tasks: Set<SnippetTask>,
        at: Instant,
    ): Result<SnippetError, List<VersionRef>> =
        transactionTemplate.inResult(logger, "mark the snippets of $owner pending") {
            val refs = snippetRepository.findCurrentVersionsByOwner(owner).map { it.toRef() }
            refs.map { it.versionId }.chunked(BATCH_SIZE).forEach { ids -> markPending(ids, tasks, at) }
            refs
        }

    override fun markAllPending(
        tasks: Set<SnippetTask>,
        at: Instant,
    ): Result<SnippetError, Unit> =
        transactionTemplate.inResult(logger, "mark every snippet pending") {
            var page = snippetRepository.findCurrentVersions(PageRequest.of(0, BATCH_SIZE))
            while (page.isNotEmpty()) {
                markPending(page.map { it.versionId }, tasks, at)
                if (SnippetTask.TEST in tasks) testRepository.markPendingForSnippets(page.map { it.snippetId }, at)
                page = snippetRepository.findCurrentVersionsAfter(page.last().snippetId, PageRequest.of(0, BATCH_SIZE))
            }
        }

    override fun currentVersions(
        afterSnippetId: UUID?,
        limit: Int,
    ): List<VersionRef> {
        val page = PageRequest.of(0, limit)
        val rows =
            if (afterSnippetId == null) {
                snippetRepository.findCurrentVersions(page)
            } else {
                snippetRepository.findCurrentVersionsAfter(afterSnippetId, page)
            }
        return rows.map { it.toRef() }
    }

    override fun currentVersionId(snippetId: UUID): UUID? {
        val snippet = snippetRepository.findById(snippetId).orElse(null)
        return snippet?.currentVersionId
    }

    override fun saveValidation(
        versionId: UUID,
        status: ValidationStatus,
        error: CodeIssue?,
    ): Result<SnippetError, Unit> =
        transactionTemplate.inResult(logger, "save the validation of $versionId") {
            versionRepository.updateValidation(versionId, status.name, PersistenceJson.writeIssue(error))
            Unit
        }

    override fun saveLint(
        versionId: UUID,
        status: LintStatus,
        issues: List<CodeIssue>,
    ): Result<SnippetError, Unit> =
        transactionTemplate.inResult(logger, "save the lint of $versionId") {
            versionRepository.updateLint(versionId, status.name, PersistenceJson.writeIssues(issues))
            Unit
        }

    override fun saveFormat(
        versionId: UUID,
        status: FormatStatus,
        formattedContentKey: String?,
    ): Result<SnippetError, Unit> =
        transactionTemplate.inResult(logger, "save the format of $versionId") {
            if (formattedContentKey == null) {
                versionRepository.updateFormatStatus(versionId, status.name)
            } else {
                versionRepository.updateFormat(versionId, status.name, formattedContentKey)
            }
            Unit
        }

    override fun touchRequested(
        versionIds: Collection<UUID>,
        at: Instant,
    ): Result<SnippetError, Unit> =
        transactionTemplate.inResult(logger, "touch ${versionIds.size} versions") {
            versionIds.chunked(BATCH_SIZE).forEach { ids -> versionRepository.touchRequested(ids, at) }
        }

    override fun findStale(
        before: Instant,
        limit: Int,
    ): List<StaleVersion> {
        val versions = versionRepository.findStale(before, PageRequest.of(0, limit))
        val withPendingTests =
            versions
                .map { it.snippetId }
                .distinct()
                .chunked(BATCH_SIZE)
                .flatMap { ids -> testRepository.findSnippetIdsWithStatus(ids, TestStatus.PENDING.name) }
                .toSet()
        return versions.map { version ->
            val testTask = if (version.snippetId in withPendingTests) setOf(SnippetTask.TEST) else emptySet()
            StaleVersion(ref = VersionRef(version.snippetId, version.id), tasks = version.pendingTasks() + testTask)
        }
    }

    private fun markPending(
        versionIds: List<UUID>,
        tasks: Set<SnippetTask>,
        at: Instant,
    ) {
        if (versionIds.isEmpty()) return
        if (SnippetTask.VALIDATE in tasks) versionRepository.markValidationPending(versionIds, at)
        if (SnippetTask.LINT in tasks) versionRepository.markLintPending(versionIds, at)
        if (SnippetTask.FORMAT in tasks) versionRepository.markFormatPending(versionIds, at)
    }

    private fun SnippetVersionEntity.pendingTasks(): Set<SnippetTask> =
        buildSet {
            if (validationStatus == PENDING) add(SnippetTask.VALIDATE)
            if (lintStatus == PENDING) add(SnippetTask.LINT)
            if (formatStatus == PENDING) add(SnippetTask.FORMAT)
        }

    private fun CurrentVersionRow.toRef(): VersionRef = VersionRef(snippetId, versionId)

    private companion object {
        const val BATCH_SIZE = 500
        const val PENDING = "PENDING"
    }
}

internal fun SnippetVersionEntity.toAnalysis(): SnippetAnalysis =
    SnippetAnalysis(
        validationStatus = ValidationStatus.valueOf(validationStatus),
        validationError = PersistenceJson.readIssue(validationError),
        lintStatus = LintStatus.valueOf(lintStatus),
        lintIssues = PersistenceJson.readIssues(lintIssues),
        formatStatus = FormatStatus.valueOf(formatStatus),
    )
