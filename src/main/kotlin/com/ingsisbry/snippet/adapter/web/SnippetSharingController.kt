package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.dto.request.ShareSnippetRequest
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.share.ShareSnippet
import com.ingsisbry.snippet.application.command.share.ShareSnippetCommand
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/snippets/{snippetId}/share")
class SnippetSharingController(
    private val shareSnippet: ShareSnippet,
    private val errorMapper: SnippetErrorMapper,
) {
    @PostMapping
    fun share(
        @RequestHeader(UserHeader.NAME) userId: String,
        @PathVariable snippetId: UUID,
        @RequestBody request: ShareSnippetRequest,
    ): ResponseEntity<*> =
        shareSnippet
            .execute(
                ShareSnippetCommand(
                    snippetId = snippetId,
                    ownerId = userId,
                    targetUserId = request.targetUserId,
                ),
            ).fold(
                onSuccess = { ResponseEntity.noContent().build<Void>() },
                onFailure = errorMapper::toResponse,
            )
}
