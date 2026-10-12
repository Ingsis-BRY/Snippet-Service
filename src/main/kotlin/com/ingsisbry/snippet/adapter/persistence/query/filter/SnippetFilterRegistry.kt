package com.ingsisbry.snippet.adapter.persistence.query.filter

import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import org.hibernate.UnknownFilterException
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component

@Component
class SnippetFilterRegistry(
    filters: List<SnippetFilter>,
) {
    private val filtersByName: Map<String, SnippetFilter> =
        filters.associateBy { it.parameterName }.also { registered ->

            require(registered.size == filters.size) {
                "Multiple snippet filters use the same parameter name"
            }
        }

    fun toSpecification(parameters: Map<String, String>): Specification<SnippetEntity> =
        Specification { root, _, criteriaBuilder ->
            val predicates =
                parameters.mapNotNull { (name, value) ->
                    val filter =
                        filtersByName[name]
                            ?: throw UnknownFilterException(name)

                    filter.toPredicate(
                        value,
                        root,
                        criteriaBuilder,
                    )
                }

            combine(predicates, criteriaBuilder)
        }

    private fun combine(
        predicates: List<Predicate>,
        criteriaBuilder: CriteriaBuilder,
    ): Predicate = criteriaBuilder.and(*predicates.toTypedArray())
}
