package com.ingsisbry.snippet

import com.ingsisbry.snippet.adapter.redis.RedisSnippetJobPublisher
import com.ingsisbry.snippet.adapter.worker.ParserVersionWatcher
import com.ingsisbry.snippet.adapter.worker.PendingAnalysisSweeper
import com.ingsisbry.snippet.adapter.worker.SnippetJobConsumer
import com.ingsisbry.snippet.application.command.process.ProcessSnippetJob
import com.ingsisbry.snippet.application.command.reprocess.ParserCheck
import com.ingsisbry.snippet.application.command.reprocess.RequeueStaleAnalyses
import com.ingsisbry.snippet.application.command.reprocess.RevalidateOnParserUpgrade
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.port.out.job.SnippetJobPublisher
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext

/**
 * The real application started with the `worker` profile, on in-memory H2: no web server, no controllers, no
 * security, and the consumer, sweeper and parser watcher wired. Redis, the engine and Azure are never contacted:
 * the consumer, the parser check and the blob storage are mocked, and Lettuce only connects on first use.
 */
@SpringBootTest(
    classes = [SnippetApplication::class],
    properties = [
        "spring.profiles.active=worker",
        "spring.datasource.url=jdbc:h2:mem:worker-context;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.main.keep-alive=false",
        "AUTH0_DOMAIN=auth0.test.invalid",
        "AUTH0_AUDIENCE=https://snippet-searcher-api",
        "AUTH0_M2M_CLIENT_ID=test",
        "AUTH0_M2M_CLIENT_SECRET=test",
        "azure.storage.connection-string=" +
            "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=a2V5;" +
            "BlobEndpoint=http://127.0.0.1:1/devstoreaccount1;",
        // embedded-azurite would start a container; this test needs none
        "embedded.containers.enabled=false",
        "embedded.azurite.enabled=false",
    ],
)
class WorkerContextTest {
    @Autowired
    private lateinit var context: ApplicationContext

    @MockitoBean
    private lateinit var consumer: SnippetJobConsumer

    @TestBean
    private lateinit var revalidateOnParserUpgrade: RevalidateOnParserUpgrade

    /** The Azure adapter checks its container when it starts. */
    @MockitoBean
    private lateinit var contentStorage: SnippetContentStorage

    @Test
    fun `the worker serves no HTTP`() {
        assertFalse(context is WebApplicationContext)
        assertEquals(emptyMap<String, Any>(), context.getBeansWithAnnotation(RestController::class.java))
        assertTrue(context.getBeanNamesForType(SecurityFilterChain::class.java).isEmpty())
    }

    @Test
    fun `the worker consumes, sweeps, watches the parser and publishes to Redis`() {
        context.getBean(PendingAnalysisSweeper::class.java)
        context.getBean(ParserVersionWatcher::class.java)
        context.getBean(ProcessSnippetJob::class.java)
        context.getBean(RequeueStaleAnalyses::class.java)
        assertTrue(context.getBean(SnippetJobPublisher::class.java) is RedisSnippetJobPublisher)
    }

    companion object {
        /** Runs at startup: answers like an engine that does not report its parser version. */
        @JvmStatic
        fun revalidateOnParserUpgrade(): RevalidateOnParserUpgrade {
            val answer = Result.Success(ParserCheck.UNKNOWN)
            return RevalidateOnParserUpgrade { answer }
        }
    }
}
