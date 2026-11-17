package com.ingsisbry.snippet.adapter.web.dto.request

/** [content] null or absent = the snippet's current content. */
data class FormatSnippetRequest(
    val content: String? = null,
)
