package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.security.CurrentUserId
import com.ingsisbry.snippet.adapter.web.dto.request.FormatSnippetRequest
import com.ingsisbry.snippet.adapter.web.dto.response.FormatSnippetResponse
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.format.FormatSnippet
import com.ingsisbry.snippet.application.command.format.FormatSnippetCommand
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The editor's Format button: formats with the owner's rules and stores nothing. */
@RestController
@Profile("!worker")
@RequestMapping("/snippets/{snippetId}/format")
class SnippetFormatController(
    private val formatSnippet: FormatSnippet,
    private val errorMapper: SnippetErrorMapper,
) {
    @PostMapping
    fun format(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
        @RequestBody(required = false) request: FormatSnippetRequest?,
    ): ResponseEntity<*> =
        formatSnippet
            .execute(FormatSnippetCommand(snippetId, userId, request?.content))
            .fold(
                onSuccess = { ResponseEntity.ok(FormatSnippetResponse(it.formatted, it.ignoredRules)) },
                onFailure = errorMapper::toResponse,
            )
}
