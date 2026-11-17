package com.ingsisbry.snippet.adapter.worker

import com.ingsisbry.snippet.application.command.reprocess.RequeueStaleAnalyses
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Requeues analyses whose job was lost; the database, not Redis, is the source of truth for what is pending. */
@Component
@Profile("worker")
class PendingAnalysisSweeper(
    private val requeueStaleAnalyses: RequeueStaleAnalyses,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${worker.sweep-interval}", initialDelayString = "\${worker.sweep-interval}")
    fun sweep() {
        requeueStaleAnalyses.execute().fold(
            onSuccess = { count -> if (count > 0) logger.info("Requeued {} stale snippet analyses", count) },
            onFailure = { error -> logger.warn("Requeueing stale analyses failed: {}", error) },
        )
    }
}
