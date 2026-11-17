package com.ingsisbry.snippet.support

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.ContentMissing
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.application.port.out.storage.StoredContent
import java.time.Instant

class InMemoryContentStorage : SnippetContentStorage {
    val blobs = linkedMapOf<String, String>()

    override fun store(
        key: String,
        content: String,
    ): Result<SnippetError, Unit> {
        blobs[key] = content
        return Result.Success(Unit)
    }

    override fun retrieve(key: String): Result<SnippetError, String> =
        blobs[key]?.let { Result.Success(it) } ?: Result.Failure(ContentMissing(key))

    override fun delete(key: String): Result<SnippetError, Unit> {
        blobs.remove(key)
        return Result.Success(Unit)
    }

    override fun listAll(): Result<SnippetError, List<StoredContent>> {
        val stored = blobs.keys.map { StoredContent(it, Instant.EPOCH) }
        return Result.Success(stored)
    }
}
