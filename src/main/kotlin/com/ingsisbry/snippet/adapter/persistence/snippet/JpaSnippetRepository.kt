package com.ingsisbry.snippet.adapter.persistence.snippet

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface JpaSnippetRepository :
    JpaRepository<SnippetEntity, UUID>,
    JpaSpecificationExecutor<SnippetEntity> {
    @Query("select s.id as snippetId, s.currentVersionId as versionId from SnippetEntity s where s.owner = :owner")
    fun findCurrentVersionsByOwner(
        @Param("owner") owner: String,
    ): List<CurrentVersionRow>

    @Query("select s.id as snippetId, s.currentVersionId as versionId from SnippetEntity s order by s.id")
    fun findCurrentVersions(pageable: Pageable): List<CurrentVersionRow>

    @Query(
        "select s.id as snippetId, s.currentVersionId as versionId from SnippetEntity s " +
            "where s.id > :after order by s.id",
    )
    fun findCurrentVersionsAfter(
        @Param("after") after: UUID,
        pageable: Pageable,
    ): List<CurrentVersionRow>
}

/** A snippet and the id of its current version. */
interface CurrentVersionRow {
    val snippetId: UUID
    val versionId: UUID
}
