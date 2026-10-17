package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.dto.common.pagination.PageResponse
import com.ingsisbry.snippet.adapter.web.dto.request.CreateSnippetRequest
import com.ingsisbry.snippet.adapter.web.dto.request.UpdateSnippetRequest
import com.ingsisbry.snippet.adapter.web.dto.response.CreateSnippetResponse
import com.ingsisbry.snippet.adapter.web.dto.response.GetSnippetResponse
import com.ingsisbry.snippet.adapter.web.dto.response.UpdateSnippetResponse
import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.create.CreateSnippetCommand
import com.ingsisbry.snippet.application.command.delete.DeleteSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippet
import com.ingsisbry.snippet.application.query.download.DownloadSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippetQuery
import com.ingsisbry.snippet.application.query.list.ListSnippets
import com.ingsisbry.snippet.application.query.list.SnippetListItem
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/snippets")
class SnippetController(
    private val createSnippet: CreateSnippet,
    private val getSnippet: GetSnippet,
    private val updateSnippet: UpdateSnippet,
    private val deleteSnippet: DeleteSnippet,
    private val listSnippets: ListSnippets,
    private val queryFactory: ListSnippetsQueryFactory,
    private val downloadSnippet: DownloadSnippet,
    private val snippetDownloadMapper: SnippetDownloadMapper,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateSnippetRequest,
    ): CreateSnippetResponse {
        val snippet =
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
        return CreateSnippetResponse(id = snippet.id)
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun list(
        @RequestParam parameters: Map<String, String>,
    ): PageResponse<SnippetListItem> {
        val query =
            queryFactory.create(
                parameters = parameters,
                user = "anonymous",
            )

        return PageResponse.from(listSnippets.execute(query))
    }

    @GetMapping("/{snippetId}")
    @ResponseStatus(HttpStatus.OK)
    fun get(
        @PathVariable snippetId: UUID,
    ): GetSnippetResponse {
        val snippet = getSnippet.execute(GetSnippetQuery(snippetId))
        return GetSnippetResponse.from(snippet)
    }

    @PutMapping("/{snippetId}")
    fun update(
        @PathVariable snippetId: UUID,
        @RequestBody request: UpdateSnippetRequest,
    ): UpdateSnippetResponse {
        val updatedSnippet =
            updateSnippet.execute(
                id = snippetId,
                request = request,
            ) ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Snippet not found",
            )

        return UpdateSnippetResponse.from(updatedSnippet)
    }

    @DeleteMapping("/{snippetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable snippetId: UUID,
    ) {
        val deleted = deleteSnippet.execute(snippetId)

        if (!deleted) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Snippet not found",
            )
        }
    }

    @GetMapping("/{snippetId}/download")
    fun download(
        @PathVariable snippetId: UUID,
        @RequestParam(defaultValue = "original") version: String,
    ): ResponseEntity<ByteArray> {
        val variant = snippetDownloadMapper.toVariant(version)

        val content =
            downloadSnippet.execute(
                id = snippetId,
                variant = variant,
            ) ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Snippet not found",
            )

        return snippetDownloadMapper.toResponse(
            snippetId = snippetId,
            variant = variant,
            content = content,
        )
    }
}
