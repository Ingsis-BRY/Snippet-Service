package com.ingsisbry.snippet.adapter.web.dto

data class CreateSnippetRequest(
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val content: String,
)
