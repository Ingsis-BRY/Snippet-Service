package com.ingsisbry.snippet.adapter.persistence.query.specification

import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification

object SnippetAccessSpecifications {
    fun belongsToUser(user: String): Specification<SnippetEntity> =
        Specification { root, _, criteriaBuilder ->
            matchesUser(
                root = root,
                criteriaBuilder = criteriaBuilder,
                user = user,
            )
        }

    private fun matchesUser(
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
        user: String,
    ): Predicate =
        criteriaBuilder.equal(
            criteriaBuilder.lower(root.get("owner")),
            user.lowercase(),
        )
}
