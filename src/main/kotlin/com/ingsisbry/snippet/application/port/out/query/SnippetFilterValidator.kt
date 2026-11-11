package com.ingsisbry.snippet.application.port.out.query

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

fun interface SnippetFilterValidator {
    fun validate(filters: Map<String, String>): Result<SnippetError, Unit>
}
