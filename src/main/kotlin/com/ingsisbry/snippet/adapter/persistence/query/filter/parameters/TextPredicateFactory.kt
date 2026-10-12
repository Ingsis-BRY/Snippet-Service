package com.ingsisbry.snippet.adapter.persistence.query.filter.parameters

import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.stereotype.Component

@Component
class TextPredicateFactory {
    fun create(
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
        property: String,
        value: String,
        exactMatch: Boolean,
    ): Predicate? {
        val searchValue = value.trim()

        if (searchValue.isEmpty()) {
            return null
        }

        val expression = criteriaBuilder.lower(root.get(property))

        val normalizedValue = searchValue.lowercase()

        return if (exactMatch) {
            criteriaBuilder.equal(
                expression,
                normalizedValue,
            )
        } else {
            criteriaBuilder.like(
                expression,
                "%${escapeSearchText(normalizedValue)}%",
                '\\',
            )
        }
    }

    private fun escapeSearchText(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
