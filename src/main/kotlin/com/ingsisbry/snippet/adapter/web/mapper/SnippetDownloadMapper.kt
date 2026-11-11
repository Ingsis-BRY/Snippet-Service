package com.ingsisbry.snippet.adapter.web.mapper

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.query.download.SnippetContentVariant
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SnippetDownloadMapper {
    fun toVariant(version: String): Result<SnippetError, SnippetContentVariant> =
        when (version.lowercase()) {
            "original" -> Result.Success(SnippetContentVariant.ORIGINAL)
            "formatted" -> Result.Success(SnippetContentVariant.FORMATTED)
            else -> Result.Failure(InvalidInput("Unsupported snippet content variant: $version"))
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
