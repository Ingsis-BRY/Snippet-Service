package com.ingsisbry.snippet.adapter.persistence.specification

import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import com.ingsisbry.snippet.application.query.list.ListSnippetsQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification

object SnippetSpecifications {
    fun from(query: ListSnippetsQuery): Specification<SnippetEntity> =
        Specification { root, _, criteriaBuilder ->
            val filters =
                mutableListOf(
                    belongsToUser(root, criteriaBuilder, query.user),
                )

            addNameFilter(filters, root, criteriaBuilder, query.name)
            addLanguageFilter(filters, root, criteriaBuilder, query.language)

            criteriaBuilder.and(*filters.toTypedArray())
        }

    private fun belongsToUser(
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
        user: String,
    ): Predicate =
        criteriaBuilder.equal(
            criteriaBuilder.lower(root.get("owner")),
            user.lowercase(),
        )

    private fun addNameFilter(
        filters: MutableList<Predicate>,
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
        name: String?,
    ) {
        if (name.isNullOrBlank()) return

        filters.add(
            matchesName(root, criteriaBuilder, name.trim()),
        )
    }

    private fun matchesName(
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
        name: String,
    ): Predicate =
        criteriaBuilder.like(
            criteriaBuilder.lower(root.get("name")),
            "%${escapeSearchText(name.lowercase())}%",
            '\\',
        )

    private fun addLanguageFilter(
        filters: MutableList<Predicate>,
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
        language: String?,
    ) {
        if (language.isNullOrBlank()) return

        filters.add(
            matchesLanguage(root, criteriaBuilder, language.trim()),
        )
    }

    private fun matchesLanguage(
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
        language: String,
    ): Predicate =
        criteriaBuilder.equal(
            criteriaBuilder.lower(root.get("language")),
            language.lowercase(),
        )

    private fun escapeSearchText(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
