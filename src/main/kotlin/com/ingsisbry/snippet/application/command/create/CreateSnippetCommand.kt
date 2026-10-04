package com.ingsisbry.snippet.application.command.create

data class CreateSnippetCommand(
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val owner: String,
    val content: String,
)