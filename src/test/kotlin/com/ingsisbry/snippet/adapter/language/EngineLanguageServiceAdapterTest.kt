package com.ingsisbry.snippet.adapter.language

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.UnsupportedLanguage
import com.ingsisbry.snippet.support.SAMPLE_INVALID_CONTENT
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.io.IOException

class EngineLanguageServiceAdapterTest {
    private val builder = engineClientBuilder(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = EngineLanguageServiceAdapter(builder.build())

    @AfterEach
    fun verify() {
        SecurityContextHolder.clearContext()
        server.verify()
    }

    @Test
    fun `valid code is accepted`() {
        server
            .expect(requestTo("$BASE_URL/validate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(LANGUAGE_HEADER, "printscript"))
            .andExpect(content().json("""{"code":"$CODE","version":"1.1"}""", JsonCompareMode.STRICT))
            .andRespond(withSuccess("""{"valid":true,"error":null}""", MediaType.APPLICATION_JSON))

        assertEquals(Result.Success(Unit), adapter.validate("printscript", "1.1", CODE))
    }

    @Test
    fun `invalid code is reported with the rule, the message and the span`() {
        server
            .expect(requestTo("$BASE_URL/validate"))
            .andRespond(withSuccess(INVALID_WITH_SPAN, MediaType.APPLICATION_JSON))

        assertEquals(Result.Failure(SAMPLE_INVALID_CONTENT), adapter.validate("printscript", "1.1", CODE))
    }

    @Test
    fun `invalid code without a position is reported without a span`() {
        server
            .expect(requestTo("$BASE_URL/validate"))
            .andRespond(
                withSuccess(
                    """{"valid":false,"error":{"rule":"NestingTooDeep","message":"Too deep","span":null}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(
            Result.Failure(InvalidSnippetContent("NestingTooDeep", "Too deep", null)),
            adapter.validate("printscript", "1.1", CODE),
        )
    }

    @Test
    fun `a version the engine lists is supported`() {
        server
            .expect(requestTo("$BASE_URL/versions"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(LANGUAGE_HEADER, "printscript"))
            .andRespond(withSuccess(VERSIONS, MediaType.APPLICATION_JSON))

        assertEquals(Result.Success(Unit), adapter.check("printscript", "1.1"))
    }

    @Test
    fun `a version the engine does not list is unsupported`() {
        server
            .expect(requestTo("$BASE_URL/versions"))
            .andRespond(withSuccess(VERSIONS, MediaType.APPLICATION_JSON))

        assertEquals(
            Result.Failure(UnsupportedLanguage("printscript", "9.9")),
            adapter.check("printscript", "9.9"),
        )
    }

    @Test
    fun `a language the proxy has no engine for is unsupported`() {
        server
            .expect(requestTo("$BASE_URL/versions"))
            .andExpect(header(LANGUAGE_HEADER, "python"))
            .andRespond(problem("UnknownLanguage", "Set the X-Language header to a language that has an engine."))

        assertEquals(Result.Failure(UnsupportedLanguage("python", "3")), adapter.check("python", "3"))
    }

    @Test
    fun `an unsupported version on validate is an unsupported language`() {
        server
            .expect(requestTo("$BASE_URL/validate"))
            .andRespond(problem("UnsupportedVersion", "Version 9.9 is not supported."))

        assertEquals(
            Result.Failure(UnsupportedLanguage("printscript", "9.9")),
            adapter.validate("printscript", "9.9", CODE),
        )
    }

    @Test
    fun `any other rejected request is invalid input with the engine's detail`() {
        server
            .expect(requestTo("$BASE_URL/validate"))
            .andRespond(problem("CodeTooLarge", "The code is larger than 65536 bytes."))

        assertEquals(
            Result.Failure(InvalidInput("The code is larger than 65536 bytes.")),
            adapter.validate("printscript", "1.1", CODE),
        )
    }

    @Test
    fun `a 503 is retried once and the retry's answer is used`() {
        server
            .expect(requestTo("$BASE_URL/validate"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        server
            .expect(requestTo("$BASE_URL/validate"))
            .andRespond(withSuccess(INVALID_WITH_SPAN, MediaType.APPLICATION_JSON))

        assertEquals(Result.Failure(SAMPLE_INVALID_CONTENT), adapter.validate("printscript", "1.1", CODE))
    }

    @Test
    fun `a 502 on versions is retried once too`() {
        server
            .expect(requestTo("$BASE_URL/versions"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY))
        server
            .expect(requestTo("$BASE_URL/versions"))
            .andRespond(withSuccess(VERSIONS, MediaType.APPLICATION_JSON))

        assertEquals(Result.Success(Unit), adapter.check("printscript", "1.0"))
    }

    @Test
    fun `two 504s in a row make the language service unavailable`() {
        server
            .expect(ExpectedCount.times(2), requestTo("$BASE_URL/validate"))
            .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT))

        val result = adapter.validate("printscript", "1.1", CODE)

        assertTrue((result as Result.Failure).error is LanguageServiceUnavailable, "got $result")
    }

    @Test
    fun `a 500 is not retried and makes the language service unavailable`() {
        server
            .expect(ExpectedCount.once(), requestTo("$BASE_URL/versions"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val result = adapter.check("printscript", "1.1")

        assertTrue((result as Result.Failure).error is LanguageServiceUnavailable, "got $result")
    }

    @Test
    fun `an unreachable engine makes the language service unavailable`() {
        server
            .expect(requestTo("$BASE_URL/validate"))
            .andRespond(withException(IOException("Connection refused")))

        val result = adapter.validate("printscript", "1.1", CODE)

        assertTrue((result as Result.Failure).error is LanguageServiceUnavailable, "got $result")
    }

    @Test
    fun `the user's token is never sent to the engine`() {
        val jwt =
            Jwt
                .withTokenValue("user-access-token")
                .header("alg", "RS256")
                .subject("auth0|franco")
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
        server
            .expect(requestTo("$BASE_URL/versions"))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess(VERSIONS, MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("$BASE_URL/validate"))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess("""{"valid":true,"error":null}""", MediaType.APPLICATION_JSON))

        adapter.check("printscript", "1.1")
        adapter.validate("printscript", "1.1", CODE)
    }

    private fun problem(
        title: String,
        detail: String,
    ) = withStatus(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("""{"title":"$title","status":400,"detail":"$detail"}""")

    private companion object {
        const val BASE_URL = "http://engine-proxy"
        const val LANGUAGE_HEADER = "X-Language"
        const val CODE = "let a: number = ;"
        const val VERSIONS = """{"language":"printscript","versions":["1.0","1.1"],"default":"1.1"}"""
        const val INVALID_WITH_SPAN =
            """{"valid":false,"error":{"rule":"UnexpectedToken","message":"Expected an expression",""" +
                """"span":{"start":{"line":1,"column":17},"end":{"line":1,"column":17}}}}"""
    }
}
