package com.ingsisbry.snippet.adapter.scheduling

import com.ingsisbry.snippet.application.command.cleanup.DeleteOrphanedContent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OrphanedContentCleanupJob(
    private val deleteOrphanedContent: DeleteOrphanedContent,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${snippet.orphaned-content-cleanup.cron:0 0 3 * * *}")
    fun run() {
        deleteOrphanedContent.execute().fold(
            onSuccess = { deleted -> logger.info("Orphaned content cleanup deleted {} blob(s)", deleted) },
            onFailure = { error -> logger.warn("Orphaned content cleanup failed: {}", error) },
        )
    }
}
