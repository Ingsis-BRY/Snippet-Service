package com.ingsisbry.snippet.adapter.persistence

import com.ingsisbry.snippet.adapter.persistence.filter.SnippetFilterRegistry
import com.ingsisbry.snippet.adapter.persistence.mapper.SnippetListItemMapper
import com.ingsisbry.snippet.adapter.persistence.snippet.JpaSnippetRepository
import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import com.ingsisbry.snippet.adapter.persistence.sorting.SnippetSortFactory
import com.ingsisbry.snippet.adapter.persistence.specification.SnippetAccessSpecifications
import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.query.list.ListSnippetsQuery
import com.ingsisbry.snippet.application.query.list.SnippetListItem
import com.ingsisbry.snippet.application.query.list.SnippetRelationship
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JpaSnippetQueryRepositoryAdapter(
    private val repository: JpaSnippetRepository,
    private val filterRegistry: SnippetFilterRegistry,
) : SnippetQueryRepository {
    @Transactional(readOnly = true)
    override fun findAll(query: ListSnippetsQuery): PageResult<SnippetListItem> {
        if (query.relationship == SnippetRelationship.SHARED) {
            return PageResult.empty(
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
}
