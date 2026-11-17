package com.ingsisbry.snippet.adapter.worker

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** `worker.*` in application.yml. The API uses the stream settings too, to publish. */
@ConfigurationProperties("worker")
data class WorkerProperties(
    val stream: String = "snippet.analysis",
    val deadLetterStream: String = "snippet.analysis.dlq",
    val group: String = "snippet-workers",
    val consumer: String = "worker-1",
    val batchSize: Int = 10,
    val block: Duration = Duration.parse("PT2S"),
    /** A message delivered more times than this is given up: its tasks are marked failed. */
    val maxDeliveries: Int = 5,
    val claimMinIdle: Duration = Duration.parse("PT60S"),
    val claimInterval: Duration = Duration.parse("PT30S"),
    val sweepInterval: Duration = Duration.parse("PT5M"),
    val staleAfter: Duration = Duration.parse("PT10M"),
    val parserCheckInterval: Duration = Duration.parse("PT10M"),
    val streamMaxLength: Long = 100_000,
)
