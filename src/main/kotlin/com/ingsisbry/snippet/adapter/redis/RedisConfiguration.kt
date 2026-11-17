package com.ingsisbry.snippet.adapter.redis

import com.ingsisbry.snippet.adapter.worker.WorkerProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Both profiles publish to the stream, so the stream settings exist in both. The connection itself
 * (`spring.data.redis.*`) is Spring Boot's; Lettuce connects on first use, so the API starts without Redis.
 */
@Configuration
@EnableConfigurationProperties(WorkerProperties::class)
class RedisConfiguration
