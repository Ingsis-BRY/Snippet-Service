package com.ingsisbry.snippet.adapter.language

import org.springframework.boot.context.properties.ConfigurationProperties

/** `snippet.languages`: the languages offered to users, each answered by an engine behind the proxy. */
@ConfigurationProperties("snippet")
data class SnippetLanguagesProperties(
    val languages: List<LanguageProperties> = emptyList(),
) {
    data class LanguageProperties(
        val name: String,
        val extensions: List<String> = emptyList(),
    )
}
