package com.ingsisbry.snippet.domain.test

object SnippetTestRules {
    const val MAX_NAME_LENGTH = 255
    const val MAX_ITEMS = 100
    const val MAX_ITEM_LENGTH = 1000
    const val MAX_TESTS_PER_SNIPPET = 50

    /** The first rule the definition breaks, or null when it is valid. */
    fun violation(
        name: String,
        inputs: List<String>,
        expectedOutputs: List<String>,
    ): String? =
        when {
            name.isBlank() -> "name must not be blank"
            name.length > MAX_NAME_LENGTH -> "name must be at most $MAX_NAME_LENGTH characters"
            else -> itemsViolation("input", inputs) ?: itemsViolation("output", expectedOutputs)
        }

    /** Whether one more test fits next to [existingTests]. */
    fun countViolation(existingTests: Int): String? =
        if (existingTests >= MAX_TESTS_PER_SNIPPET) "A snippet can have at most $MAX_TESTS_PER_SNIPPET tests" else null

    private fun itemsViolation(
        field: String,
        items: List<String>,
    ): String? =
        when {
            items.size > MAX_ITEMS -> "$field must have at most $MAX_ITEMS items"
            items.any { it.length > MAX_ITEM_LENGTH } -> "each $field item must be at most $MAX_ITEM_LENGTH characters"
            else -> null
        }
}
