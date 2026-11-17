package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.security.CurrentUserId
import com.ingsisbry.snippet.adapter.web.dto.common.pagination.PageResponse
import com.ingsisbry.snippet.adapter.web.dto.request.CreateSnippetRequest
import com.ingsisbry.snippet.adapter.web.dto.request.UpdateSnippetContentRequest
import com.ingsisbry.snippet.adapter.web.dto.request.UpdateSnippetRequest
import com.ingsisbry.snippet.adapter.web.dto.response.CreateSnippetResponse
import com.ingsisbry.snippet.adapter.web.dto.response.GetSnippetResponse
import com.ingsisbry.snippet.adapter.web.dto.response.SnippetListItemResponse
import com.ingsisbry.snippet.adapter.web.dto.response.UpdateSnippetResponse
import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.adapter.web.mapper.SnippetFileReader
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
import com.ingsisbry.snippet.application.query.get.GetSnippetDetails
import com.ingsisbry.snippet.application.query.get.GetSnippetQuery
import com.ingsisbry.snippet.application.query.list.ListSnippets
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@Profile("!worker")
@RequestMapping("/snippets")
class SnippetController(
    private val createSnippet: CreateSnippet,
    private val getSnippetDetails: GetSnippetDetails,
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
        @CurrentUserId userId: String,
        @RequestBody request: CreateSnippetRequest,
    ): ResponseEntity<*> =
        createSnippet
            .execute(
                CreateSnippetCommand(
                    name = request.name,
                    description = request.description,
                    language = request.language,
                    languageVersion = request.languageVersion,
                    owner = userId,
                    content = request.content,
                ),
            ).respond { snippet ->
                ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(CreateSnippetResponse.from(snippet))
            }

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createFromFile(
        @CurrentUserId userId: String,
        @RequestParam name: String,
        @RequestParam description: String,
        @RequestParam language: String,
        @RequestParam languageVersion: String,
        @RequestPart file: MultipartFile,
    ): ResponseEntity<*> =
        SnippetFileReader
            .read(file)
            .flatMap { content ->
                createSnippet.execute(
                    CreateSnippetCommand(
                        name = name,
                        description = description,
                        language = language,
                        languageVersion = languageVersion,
                        owner = userId,
                        content = content,
                    ),
                )
            }.respond { snippet ->
                ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(CreateSnippetResponse.from(snippet))
            }

    @GetMapping
    fun list(
        @CurrentUserId userId: String,
        @RequestParam parameters: Map<String, String>,
    ): ResponseEntity<*> =
        queryFactory
            .create(
                parameters = parameters,
                user = userId,
            ).flatMap { query -> listSnippets.execute(query) }
            .respond { page -> ResponseEntity.ok(PageResponse.from(page.map(SnippetListItemResponse::from))) }

    @GetMapping("/{snippetId}")
    fun get(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
    ): ResponseEntity<*> =
        getSnippetDetails
            .execute(GetSnippetQuery(snippetId = snippetId, userId = userId))
            .respond { details -> ResponseEntity.ok(GetSnippetResponse.from(details)) }

    @PutMapping("/{snippetId}")
    fun update(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
        @RequestBody request: UpdateSnippetRequest,
    ): ResponseEntity<*> =
        updateSnippet
            .execute(
                UpdateSnippetCommand(
                    id = snippetId,
                    userId = userId,
                    name = request.name,
                    description = request.description,
                    language = request.language,
                    languageVersion = request.languageVersion,
                    content = request.content,
                ),
            ).respond { snippet -> ResponseEntity.ok(UpdateSnippetResponse.from(snippet)) }

    @PutMapping("/{snippetId}/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateFromFile(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
        @RequestParam name: String,
        @RequestParam description: String,
        @RequestParam language: String,
        @RequestParam languageVersion: String,
        @RequestPart file: MultipartFile,
    ): ResponseEntity<*> =
        SnippetFileReader
            .read(file)
            .flatMap { content ->
                updateSnippet.execute(
                    UpdateSnippetCommand(
                        id = snippetId,
                        userId = userId,
                        name = name,
                        description = description,
                        language = language,
                        languageVersion = languageVersion,
                        content = content,
                    ),
                )
            }.respond { snippet -> ResponseEntity.ok(UpdateSnippetResponse.from(snippet)) }

    @PatchMapping("/{snippetId}")
    fun updateContent(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
        @RequestBody request: UpdateSnippetContentRequest,
    ): ResponseEntity<*> =
        updateSnippetContent
            .execute(UpdateSnippetContentCommand(id = snippetId, userId = userId, content = request.content))
            .respond { snippet -> ResponseEntity.ok(UpdateSnippetResponse.from(snippet)) }

    @PatchMapping("/{snippetId}/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateContentFromFile(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
        @RequestPart file: MultipartFile,
    ): ResponseEntity<*> =
        SnippetFileReader
            .read(file)
            .flatMap { content ->
                updateSnippetContent.execute(
                    UpdateSnippetContentCommand(id = snippetId, userId = userId, content = content),
                )
            }.respond { snippet -> ResponseEntity.ok(UpdateSnippetResponse.from(snippet)) }

    @DeleteMapping("/{snippetId}")
    fun delete(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
    ): ResponseEntity<*> =
        deleteSnippet
            .execute(id = snippetId, userId = userId)
            .respond { ResponseEntity.noContent().build<Void>() }

    @GetMapping("/{snippetId}/download")
    fun download(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
        @RequestParam(defaultValue = "original") version: String,
    ): ResponseEntity<*> =
        snippetDownloadMapper
            .toVariant(version)
            .flatMap { variant ->
                downloadSnippet
                    .execute(id = snippetId, userId = userId, variant = variant)
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
