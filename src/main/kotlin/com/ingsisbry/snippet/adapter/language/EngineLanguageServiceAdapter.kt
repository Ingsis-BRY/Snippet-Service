package com.ingsisbry.snippet.adapter.language

import com.ingsisbry.snippet.adapter.language.EngineRequests.Companion.LANGUAGE_HEADER
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.UnsupportedLanguage
import com.ingsisbry.snippet.application.port.out.language.SnippetContentValidator
import com.ingsisbry.snippet.application.port.out.language.SupportedLanguages
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Asks the language engine, through the engine proxy, which versions exist and whether code is valid.
 * The proxy picks the engine from `X-Language`, so the paths never name a language.
 */
@Component
class EngineLanguageServiceAdapter(
    @param:Qualifier("engineClient")
    private val engineClient: RestClient,
) : SupportedLanguages,
    SnippetContentValidator {
    private val requests = EngineRequests(LoggerFactory.getLogger(javaClass))

    override fun check(
        language: String,
        languageVersion: String,
    ): Result<SnippetError, Unit> =
        requests
            .call("GET /versions", language, languageVersion) {
                engineClient
                    .get()
                    .uri("/versions")
                    .header(LANGUAGE_HEADER, language)
                    .retrieve()
                    .body(EngineVersionsResponse::class.java)
            }.flatMap { response ->
                if (response != null && languageVersion in response.versions) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(UnsupportedLanguage(language, languageVersion))
                }
            }

    override fun validate(
        language: String,
        languageVersion: String,
        content: String,
    ): Result<SnippetError, Unit> =
        requests
            .call("POST /validate", language, languageVersion) {
                engineClient
                    .post()
                    .uri("/validate")
                    .header(LANGUAGE_HEADER, language)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(EngineValidateRequest(code = content, version = languageVersion))
                    .retrieve()
                    .body(EngineValidateResponse::class.java)
            }.flatMap { response -> toValidation(response) }

    private fun toValidation(response: EngineValidateResponse?): Result<SnippetError, Unit> {
        val issue = response?.error
        return when {
            response == null -> requests.unavailable("the engine answered /validate without a body")
            response.valid -> Result.Success(Unit)
            issue == null -> requests.unavailable("the engine answered valid=false without an error")
            else -> Result.Failure(InvalidSnippetContent(issue.rule, issue.message, issue.span?.toSourceSpan()))
        }
    }
}
