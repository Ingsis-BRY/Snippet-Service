package com.ingsisbry.snippet.application.command.update

import com.ingsisbry.snippet.domain.Snippet

interface UpdateSnippet {
    fun execute(command: UpdateSnippetCommand): Snippet?
}
