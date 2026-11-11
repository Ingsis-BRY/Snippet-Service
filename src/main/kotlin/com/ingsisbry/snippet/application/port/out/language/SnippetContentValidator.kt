package com.ingsisbry.snippet.application.port.out.language

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

fun interface SnippetContentValidator {
    fun validate(
        language: String,
        languageVersion: String,
        content: String,
    ): Result<SnippetError, Unit>
}
