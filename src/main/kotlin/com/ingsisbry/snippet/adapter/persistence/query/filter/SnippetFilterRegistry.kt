package com.ingsisbry.snippet.adapter.persistence.query.filter

import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.query.SnippetFilterValidator
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component

@Component
class SnippetFilterRegistry(
    filters: List<SnippetFilter>,
) : SnippetFilterValidator {
    private val filtersByName: Map<String, SnippetFilter> =
        filters.associateBy { it.parameterName }.also { registered ->

            require(registered.size == filters.size) {
                "Multiple snippet filters use the same parameter name"
            }
        }

    override fun validate(filters: Map<String, String>): Result<SnippetError, Unit> {
        val reason =
            filters.firstNotNullOfOrNull { (name, value) ->
                val filter = filtersByName[name]
                if (filter == null) "Unknown filter '$name'" else filter.validate(value)
            }

        return if (reason == null) Result.Success(Unit) else Result.Failure(InvalidInput(reason))
    }

    fun toSpecification(parameters: Map<String, String>): Specification<SnippetEntity> =
        Specification { root, _, criteriaBuilder ->
            val predicates =
                parameters.mapNotNull { (name, value) ->
                    val filter = checkNotNull(filtersByName[name]) { "Unvalidated filter '$name'" }

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
