package com.ingsisbry.snippet.adapter.persistence.tests

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface JpaSnippetTestRepository : JpaRepository<SnippetTestEntity, UUID> {
    fun findAllBySnippetIdOrderByCreatedAtAscIdAsc(snippetId: UUID): List<SnippetTestEntity>

    fun findByIdAndSnippetId(
        id: UUID,
        snippetId: UUID,
    ): SnippetTestEntity?

    fun countBySnippetId(snippetId: UUID): Long

    fun deleteAllBySnippetId(snippetId: UUID)

    @Query("select distinct t.snippetId from SnippetTestEntity t where t.snippetId in :ids and t.status = :status")
    fun findSnippetIdsWithStatus(
        @Param("ids") snippetIds: Collection<UUID>,
        @Param("status") status: String,
    ): List<UUID>

    @Modifying
    @Query("update SnippetTestEntity t set t.status = 'PENDING', t.updatedAt = :at where t.snippetId = :snippetId")
    fun markPending(
        @Param("snippetId") snippetId: UUID,
        @Param("at") at: Instant,
    ): Int

    @Modifying
    @Query("update SnippetTestEntity t set t.status = 'PENDING', t.updatedAt = :at where t.snippetId in :snippetIds")
    fun markPendingForSnippets(
        @Param("snippetIds") snippetIds: Collection<UUID>,
        @Param("at") at: Instant,
    ): Int

    @Modifying
    @Query(
        "update SnippetTestEntity t set t.status = :status, t.actualOutputs = :outputs, t.error = :error, " +
            "t.lastRunVersionId = :versionId, t.lastRunAt = :at where t.id = :id",
    )
    fun saveResult(
        @Param("id") id: UUID,
        @Param("status") status: String,
        @Param("outputs") outputs: String,
        @Param("error") error: String?,
        @Param("versionId") versionId: UUID,
        @Param("at") at: Instant,
    ): Int
}
