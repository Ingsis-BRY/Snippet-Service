package com.ingsisbry.snippet.adapter.language

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.UnsupportedLanguage
import org.slf4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/** How every call to the engine proxy turns HTTP outcomes into [SnippetError]s. */
internal class EngineRequests(
    private val logger: Logger,
) {
    /**
     * Runs [request], retrying it once at once on 502/503/504: the proxy can still answer those while a
     * dead engine instance drops out of DNS, and every engine operation is safe to repeat.
     */
    fun <T> call(
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

    fun unavailable(reason: String): Result<SnippetError, Nothing> {
        logger.error("Language engine broke its contract: {}", reason)
        return Result.Failure(LanguageServiceUnavailable(reason))
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

    companion object {
        const val LANGUAGE_HEADER = "X-Language"
        private const val UNKNOWN_LANGUAGE = "UnknownLanguage"
        private const val UNSUPPORTED_VERSION = "UnsupportedVersion"
        private val RETRYABLE_STATUSES = setOf(502, 503, 504)
    }
}
