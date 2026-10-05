package com.ingsisbry.snippet.adapter.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ApplicationConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
