package com.ingsisbry.snippet.adapter

import com.ingsisbry.snippet.adapter.redis.RedisJobStream
import com.ingsisbry.snippet.adapter.redis.RedisSnippetJobPublisher
import com.ingsisbry.snippet.adapter.run.RunWebSocketConfiguration
import com.ingsisbry.snippet.adapter.run.SnippetRunHandshakeInterceptor
import com.ingsisbry.snippet.adapter.run.SnippetRunWebSocketHandler
import com.ingsisbry.snippet.adapter.scheduling.OrphanedContentCleanupJob
import com.ingsisbry.snippet.adapter.security.SecurityConfiguration
import com.ingsisbry.snippet.adapter.security.WebConfiguration
import com.ingsisbry.snippet.adapter.worker.ParserVersionWatcher
import com.ingsisbry.snippet.adapter.worker.PendingAnalysisSweeper
import com.ingsisbry.snippet.adapter.worker.SnippetJobConsumer
import com.ingsisbry.snippet.adapter.worker.WorkerConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.RestController
import kotlin.reflect.KClass

/**
 * One image, two roles: the `worker` profile must not serve HTTP (no controllers, security or sockets) and the API
 * must not consume the stream. Checked on the annotations, without starting a context.
 */
class WorkerProfileTest {
    @Test
    fun `every controller is left out of the worker`() {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        val candidates = scanner.findCandidateComponents("com.ingsisbry.snippet")
        val controllers = candidates.map { Class.forName(it.beanClassName) }

        assertTrue(controllers.size >= 7, "found $controllers")
        controllers.forEach { assertEquals(listOf("!worker"), profiles(it.kotlin), it.name) }
    }

    @Test
    fun `security, the run socket and the cleanup job are left out of the worker`() {
        listOf(
            SecurityConfiguration::class,
            WebConfiguration::class,
            RunWebSocketConfiguration::class,
            SnippetRunWebSocketHandler::class,
            SnippetRunHandshakeInterceptor::class,
            OrphanedContentCleanupJob::class,
        ).forEach { assertEquals(listOf("!worker"), profiles(it), it.simpleName) }
    }

    @Test
    fun `the consumer, sweeper and parser watcher run only in the worker`() {
        listOf(
            SnippetJobConsumer::class,
            PendingAnalysisSweeper::class,
            ParserVersionWatcher::class,
            WorkerConfiguration::class,
            RedisJobStream::class,
        ).forEach { assertEquals(listOf("worker"), profiles(it), it.simpleName) }
    }

    @Test
    fun `both roles publish jobs`() {
        assertNull(AnnotationUtils.findAnnotation(RedisSnippetJobPublisher::class.java, Profile::class.java))
    }

    private fun profiles(type: KClass<*>): List<String>? {
        val profile = AnnotationUtils.findAnnotation(type.java, Profile::class.java)
        return profile?.value?.toList()
    }
}
