package com.ingsisbry.snippet.application.command.common

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ContentBackedWrite")

/**
 * Stores [content] under [key] and then runs [persist] (the database write).
 *
 * The blob store and the database cannot share a transaction, so the blob is written first under an
 * immutable, never-reused key. If [persist] fails the blob is deleted on a best-effort basis; a failed
 * cleanup is only logged so it never hides the original error.
 */
fun <T> SnippetContentStorage.storeThenPersist(
    key: String,
    content: String,
    persist: () -> Result<SnippetError, T>,
): Result<SnippetError, T> =
    store(key = key, content = content).flatMap {
        persist().onFailure {
            delete(key).onFailure { cleanupError ->
                logger.warn("Failed to clean up orphaned snippet blob {}: {}", key, cleanupError)
            }
        }
    }
