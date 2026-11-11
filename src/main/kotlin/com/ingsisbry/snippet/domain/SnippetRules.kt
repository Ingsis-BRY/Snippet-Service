package com.ingsisbry.snippet.domain

object SnippetRules {
    const val MAX_TEXT_LENGTH = 255
    const val MAX_CONTENT_LENGTH = 10000

    @Suppress("LongParameterList")
    fun violation(
        name: String,
        description: String,
        language: String,
        languageVersion: String,
        content: String,
    ): String? =
        requiredText("name", name)
            ?: requiredText("language", language)
            ?: requiredText("languageVersion", languageVersion)
            ?: textTooLong("description", description)
            ?: contentTooLong(content)

    private fun requiredText(
        field: String,
        value: String,
    ): String? = if (value.isBlank()) "$field must not be blank" else textTooLong(field, value)

    private fun textTooLong(
        field: String,
        value: String,
    ): String? = if (value.length > MAX_TEXT_LENGTH) "$field must be at most $MAX_TEXT_LENGTH characters" else null

    private fun contentTooLong(content: String): String? =
        if (content.length > MAX_CONTENT_LENGTH) "content must be at most $MAX_CONTENT_LENGTH characters" else null
}
