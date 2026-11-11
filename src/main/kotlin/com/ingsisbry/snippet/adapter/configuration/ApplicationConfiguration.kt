package com.ingsisbry.snippet.adapter.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

@Configuration
@EnableScheduling
class ApplicationConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
