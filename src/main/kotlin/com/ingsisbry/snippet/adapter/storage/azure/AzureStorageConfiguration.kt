package com.ingsisbry.snippet.adapter.storage.azure

import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AzureStorageConfiguration {
    @Bean
    fun blobServiceClient(
        @Value("\${azure.storage.connection-string}")
        connectionString: String,
    ): BlobServiceClient =
        BlobServiceClientBuilder()
            .connectionString(connectionString)
            .buildClient()
}
