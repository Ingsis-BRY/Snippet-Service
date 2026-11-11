package com.ingsisbry.snippet.adapter.storage.azure

import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.common.policy.RequestRetryOptions
import com.azure.storage.common.policy.RetryPolicyType
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
            // Fail fast instead of hanging a request thread when Azure is slow or unreachable.
            .retryOptions(
                RequestRetryOptions(
                    RetryPolicyType.EXPONENTIAL,
                    MAX_TRIES,
                    TRY_TIMEOUT_SECONDS,
                    null,
                    null,
                    null,
                ),
            ).buildClient()

    private companion object {
        const val MAX_TRIES = 3
        const val TRY_TIMEOUT_SECONDS = 10
    }
}
