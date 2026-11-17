package com.ingsisbry.snippet.adapter.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest

class WebSocketBearerTokenResolverTest {
    private val resolver = WebSocketBearerTokenResolver()

    @Test
    fun `the run socket takes the token from the bearer subprotocol`() {
        val request = request("/snippets/$SNIPPET_ID/run")
        request.addHeader(SUBPROTOCOL_HEADER, "snippet-run.v1, bearer.$TOKEN")

        assertEquals(TOKEN, resolver.resolve(request))
    }

    @Test
    fun `the subprotocols may come in several headers and in any order`() {
        val request = request("/snippets/$SNIPPET_ID/run")
        request.addHeader(SUBPROTOCOL_HEADER, "bearer.$TOKEN")
        request.addHeader(SUBPROTOCOL_HEADER, "snippet-run.v1")

        assertEquals(TOKEN, resolver.resolve(request))
    }

    @Test
    fun `the run socket falls back to the Authorization header`() {
        val request = request("/snippets/$SNIPPET_ID/run")
        request.addHeader(SUBPROTOCOL_HEADER, "snippet-run.v1")
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN")

        assertEquals(TOKEN, resolver.resolve(request))
    }

    @Test
    fun `other paths ignore the subprotocol header`() {
        val request = request("/snippets/$SNIPPET_ID")
        request.addHeader(SUBPROTOCOL_HEADER, "bearer.$TOKEN")

        assertNull(resolver.resolve(request))
    }

    @Test
    fun `other paths use the Authorization header`() {
        val request = request("/snippets")
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN")

        assertEquals(TOKEN, resolver.resolve(request))
    }

    @Test
    fun `no token at all resolves to nothing`() {
        assertNull(resolver.resolve(request("/snippets/$SNIPPET_ID/run")))
    }

    private fun request(uri: String) = MockHttpServletRequest("GET", uri)

    private companion object {
        const val SUBPROTOCOL_HEADER = "Sec-WebSocket-Protocol"
        const val SNIPPET_ID = "11111111-1111-1111-1111-111111111111"
        const val TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhdXRoMHxmcmFuY28ifQ.c2lnbmF0dXJl"
    }
}
