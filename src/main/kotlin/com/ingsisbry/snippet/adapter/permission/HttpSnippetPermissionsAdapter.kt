package com.ingsisbry.snippet.adapter.permission

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.AlreadyShared
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.PermissionServiceUnavailable
import com.ingsisbry.snippet.application.error.SnippetConflict
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.port.out.permission.SnippetPermissions
import com.ingsisbry.snippet.application.query.list.SnippetRelationship
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

/** Talks to snippet-permission-service over HTTP inside the stack network. */
@Component
class HttpSnippetPermissionsAdapter(
    @param:Qualifier("permissionServiceClient")
    private val permissionServiceClient: RestClient,
) : SnippetPermissions {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun registerOwner(
        snippetId: UUID,
        userId: String,
    ): Result<SnippetError, Unit> =
        call(snippetId, userId, onConflict = SnippetConflict(snippetId)) {
            permissionServiceClient
                .post()
                .uri("/permissions/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .body(GrantOwnerRequest(snippetId))
                .retrieve()
                .toBodilessEntity()
        }.map { }

    override fun isAllowed(
        snippetId: UUID,
        userId: String,
        action: SnippetAction,
    ): Result<SnippetError, Boolean> =
        call(snippetId, userId) {
            permissionServiceClient
                .get()
                .uri { uri ->
                    uri
                        .path("/permissions/check")
                        .queryParam("snippetId", snippetId)
                        .queryParam("action", action.name)
                        .build()
                }.retrieve()
                .body(CheckResponse::class.java)
        }.map { response -> response?.allowed ?: false }

    override fun accessibleSnippetIds(
        userId: String,
        relationship: SnippetRelationship,
    ): Result<SnippetError, Set<UUID>> =
        call(snippetId = null, userId = userId) {
            permissionServiceClient
                .get()
                .uri { uri ->
                    uri
                        .path("/permissions/snippets")
                        .queryParam("relationship", relationship.toPermissionRelationship())
                        .build()
                }.retrieve()
                .body(SnippetIdsResponse::class.java)
        }.map { response -> response?.snippetIds ?: emptySet() }

    override fun share(
        snippetId: UUID,
        ownerId: String,
        targetUserId: String,
    ): Result<SnippetError, Unit> =
        call(snippetId, ownerId, onConflict = AlreadyShared(snippetId, targetUserId)) {
            permissionServiceClient
                .post()
                .uri("/permissions/share")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ShareRequest(snippetId, targetUserId))
                .retrieve()
                .toBodilessEntity()
        }.map { }

    override fun removeAll(
        snippetId: UUID,
        requesterId: String,
    ): Result<SnippetError, Unit> =
        call(snippetId, requesterId) {
            permissionServiceClient
                .delete()
                .uri("/permissions/snippets/{snippetId}", snippetId)
                .retrieve()
                .toBodilessEntity()
        }.map { }

    /** Runs [request] and translates every HTTP or network failure into a [SnippetError]. */
    private fun <T> call(
        snippetId: UUID?,
        userId: String,
        onConflict: SnippetError? = null,
        request: () -> T,
    ): Result<SnippetError, T> =
        try {
            Result.Success(request())
        } catch (exception: RestClientResponseException) {
            Result.Failure(toError(exception, snippetId, userId, onConflict))
        } catch (exception: RestClientException) {
            logger.error("Permission service is unreachable", exception)
            Result.Failure(PermissionServiceUnavailable(exception.message))
        }

    private fun toError(
        exception: RestClientResponseException,
        snippetId: UUID?,
        userId: String,
        onConflict: SnippetError?,
    ): SnippetError {
        val message = errorMessage(exception)
        return when {
            exception.statusCode.isSameCodeAs(HttpStatus.BAD_REQUEST) -> InvalidInput(message)
            exception.statusCode.isSameCodeAs(HttpStatus.FORBIDDEN) && snippetId != null ->
                AccessDenied(snippetId, userId)
            exception.statusCode.isSameCodeAs(HttpStatus.CONFLICT) && onConflict != null -> onConflict
            else -> {
                logger.error("Permission service answered {}: {}", exception.statusCode, message)
                PermissionServiceUnavailable("${exception.statusCode.value()} $message")
            }
        }
    }

    private fun errorMessage(exception: RestClientResponseException): String =
        runCatching { exception.getResponseBodyAs(ErrorResponse::class.java)?.message }
            .getOrNull()
            ?: exception.statusText

    private fun SnippetRelationship.toPermissionRelationship(): String =
        when (this) {
            SnippetRelationship.OWNED -> "OWNER"
            SnippetRelationship.SHARED -> "SHARED"
            SnippetRelationship.ALL -> "ALL"
        }
}
