package com.ingsisbry.snippet.application.command.create

import com.ingsisbry.snippet.domain.Snippet

fun interface CreateSnippet {
    fun execute(command: CreateSnippetCommand): Snippet
}
