package com.ingsisbry.snippet.adapter.web.mapper

import com.ingsisbry.snippet.application.error.ContentMissing
import com.ingsisbry.snippet.application.error.ContentStoreUnavailable
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.PersistenceUnavailable
import com.ingsisbry.snippet.application.error.SnippetConflict
import com.ingsisbry.snippet.application.error.SnippetCorrupted
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.error.UnsupportedLanguage
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class SnippetErrorMapper {
    fun toResponse(error: SnippetError): ResponseEntity<ProblemDetail> {
        val (status, detail) =
            when (error) {
                is SnippetNotFound -> HttpStatus.NOT_FOUND to "Snippet ${error.id} not found"
                is SnippetConflict -> HttpStatus.CONFLICT to "Snippet ${error.id} conflicts with existing data"
                is InvalidInput -> HttpStatus.BAD_REQUEST to error.reason
                is InvalidSnippetContent ->
                    HttpStatus.BAD_REQUEST to
                        "Snippet breaks the rule '${error.rule}' at line ${error.line}, column ${error.column}"
                is UnsupportedLanguage ->
                    HttpStatus.BAD_REQUEST to "Language ${error.language} ${error.languageVersion} is not supported"
                is LanguageServiceUnavailable -> HttpStatus.SERVICE_UNAVAILABLE to "Language service is unavailable"
                is ContentStoreUnavailable -> HttpStatus.SERVICE_UNAVAILABLE to "Snippet content storage is unavailable"
                is PersistenceUnavailable -> HttpStatus.SERVICE_UNAVAILABLE to "Snippet database is unavailable"
                is ContentMissing -> HttpStatus.INTERNAL_SERVER_ERROR to "Snippet content is missing"
                is SnippetCorrupted -> HttpStatus.INTERNAL_SERVER_ERROR to "Snippet data is inconsistent"
            }

        return ResponseEntity
            .status(status)
            .body(ProblemDetail.forStatusAndDetail(status, detail))
    }
}
