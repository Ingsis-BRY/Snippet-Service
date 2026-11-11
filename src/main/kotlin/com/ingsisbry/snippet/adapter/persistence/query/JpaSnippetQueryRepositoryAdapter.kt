package com.ingsisbry.snippet.adapter.persistence.query

import com.ingsisbry.snippet.adapter.persistence.query.filter.SnippetFilterRegistry
import com.ingsisbry.snippet.adapter.persistence.query.mapper.SnippetListItemMapper
import com.ingsisbry.snippet.adapter.persistence.query.sorting.SnippetSortFactory
import com.ingsisbry.snippet.adapter.persistence.query.specification.SnippetAccessSpecifications
import com.ingsisbry.snippet.adapter.persistence.snippet.JpaSnippetRepository
import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import com.ingsisbry.snippet.adapter.persistence.snippetversion.JpaSnippetVersionRepository
import com.ingsisbry.snippet.adapter.persistence.snippetversion.SnippetVersionEntity
import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetCorrupted
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.port.out.query.SnippetMetadata
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

    override fun findById(id: UUID): Result<SnippetError, Snippet> =
        loadRecords(id).flatMap { (entity, version) ->
            contentStorage.retrieve(version.originalContentKey).flatMap { originalContent ->
                retrieveFormattedContent(version).map { formattedContent ->
                    Snippet(
                        id = entity.id,
                        name = entity.name,
                        description = entity.description,
                        language = entity.language,
                        languageVersion = entity.languageVersion,
                        owner = entity.owner,
                        currentVersion =
                            SnippetVersion(
                                id = version.id,
                                snippetId = version.snippetId,
                                versionNumber = version.versionNumber,
                                originalContent = originalContent,
                                formattedContent = formattedContent,
                                createdAt = version.createdAt,
                            ),
                    )
                }
            }
        }

    override fun findReferencedContentKeys(keys: Collection<String>): Set<String> =
        versionRepository.findAllByOriginalContentKeyIn(keys).map { it.originalContentKey }.toSet() +
            versionRepository.findAllByFormattedContentKeyIn(keys).mapNotNull { it.formattedContentKey }

    override fun findMetadataById(id: UUID): Result<SnippetError, SnippetMetadata> =
        loadRecords(id).map { (entity, version) ->
            SnippetMetadata(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                language = entity.language,
                languageVersion = entity.languageVersion,
                owner = entity.owner,
                currentVersionNumber = version.versionNumber,
            )
        }

    private fun loadRecords(id: UUID): Result<SnippetError, Pair<SnippetEntity, SnippetVersionEntity>> {
        val entity =
            repository.findById(id).orElse(null)
                ?: return Result.Failure(SnippetNotFound(id))

        val version = versionRepository.findById(entity.currentVersionId).orElse(null)

        return if (version == null) {
            Result.Failure(
                SnippetCorrupted(
                    id = id,
                    reason = "Snippet references missing version ${entity.currentVersionId}",
                ),
            )
        } else {
            Result.Success(entity to version)
        }
    }

    private fun retrieveFormattedContent(version: SnippetVersionEntity): Result<SnippetError, String?> =
        version.formattedContentKey
            ?.let { key -> contentStorage.retrieve(key) }
            ?: Result.Success(null)
}
