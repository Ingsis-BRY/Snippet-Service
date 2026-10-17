package com.ingsisbry.snippet.adapter.web.mapper

import com.ingsisbry.snippet.application.query.download.SnippetContentVariant
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Component
class SnippetDownloadMapper {
    fun toVariant(version: String): SnippetContentVariant =
        when (version.lowercase()) {
            "original" -> SnippetContentVariant.ORIGINAL
            "formatted" -> SnippetContentVariant.FORMATTED
            else -> throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported snippet content variant: $version",
            )
        }

    fun toResponse(
        snippetId: UUID,
        variant: SnippetContentVariant,
        content: String,
    ): ResponseEntity<ByteArray> {
        val fileName = "snippet-$snippetId-${variant.name.lowercase()}.txt"

        return ResponseEntity
            .ok()
            .contentType(MediaType.TEXT_PLAIN)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(fileName)
                    .build()
                    .toString(),
            ).body(content.toByteArray(Charsets.UTF_8))
    }
}
