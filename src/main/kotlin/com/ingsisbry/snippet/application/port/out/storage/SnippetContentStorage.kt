package com.ingsisbry.snippet.application.port.out.storage

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

interface SnippetContentStorage {
    fun store(
        key: String,
        content: String,
    ): Result<SnippetError, Unit>

    fun retrieve(key: String): Result<SnippetError, String>

    fun delete(key: String): Result<SnippetError, Unit>
}
