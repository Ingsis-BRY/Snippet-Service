package com.ingsisbry.snippet.adapter.worker

import com.ingsisbry.snippet.application.command.reprocess.RequeueStaleAnalyses
import com.ingsisbry.snippet.application.command.reprocess.RequeueStaleAnalysesUseCase
import com.ingsisbry.snippet.application.port.out.analysis.SnippetAnalysisRepository
import com.ingsisbry.snippet.application.port.out.job.SnippetJobPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Clock

@Configuration
@Profile("worker")
class WorkerConfiguration {
    @Bean
    fun requeueStaleAnalyses(
        analysis: SnippetAnalysisRepository,
        publisher: SnippetJobPublisher,
        clock: Clock,
        properties: WorkerProperties,
    ): RequeueStaleAnalyses = RequeueStaleAnalysesUseCase(analysis, publisher, clock, properties.staleAfter)
}
