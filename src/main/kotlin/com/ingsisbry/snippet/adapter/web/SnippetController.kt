package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.dto.common.pagination.PageResponse
import com.ingsisbry.snippet.adapter.web.dto.request.CreateSnippetRequest
import com.ingsisbry.snippet.adapter.web.dto.request.UpdateSnippetContentRequest
import com.ingsisbry.snippet.adapter.web.dto.request.UpdateSnippetRequest
import com.ingsisbry.snippet.adapter.web.dto.response.CreateSnippetResponse
import com.ingsisbry.snippet.adapter.web.dto.response.GetSnippetResponse
import com.ingsisbry.snippet.adapter.web.dto.response.UpdateSnippetResponse
import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.create.CreateSnippetCommand
import com.ingsisbry.snippet.application.command.delete.DeleteSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippetCommand
import com.ingsisbry.snippet.application.command.update.UpdateSnippetContent
import com.ingsisbry.snippet.application.command.update.UpdateSnippetContentCommand
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.query.download.DownloadSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippetQuery
import com.ingsisbry.snippet.application.query.list.ListSnippets
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/snippets")
class SnippetController(
    private val createSnippet: CreateSnippet,
    private val getSnippet: GetSnippet,
    private val updateSnippet: UpdateSnippet,
    private val updateSnippetContent: UpdateSnippetContent,
    private val deleteSnippet: DeleteSnippet,
    private val listSnippets: ListSnippets,
    private val queryFactory: ListSnippetsQueryFactory,
    private val downloadSnippet: DownloadSnippet,
    private val snippetDownloadMapper: SnippetDownloadMapper,
    private val errorMapper: SnippetErrorMapper,
) {
    @PostMapping
    fun create(
        @RequestBody request: CreateSnippetRequest,
    ): ResponseEntity<*> =
        createSnippet
            .execute(
                CreateSnippetCommand(
                    name = request.name,
                    description = request.description,
                    language = request.language,
                    languageVersion = request.languageVersion,
                    owner = "Anonymous",
                    content = request.content,
                ),
            ).respond { snippet ->
                ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(CreateSnippetResponse(id = snippet.id))
            }

    @GetMapping
    fun list(
        @RequestParam parameters: Map<String, String>,
    ): ResponseEntity<*> =
        queryFactory
            .create(
                parameters = parameters,
                user = "anonymous",
            ).flatMap { query -> listSnippets.execute(query) }
            .respond { page -> ResponseEntity.ok(PageResponse.from(page)) }

    @GetMapping("/{snippetId}")
    fun get(
        @PathVariable snippetId: UUID,
    ): ResponseEntity<*> =
        getSnippet
            .execute(GetSnippetQuery(snippetId))
            .respond { snippet -> ResponseEntity.ok(GetSnippetResponse.from(snippet)) }

    @PutMapping("/{snippetId}")
    fun update(
        @PathVariable snippetId: UUID,
        @RequestBody request: UpdateSnippetRequest,
    ): ResponseEntity<*> =
        updateSnippet
            .execute(
                UpdateSnippetCommand(
                    id = snippetId,
                    name = request.name,
                    description = request.description,
                    language = request.language,
                    languageVersion = request.languageVersion,
                    content = request.content,
                ),
            ).respond { snippet -> ResponseEntity.ok(UpdateSnippetResponse.from(snippet)) }

    @PatchMapping("/{snippetId}")
    fun updateContent(
        @PathVariable snippetId: UUID,
        @RequestBody request: UpdateSnippetContentRequest,
    ): ResponseEntity<*> =
        updateSnippetContent
            .execute(UpdateSnippetContentCommand(id = snippetId, content = request.content))
            .respond { snippet -> ResponseEntity.ok(UpdateSnippetResponse.from(snippet)) }

    @DeleteMapping("/{snippetId}")
    fun delete(
        @PathVariable snippetId: UUID,
    ): ResponseEntity<*> =
        deleteSnippet
            .execute(snippetId)
            .respond { ResponseEntity.noContent().build<Void>() }

    @GetMapping("/{snippetId}/download")
    fun download(
        @PathVariable snippetId: UUID,
        @RequestParam(defaultValue = "original") version: String,
    ): ResponseEntity<*> =
        snippetDownloadMapper
            .toVariant(version)
            .flatMap { variant ->
                downloadSnippet
                    .execute(id = snippetId, variant = variant)
                    .map { content -> variant to content }
            }.respond { (variant, content) ->
                snippetDownloadMapper.toResponse(
                    snippetId = snippetId,
                    variant = variant,
                    content = content,
                )
            }

    private fun <T> Result<SnippetError, T>.respond(onSuccess: (T) -> ResponseEntity<*>): ResponseEntity<*> =
        fold(
            onSuccess = onSuccess,
            onFailure = errorMapper::toResponse,
        )
}
