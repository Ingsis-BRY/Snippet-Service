package com.ingsisbry.snippet.adapter.worker

import com.ingsisbry.snippet.application.command.reprocess.ParserCheck
import com.ingsisbry.snippet.application.command.reprocess.RevalidateOnParserUpgrade
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Checks the engine's parser version at startup and every `parser-check-interval` (Consigna). */
@Component
@Profile("worker")
class ParserVersionWatcher(
    private val revalidateOnParserUpgrade: RevalidateOnParserUpgrade,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() = check()

    @Scheduled(
        fixedDelayString = "\${worker.parser-check-interval}",
        initialDelayString = "\${worker.parser-check-interval}",
    )
    fun check() {
        revalidateOnParserUpgrade.execute().fold(
            onSuccess = { check ->
                when (check) {
                    ParserCheck.REQUEUED -> logger.info("New parser version: every snippet was requeued for validation")
                    ParserCheck.UNKNOWN -> logger.info("The engine does not report its parser version")
                    else -> logger.debug("Parser version check: {}", check)
                }
            },
            onFailure = { error -> logger.warn("Parser version check failed, will retry: {}", error) },
        )
    }
}
