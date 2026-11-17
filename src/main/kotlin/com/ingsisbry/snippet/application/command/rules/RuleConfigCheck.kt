package com.ingsisbry.snippet.application.command.rules

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.language.LanguageCatalog
import com.ingsisbry.snippet.application.port.out.language.SnippetFormatter
import com.ingsisbry.snippet.application.port.out.language.SnippetLinter
import com.ingsisbry.snippet.domain.rules.RULES_LANGUAGE
import com.ingsisbry.snippet.domain.rules.RuleKind
import org.springframework.stereotype.Service

/**
 * Asks the engine whether a config is acceptable by using it on empty code with the newest language version, so
 * a config the engine refuses (e.g. contradictory rules, an engine `InvalidConfig`) is rejected before it is stored.
 */
@Service
class RuleConfigCheck(
    private val catalog: LanguageCatalog,
    private val linter: SnippetLinter,
    private val formatter: SnippetFormatter,
) {
    fun check(
        kind: RuleKind,
        config: Map<String, Any>,
    ): Result<SnippetError, Unit> =
        newestVersion().flatMap { version ->
            when (kind) {
                RuleKind.FORMAT -> formatter.format(RULES_LANGUAGE, version, "", config).map { }
                RuleKind.LINT -> linter.lint(RULES_LANGUAGE, version, "", config).map { }
            }
        }

    private fun newestVersion(): Result<SnippetError, String> =
        catalog.languages().flatMap { languages ->
            val version = languages.firstOrNull { it.language == RULES_LANGUAGE }?.versions?.lastOrNull()
            if (version == null) {
                Result.Failure(LanguageServiceUnavailable("no engine for $RULES_LANGUAGE"))
            } else {
                Result.Success(version)
            }
        }
}
