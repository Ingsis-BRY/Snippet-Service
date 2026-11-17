package com.ingsisbry.snippet.adapter.persistence.snippetversion

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

@Suppress("TooManyFunctions")
interface JpaSnippetVersionRepository : JpaRepository<SnippetVersionEntity, UUID> {
    fun findAllBySnippetId(snippetId: UUID): List<SnippetVersionEntity>

    fun findAllByOriginalContentKeyIn(keys: Collection<String>): List<SnippetVersionEntity>

    fun findAllByFormattedContentKeyIn(keys: Collection<String>): List<SnippetVersionEntity>

    @Modifying
    @Query(
        "update SnippetVersionEntity v set v.validationStatus = :status, v.validationError = :error " +
            "where v.id = :id",
    )
    fun updateValidation(
        @Param("id") id: UUID,
        @Param("status") status: String,
        @Param("error") error: String?,
    ): Int

    @Modifying
    @Query("update SnippetVersionEntity v set v.lintStatus = :status, v.lintIssues = :issues where v.id = :id")
    fun updateLint(
        @Param("id") id: UUID,
        @Param("status") status: String,
        @Param("issues") issues: String?,
    ): Int

    @Modifying
    @Query("update SnippetVersionEntity v set v.formatStatus = :status where v.id = :id")
    fun updateFormatStatus(
        @Param("id") id: UUID,
        @Param("status") status: String,
    ): Int

    @Modifying
    @Query("update SnippetVersionEntity v set v.formatStatus = :status, v.formattedContentKey = :key where v.id = :id")
    fun updateFormat(
        @Param("id") id: UUID,
        @Param("status") status: String,
        @Param("key") key: String,
    ): Int

    @Modifying
    @Query("update SnippetVersionEntity v set v.analysisRequestedAt = :at where v.id in :ids")
    fun touchRequested(
        @Param("ids") ids: Collection<UUID>,
        @Param("at") at: Instant,
    ): Int

    @Modifying
    @Query(
        "update SnippetVersionEntity v set v.validationStatus = 'PENDING', v.analysisRequestedAt = :at " +
            "where v.id in :ids",
    )
    fun markValidationPending(
        @Param("ids") ids: Collection<UUID>,
        @Param("at") at: Instant,
    ): Int

    @Modifying
    @Query("update SnippetVersionEntity v set v.lintStatus = 'PENDING', v.analysisRequestedAt = :at where v.id in :ids")
    fun markLintPending(
        @Param("ids") ids: Collection<UUID>,
        @Param("at") at: Instant,
    ): Int

    @Modifying
    @Query(
        "update SnippetVersionEntity v set v.formatStatus = 'PENDING', v.analysisRequestedAt = :at " +
            "where v.id in :ids",
    )
    fun markFormatPending(
        @Param("ids") ids: Collection<UUID>,
        @Param("at") at: Instant,
    ): Int

    /** Current versions waiting for some analysis, requested long ago or never. */
    @Query(
        "select v from SnippetVersionEntity v " +
            "where v.id in (select s.currentVersionId from SnippetEntity s) " +
            "and (v.analysisRequestedAt is null or v.analysisRequestedAt < :before) " +
            "and (v.validationStatus = 'PENDING' or v.lintStatus = 'PENDING' or v.formatStatus = 'PENDING' " +
            "or exists (select t.id from SnippetTestEntity t " +
            "where t.snippetId = v.snippetId and t.status = 'PENDING')) " +
            "order by v.id",
    )
    fun findStale(
        @Param("before") before: Instant,
        pageable: Pageable,
    ): List<SnippetVersionEntity>
}
