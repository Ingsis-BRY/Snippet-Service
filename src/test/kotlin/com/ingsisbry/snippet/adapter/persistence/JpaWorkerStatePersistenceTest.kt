package com.ingsisbry.snippet.adapter.persistence

import com.ingsisbry.snippet.adapter.persistence.transaction.SpringTransactionRunner
import com.ingsisbry.snippet.adapter.persistence.workerstate.JpaWorkerStateRepositoryAdapter
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** `worker_state` compare-and-set and the transaction runner, on in-memory H2; each call commits as in production. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(
    JpaWorkerStateRepositoryAdapter::class,
    SpringTransactionRunner::class,
    JpaWorkerStatePersistenceTest.Config::class,
)
class JpaWorkerStatePersistenceTest {
    @Autowired
    private lateinit var state: JpaWorkerStateRepositoryAdapter

    @Autowired
    private lateinit var transactions: SpringTransactionRunner

    private val key = "engine.library-version.${UUID.randomUUID()}"

    @Test
    fun `a missing key reads as null and is set only when null is expected`() {
        assertEquals(Result.Success(null), state.get(key))

        assertEquals(Result.Success(false), state.compareAndSet(key, "1.1.2", "1.1.3"))
        assertEquals(Result.Success(true), state.compareAndSet(key, null, "1.1.3"))
        assertEquals(Result.Success("1.1.3"), state.get(key))
    }

    @Test
    fun `a set key changes only from the value it holds`() {
        state.compareAndSet(key, null, "1.1.2")

        assertEquals(Result.Success(false), state.compareAndSet(key, null, "1.1.3"))
        assertEquals(Result.Success(false), state.compareAndSet(key, "1.0.0", "1.1.3"))
        assertEquals(Result.Success(true), state.compareAndSet(key, "1.1.2", "1.1.3"))
        assertEquals(Result.Success("1.1.3"), state.get(key))
    }

    @Test
    fun `a failure inside the transaction rolls back what the adapters wrote in it`() {
        val result =
            transactions.inTransaction {
                state.compareAndSet(key, null, "1.1.3").flatMap { Result.Failure(InvalidInput("later step failed")) }
            }

        assertEquals(Result.Failure(InvalidInput("later step failed")), result)
        assertEquals(Result.Success(null), state.get(key))
    }

    @Test
    fun `a successful transaction commits`() {
        assertEquals(Result.Success(true), transactions.inTransaction { state.compareAndSet(key, null, "1.1.3") })
        assertEquals(Result.Success("1.1.3"), state.get(key))
    }

    @TestConfiguration
    class Config {
        @Bean
        fun clock(): Clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC)
    }
}
