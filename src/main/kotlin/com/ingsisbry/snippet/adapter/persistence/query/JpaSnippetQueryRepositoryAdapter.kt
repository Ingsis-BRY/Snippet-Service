package com.ingsisbry.snippet.adapter.persistence.query

import com.ingsisbry.snippet.adapter.persistence.query.filter.SnippetFilterRegistry
import com.ingsisbry.snippet.adapter.persistence.query.mapper.SnippetListItemMapper
import com.ingsisbry.snippet.adapter.persistence.query.sorting.SnippetSortFactory
import com.ingsisbry.snippet.adapter.persistence.query.specification.SnippetAccessSpecifications
import com.ingsisbry.snippet.adapter.persistence.snippet.JpaSnippetRepository
import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import com.ingsisbry.snippet.adapter.persistence.snippetversion.JpaSnippetVersionRepository
import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.application.query.list.ListSnippetsQuery
import com.ingsisbry.snippet.application.query.list.SnippetListItem
import com.ingsisbry.snippet.application.query.list.SnippetRelationship
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetVersion
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class JpaSnippetQueryRepositoryAdapter(
    private val repository: JpaSnippetRepository,
    private val versionRepository: JpaSnippetVersionRepository,
    private val filterRegistry: SnippetFilterRegistry,
    private val contentStorage: SnippetContentStorage,
) : SnippetQueryRepository {
    @Transactional(readOnly = true)
    override fun findAll(query: ListSnippetsQuery): PageResult<SnippetListItem> {
        if (query.relationship == SnippetRelationship.SHARED) {
            return PageResult.Companion.empty(
                page = query.page,
                size = query.size,
            )
        }

        val pageable =
            PageRequest.of(
                query.page,
                query.size,
                SnippetSortFactory.create(
                    query.sortBy,
                    query.direction,
                ),
            )

        val specification: Specification<SnippetEntity> =
            SnippetAccessSpecifications
                .belongsToUser(query.user)
                .and(filterRegistry.toSpecification(query.filters))

        val result = repository.findAll(specification, pageable)

        return PageResult(
            content = result.content.map(SnippetListItemMapper::toListItem),
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    override fun findById(id: UUID): Snippet? {
        val entity = repository.findById(id).orElse(null) ?: return null

        val versionEntity =
            versionRepository
                .findById(entity.currentVersionId)
                .orElseThrow {
                    IllegalStateException(
                        "Snippet ${entity.id} references missing version ${entity.currentVersionId}",
                    )
                }

        return Snippet(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            language = entity.language,
            languageVersion = entity.languageVersion,
            owner = entity.owner,
            currentVersion =
                SnippetVersion(
                    id = versionEntity.id,
                    snippetId = versionEntity.snippetId,
                    versionNumber = versionEntity.versionNumber,
                    originalContent =
                        contentStorage.retrieve(
                            versionEntity.originalContentKey,
                        ),
                    formattedContent =
                        versionEntity.formattedContentKey?.let(
                            contentStorage::retrieve,
                        ),
                    createdAt = versionEntity.createdAt,
                ),
        )
    }
}
