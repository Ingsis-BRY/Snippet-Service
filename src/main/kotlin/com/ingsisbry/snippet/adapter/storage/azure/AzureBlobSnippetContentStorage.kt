package com.ingsisbry.snippet.adapter.storage.azure

import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobServiceClient
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import org.springframework.stereotype.Component

@Component
class AzureBlobSnippetContentStorage(
    blobServiceClient: BlobServiceClient,
) : SnippetContentStorage {
    companion object {
        private const val CONTAINER_NAME = "snippets"
    }

    private val containerClient =
        blobServiceClient.getBlobContainerClient(CONTAINER_NAME)

    init {
        containerClient.createIfNotExists()
    }

    override fun store(
        key: String,
        content: String,
    ) {
        val blobClient = containerClient.getBlobClient(key)

        blobClient.upload(
            BinaryData.fromString(content),
            true,
        )
    }

    override fun retrieve(key: String): String {
        val blobClient = containerClient.getBlobClient(key)

        return blobClient.downloadContent().toString()
    }

    override fun delete(key: String) {
        val blobClient = containerClient.getBlobClient(key)

        blobClient.deleteIfExists()
    }
}
