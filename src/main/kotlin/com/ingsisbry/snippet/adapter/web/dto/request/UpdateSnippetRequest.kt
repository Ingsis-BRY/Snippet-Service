package com.ingsisbry.snippet.adapter.web.dto.request

data class UpdateSnippetRequest(
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val content: String,
)
