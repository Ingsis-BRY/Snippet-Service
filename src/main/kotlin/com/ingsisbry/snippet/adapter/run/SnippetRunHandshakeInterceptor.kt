package com.ingsisbry.snippet.adapter.run

import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippetQuery
import com.ingsisbry.snippet.application.query.tests.ListSnippetTests
import org.springframework.context.annotation.Profile
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
@Profile("!worker")
class SnippetRunHandshakeInterceptor(
    private val getSnippet: GetSnippet,
    private val listSnippetTests: ListSnippetTests,
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
            // the tests are loaded with the snippet so a test run (US#9) can only use one of this snippet's tests
            getSnippet
                .execute(GetSnippetQuery(snippetId, userId))
                .flatMap { snippet -> listSnippetTests.execute(snippetId, userId).map { tests -> snippet to tests } }
                .fold(
                    onSuccess = { (snippet, tests) ->
                        attributes[RunAttributes.SNIPPET] = snippet
                        attributes[RunAttributes.TESTS] = tests
                    },
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
    const val TESTS = "tests"
    const val ERROR = "error"
}
