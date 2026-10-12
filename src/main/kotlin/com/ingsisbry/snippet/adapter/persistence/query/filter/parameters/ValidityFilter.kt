package com.ingsisbry.snippet.adapter.persistence.query.filter.parameters

import com.ingsisbry.snippet.adapter.persistence.query.filter.SnippetFilter
import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.stereotype.Component

@Component
class ValidityFilter : SnippetFilter {
    override val parameterName = "valid"

    override fun toPredicate(
        value: String,
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
    ): Predicate? {
        val valid =
            value.toBooleanStrictOrNull()
                ?: throw IllegalArgumentException(
                    "The valid filter must be either true or false",
                )

        return if (valid) {
            null
        } else {
            // represents a never met condition.
            // when the user asks for valid=false, the query returns zero results
            criteriaBuilder.disjunction()
        }
    }
}
