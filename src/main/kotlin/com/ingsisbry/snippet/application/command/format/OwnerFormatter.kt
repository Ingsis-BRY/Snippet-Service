package com.ingsisbry.snippet.application.command.format

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.language.SnippetFormatter
import com.ingsisbry.snippet.application.query.rules.ResolveRuleConfig
import com.ingsisbry.snippet.domain.rules.RuleKind
import org.springframework.stereotype.Service

data class FormattedSnippet(
    val formatted: String,
    val ignoredRules: List<String>,
)

/** Formats code with its snippet owner's format rules: a snippet looks the same whoever formats it. */
@Service
class OwnerFormatter(
    private val formatter: SnippetFormatter,
    private val ruleConfig: ResolveRuleConfig,
) {
    /** Code the formatter can't parse is [InvalidSnippetContent], with the engine's rule and position. */
    fun format(
        owner: String,
        language: String,
        languageVersion: String,
        code: String,
    ): Result<SnippetError, FormattedSnippet> =
        formatter
            .format(language, languageVersion, code, ruleConfig.execute(owner, RuleKind.FORMAT))
            .flatMap { outcome ->
                val error = outcome.error
                val formatted = outcome.formatted
                when {
                    error != null -> Result.Failure(InvalidSnippetContent(error.rule, error.message, error.span))
                    formatted != null -> Result.Success(FormattedSnippet(formatted, outcome.ignoredRules))
                    else -> Result.Success(FormattedSnippet(code, outcome.ignoredRules))
                }
            }
}
