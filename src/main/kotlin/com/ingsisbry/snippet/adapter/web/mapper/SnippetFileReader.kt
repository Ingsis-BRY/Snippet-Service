package com.ingsisbry.snippet.adapter.web.mapper

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

object SnippetFileReader {
    fun read(file: MultipartFile): Result<SnippetError, String> {
        if (file.isEmpty) return Result.Failure(InvalidInput("The uploaded file must not be empty"))

        return try {
            val decoder =
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            Result.Success(decoder.decode(ByteBuffer.wrap(file.bytes)).toString())
        } catch (_: CharacterCodingException) {
            Result.Failure(InvalidInput("The uploaded file must be UTF-8 text"))
        } catch (_: IOException) {
            Result.Failure(InvalidInput("The uploaded file could not be read"))
        }
    }
}
