package com.ingsisbry.snippet.application.port.out.language

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

/** A language snippets can be written in; [libraryVersion] is null when the engine does not report it. */
data class LanguageInfo(
    val language: String,
    val extensions: List<String>,
    val versions: List<String>,
    val defaultVersion: String,
    val libraryVersion: String?,
)

fun interface LanguageCatalog {
    fun languages(): Result<SnippetError, List<LanguageInfo>>
}
