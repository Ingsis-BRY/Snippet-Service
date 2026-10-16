package com.ingsisbry.snippet.application.command.delete

import java.util.UUID

interface DeleteSnippet {
    fun execute(id: UUID): Boolean
}
