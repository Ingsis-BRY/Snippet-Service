package com.ingsisbry.snippet.adapter.persistence.query.filter

import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

interface SnippetFilter {
    val parameterName: String

    fun validate(value: String): String? = null

    fun toPredicate(
        value: String,
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
    ): Predicate?
}
