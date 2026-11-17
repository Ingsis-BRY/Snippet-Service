package com.ingsisbry.snippet.adapter.language

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.UnsupportedLanguage
import com.ingsisbry.snippet.application.port.out.language.SnippetContentValidator
import com.ingsisbry.snippet.application.port.out.language.SupportedLanguages
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

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
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun check(
        language: String,
        languageVersion: String,
    ): Result<SnippetError, Unit> =
        call("GET /versions", language, languageVersion) {
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
        call("POST /validate", language, languageVersion) {
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
            response == null -> unavailable("the engine answered /validate without a body")
            response.valid -> Result.Success(Unit)
            issue == null -> unavailable("the engine answered valid=false without an error")
            else -> Result.Failure(InvalidSnippetContent(issue.rule, issue.message, issue.span?.toSourceSpan()))
        }
    }

    /**
     * Runs [request], retrying it once at once on 502/503/504: the proxy can still answer those while a
     * dead engine instance drops out of DNS, and every engine operation is safe to repeat.
     */
    private fun <T> call(
        operation: String,
        language: String,
        languageVersion: String,
        request: () -> T,
    ): Result<SnippetError, T> =
        try {
            Result.Success(retryingOnce(request))
        } catch (exception: RestClientResponseException) {
            Result.Failure(toError(exception, operation, language, languageVersion))
        } catch (exception: RestClientException) {
            logger.error("Language engine is unreachable on {}", operation, exception)
            Result.Failure(LanguageServiceUnavailable(exception.message))
        }

    private fun <T> retryingOnce(request: () -> T): T =
        try {
            request()
        } catch (exception: RestClientResponseException) {
            if (exception.statusCode.value() in RETRYABLE_STATUSES) {
                logger.warn("Language engine answered {} on a request, retrying once", exception.statusCode.value())
                request()
            } else {
                throw exception
            }
        }

    private fun toError(
        exception: RestClientResponseException,
        operation: String,
        language: String,
        languageVersion: String,
    ): SnippetError {
        if (!exception.statusCode.isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            logger.error(
                "Language engine answered {} {} on {}",
                exception.statusCode.value(),
                exception.statusText,
                operation,
            )
            return LanguageServiceUnavailable("${exception.statusCode.value()} ${exception.statusText}")
        }
        val problem =
            runCatching { exception.getResponseBodyAs(EngineProblem::class.java) }
                .getOrNull()
        return when (problem?.title) {
            UNKNOWN_LANGUAGE, UNSUPPORTED_VERSION -> UnsupportedLanguage(language, languageVersion)
            else -> InvalidInput(problem?.detail ?: problem?.title ?: "Rejected by the language engine")
        }
    }

    private fun unavailable(reason: String): Result<SnippetError, Nothing> {
        logger.error("Language engine broke its contract: {}", reason)
        return Result.Failure(LanguageServiceUnavailable(reason))
    }

    private companion object {
        const val LANGUAGE_HEADER = "X-Language"
        const val UNKNOWN_LANGUAGE = "UnknownLanguage"
        const val UNSUPPORTED_VERSION = "UnsupportedVersion"
        val RETRYABLE_STATUSES = setOf(502, 503, 504)
    }
}
