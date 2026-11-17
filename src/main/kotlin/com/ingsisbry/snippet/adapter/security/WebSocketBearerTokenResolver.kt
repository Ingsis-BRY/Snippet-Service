package com.ingsisbry.snippet.adapter.security

import com.ingsisbry.snippet.adapter.run.RunProtocol
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver

/**
 * Browsers can't set `Authorization` on a WebSocket handshake, so the run socket sends the token as the
 * subprotocol `bearer.<jwt>` in `Sec-WebSocket-Protocol`. Unlike a query parameter, that never shows up in
 * an access or error log. Every other request uses the usual `Authorization: Bearer` header.
 */
class WebSocketBearerTokenResolver(
    private val fallback: BearerTokenResolver = DefaultBearerTokenResolver(),
) : BearerTokenResolver {
    override fun resolve(request: HttpServletRequest): String? {
        if (RunProtocol.PATH_REGEX.matches(request.requestURI)) {
            val token =
                request
                    .getHeaders(SUBPROTOCOL_HEADER)
                    .toList()
                    .flatMap { it.split(',') }
                    .map { it.trim() }
                    .firstOrNull { it.startsWith(RunProtocol.BEARER_PREFIX) }
                    ?.removePrefix(RunProtocol.BEARER_PREFIX)
            if (!token.isNullOrEmpty()) return token
        }
        return fallback.resolve(request)
    }

    private companion object {
        const val SUBPROTOCOL_HEADER = "Sec-WebSocket-Protocol"
    }
}
