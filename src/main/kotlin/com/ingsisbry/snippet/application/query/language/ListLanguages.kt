package com.ingsisbry.snippet.application.query.language

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.language.LanguageInfo

/** The languages and versions a snippet can be written in (US#1, #3). */
fun interface ListLanguages {
    fun execute(): Result<SnippetError, List<LanguageInfo>>
}
