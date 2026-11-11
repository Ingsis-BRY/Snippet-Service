package com.ingsisbry.snippet.adapter.web.mapper

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile

class SnippetFileReaderTest {
    private fun file(bytes: ByteArray) = MockMultipartFile("file", "a.ps", "text/plain", bytes)

    @Test
    fun `reads utf-8 content`() {
        val result = SnippetFileReader.read(file("let x: number = 1;".toByteArray()))

        assertEquals(Result.Success("let x: number = 1;"), result)
    }

    @Test
    fun `rejects an empty file`() {
        val result = SnippetFileReader.read(file(ByteArray(0)))

        assertTrue((result as Result.Failure).error is InvalidInput)
    }

    @Test
    fun `rejects bytes that are not utf-8`() {
        val result = SnippetFileReader.read(file(byteArrayOf(0xC3.toByte(), 0x28)))

        assertTrue((result as Result.Failure).error is InvalidInput)
    }
}
