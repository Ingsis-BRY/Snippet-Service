package com.ingsisbry.snippet.adapter.auth0

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ManagementApiTokensTest {
    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private var now = Instant.parse("2026-11-16T12:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId): Clock = this

            override fun instant(): Instant = now
        }
    private val tokens = ManagementApiTokens(builder.build(), "id", "secret", "aud", clock)

    @AfterEach
    fun verify() {
        server.verify()
    }

    private fun respond(token: String): ResponseCreator {
        val body = """{"access_token":"$token","expires_in":3600}"""
        return withSuccess(body, MediaType.APPLICATION_JSON)
    }

    @Test
    fun `a token is reused until a minute before it expires`() {
        server.expect(ExpectedCount.once(), requestTo("$BASE_URL/oauth/token")).andRespond(respond("first"))
        server.expect(ExpectedCount.once(), requestTo("$BASE_URL/oauth/token")).andRespond(respond("second"))

        assertEquals("first", tokens.current())
        now = now.plusSeconds(3600 - 61)
        assertEquals("first", tokens.current())
        now = now.plusSeconds(2)
        assertEquals("second", tokens.current())
    }

    private companion object {
        const val BASE_URL = "https://tenant.auth0.test"
    }
}
