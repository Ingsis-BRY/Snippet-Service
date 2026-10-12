package com.ingsisbry.snippet.adapter.persistence.filter.parameters

import com.ingsisbry.snippet.adapter.persistence.filter.SnippetFilter
import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.stereotype.Component

@Component
class LanguageFilter(
    private val textPredicateFactory: TextPredicateFactory,
) : SnippetFilter {
    override val parameterName = "language"

    override fun toPredicate(
        value: String,
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
    ): Predicate? =
        textPredicateFactory.create(
            root = root,
            criteriaBuilder = criteriaBuilder,
            property = "language",
            value = value,
            exactMatch = true,
        )
}
