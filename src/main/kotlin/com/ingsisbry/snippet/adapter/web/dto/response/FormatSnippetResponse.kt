package com.ingsisbry.snippet.adapter.web.dto.response

/** [ignoredRules] are enabled rules that only exist in later versions of the snippet's language. */
data class FormatSnippetResponse(
    val formatted: String,
    val ignoredRules: List<String>,
)
