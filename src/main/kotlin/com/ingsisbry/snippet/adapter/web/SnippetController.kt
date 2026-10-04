package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.dto.CreateSnippetRequest
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.create.CreateSnippetCommand
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippetQuery
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/snippets")
class SnippetController(
    private val createSnippet: CreateSnippet,
    private val getSnippet: GetSnippet,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateSnippetRequest,
    ) {
        createSnippet.execute(
            CreateSnippetCommand(
                name = request.name,
                description = request.description,
                language = request.language,
                languageVersion = request.languageVersion,
                owner = "Anonymous",
                content = request.content,
            ),
        )
    }

    @GetMapping("/{snippetId}")
    fun get(
        @PathVariable snippetId: UUID,
    ) = getSnippet.execute(GetSnippetQuery(snippetId))
}
