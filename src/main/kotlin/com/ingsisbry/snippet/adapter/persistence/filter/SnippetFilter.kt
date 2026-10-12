package com.ingsisbry.snippet.adapter.persistence.filter

import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

interface SnippetFilter {
    val parameterName: String

    fun toPredicate(
        value: String,
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
    ): Predicate?
}
