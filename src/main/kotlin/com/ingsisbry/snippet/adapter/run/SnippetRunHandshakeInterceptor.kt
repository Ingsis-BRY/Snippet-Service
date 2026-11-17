package com.ingsisbry.snippet.adapter.run

import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippetQuery
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.util.UUID

/**
 * Authorizes and loads the snippet while the handshake is still an HTTP request: the `SecurityContext` is set
 * on this thread, so `GetSnippet` checks READ with the user's token as any other request does.
 *
 * Errors don't refuse the handshake: they are stored and sent as an `error` frame once the socket is open,
 * because a browser can't read the status of a refused handshake.
 */
@Component
class SnippetRunHandshakeInterceptor(
    private val getSnippet: GetSnippet,
) : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val authentication = request.principal as? JwtAuthenticationToken
        if (authentication == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
        val userId = authentication.token.subject.orEmpty()
        attributes[RunAttributes.USER_ID] = userId

        val snippetId = snippetIdOf(request.uri.path)
        if (snippetId == null) {
            attributes[RunAttributes.ERROR] = InvalidInput("Invalid snippet id")
        } else {
            attributes[RunAttributes.SNIPPET_ID] = snippetId
            getSnippet
                .execute(GetSnippetQuery(snippetId, userId))
                .fold(
                    onSuccess = { attributes[RunAttributes.SNIPPET] = it },
                    onFailure = { attributes[RunAttributes.ERROR] = it },
                )
        }
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit

    private fun snippetIdOf(path: String): UUID? {
        val raw =
            RunProtocol.PATH_REGEX
                .find(path)
                ?.groupValues
                ?.get(1) ?: return null
        return runCatching { UUID.fromString(raw) }
            .getOrNull()
            ?.takeIf { it.toString().equals(raw, ignoreCase = true) }
    }
}

/** Keys of the WebSocket session attributes the interceptor fills in. */
internal object RunAttributes {
    const val USER_ID = "userId"
    const val SNIPPET_ID = "snippetId"
    const val SNIPPET = "snippet"
    const val ERROR = "error"
}
