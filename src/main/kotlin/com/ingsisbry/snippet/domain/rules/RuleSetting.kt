package com.ingsisbry.snippet.domain.rules

/** A user's choice for one rule; [value] is an Int, a String or null (boolean rules). */
data class RuleSetting(
    val key: String,
    val enabled: Boolean,
    val value: Any?,
)
