package com.ingsisbry.snippet.application.port.out.language

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.domain.rules.RuleKind

enum class RuleType { BOOLEAN, INTEGER, ENUM }

/** A rule of the language's catalog, the same for every user. */
data class RuleDefinition(
    val key: String,
    val type: RuleType,
    val default: Any?,
    val since: String,
    val description: String,
    val options: List<String>?,
    val min: Int?,
    val max: Int?,
)

fun interface RuleDefinitions {
    fun rules(kind: RuleKind): Result<SnippetError, List<RuleDefinition>>
}
