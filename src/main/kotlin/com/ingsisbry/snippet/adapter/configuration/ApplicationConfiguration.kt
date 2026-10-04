package com.ingsisbry.snippet.adapter.configuration

import com.ingsisbry.snippet.adapter.persistence.InMemorySnippetRepository
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ApplicationConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun snippetRepository(): SnippetRepository = InMemorySnippetRepository()
}
