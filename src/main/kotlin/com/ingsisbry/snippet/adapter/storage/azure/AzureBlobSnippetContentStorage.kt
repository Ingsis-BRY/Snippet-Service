package com.ingsisbry.snippet.adapter.storage.azure

import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.BlobStorageException
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.ContentMissing
import com.ingsisbry.snippet.application.error.ContentStoreUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AzureBlobSnippetContentStorage(
    blobServiceClient: BlobServiceClient,
) : SnippetContentStorage {
    companion object {
        private const val CONTAINER_NAME = "snippets"
        private const val HTTP_NOT_FOUND = 404
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val containerClient =
        blobServiceClient.getBlobContainerClient(CONTAINER_NAME)

    init {
        containerClient.createIfNotExists()
    }

    override fun store(
        key: String,
        content: String,
    ): Result<SnippetError, Unit> =
        guarded(key) {
            containerClient.getBlobClient(key).upload(BinaryData.fromString(content), true)
            Result.Success(Unit)
        }

    override fun retrieve(key: String): Result<SnippetError, String> =
        guarded(key) {
            try {
                Result.Success(containerClient.getBlobClient(key).downloadContent().toString())
            } catch (exception: BlobStorageException) {
                if (exception.statusCode == HTTP_NOT_FOUND) {
                    Result.Failure(ContentMissing(key))
                } else {
                    throw exception
                }
            }
        }

    override fun delete(key: String): Result<SnippetError, Unit> =
        guarded(key) {
            containerClient.getBlobClient(key).deleteIfExists()
            Result.Success(Unit)
        }

    /** Translates any Azure SDK failure (HTTP error, timeout, I/O) into a domain error. */
    @Suppress("TooGenericExceptionCaught")
    private fun <T> guarded(
        key: String,
        block: () -> Result<SnippetError, T>,
    ): Result<SnippetError, T> =
        try {
            block()
        } catch (exception: RuntimeException) {
            logger.error("Blob storage operation failed for key {}", key, exception)
            Result.Failure(ContentStoreUnavailable(key, exception.message))
        }
}
