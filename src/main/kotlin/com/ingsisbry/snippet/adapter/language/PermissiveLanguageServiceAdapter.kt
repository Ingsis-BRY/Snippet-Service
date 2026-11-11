package com.ingsisbry.snippet.adapter.language

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.language.SnippetContentValidator
import com.ingsisbry.snippet.application.port.out.language.SupportedLanguages
import org.springframework.stereotype.Component

/**
 * Placeholder until the language microservice exists: accepts every language and every content.
 * Replace it with an adapter that calls that service; the use cases won't change.
 */
@Component
class PermissiveLanguageServiceAdapter :
    SupportedLanguages,
    SnippetContentValidator {
    override fun check(
        language: String,
        languageVersion: String,
    ): Result<SnippetError, Unit> = Result.Success(Unit)

    override fun validate(
        language: String,
        languageVersion: String,
        content: String,
    ): Result<SnippetError, Unit> = Result.Success(Unit)
}
