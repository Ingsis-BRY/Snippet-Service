package com.ingsisbry.snippet.adapter.auth0

internal object Auth0UserQuery {
    private const val MINIMUM_CONTAINS_LENGTH = 3
    private val SPECIAL_CHARACTERS =
        setOf(
            '+',
            '-',
            '&',
            '|',
            '!',
            '(',
            ')',
            '{',
            '}',
            '[',
            ']',
            '^',
            '"',
            '~',
            '*',
            '?',
            ':',
            '\\',
            '/',
            ' ',
            '@',
            '.',
        )

    fun build(
        text: String,
        excludedUserId: String,
    ): String {
        val exclusion = "NOT user_id:\"${quoted(excludedUserId)}\""
        if (text.isBlank()) return exclusion

        val term = escaped(text)
        val pattern = if (text.length >= MINIMUM_CONTAINS_LENGTH) "*$term*" else "$term*"
        return "(name:$pattern OR email:$pattern) AND $exclusion"
    }

    /** `user_id:("id1" OR "id2" ...)`, each id quoted. */
    fun byIds(ids: Collection<String>): String = ids.joinToString(" OR ", "user_id:(", ")") { "\"${quoted(it)}\"" }

    private fun escaped(text: String): String =
        buildString {
            text.forEach { character ->
                if (character in SPECIAL_CHARACTERS) append('\\')
                append(character)
            }
        }

    private fun quoted(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
