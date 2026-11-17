package com.ingsisbry.snippet.adapter.persistence.query.filter.parameters

import com.ingsisbry.snippet.adapter.persistence.query.filter.SnippetFilter
import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import com.ingsisbry.snippet.adapter.persistence.snippetversion.SnippetVersionEntity
import com.ingsisbry.snippet.application.analysis.LintStatus
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.stereotype.Component

/** `valid=true`: the current version complies with its owner's lint rules; `valid=false`: anything else. */
@Component
class ValidityFilter : SnippetFilter {
    override val parameterName = "valid"

    override fun validate(value: String): String? =
        if (value.toBooleanStrictOrNull() == null) "The valid filter must be either true or false" else null

    override fun toPredicate(
        value: String,
        root: Root<SnippetEntity>,
        criteriaBuilder: CriteriaBuilder,
    ): Predicate? {
        val lintStatus = root.get<SnippetVersionEntity>("currentVersion").get<String>("lintStatus")
        val compliant = LintStatus.COMPLIANT.name
        return if (value.toBooleanStrict()) {
            criteriaBuilder.equal(lintStatus, compliant)
        } else {
            criteriaBuilder.notEqual(lintStatus, compliant)
        }
    }
}
