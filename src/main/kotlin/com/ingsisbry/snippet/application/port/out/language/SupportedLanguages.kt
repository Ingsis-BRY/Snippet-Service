package com.ingsisbry.snippet.application.port.out.language

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

fun interface SupportedLanguages {
    fun check(
        language: String,
        languageVersion: String,
    ): Result<SnippetError, Unit>
}
