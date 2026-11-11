package com.ingsisbry.snippet.application.common.result

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResultTest {
    private val success: Result<String, Int> = Result.Success(2)
    private val failure: Result<String, Int> = Result.Failure("boom")

    @Test
    fun `fold picks the branch matching the result`() {
        assertEquals("ok 2", success.fold({ "ok $it" }, { "error $it" }))
        assertEquals("error boom", failure.fold({ "ok $it" }, { "error $it" }))
    }

    @Test
    fun `map transforms only the success value`() {
        assertEquals(Result.Success(4), success.map { it * 2 })
        assertEquals(Result.Failure("boom"), failure.map { it * 2 })
    }

    @Test
    fun `flatMap chains and short-circuits on the first failure`() {
        var calls = 0

        val chained =
            success
                .flatMap { Result.Failure("first") }
                .flatMap {
                    calls++
                    Result.Success(it)
                }

        assertEquals(Result.Failure("first"), chained)
        assertEquals(0, calls)
        assertEquals(Result.Success(3), success.flatMap { Result.Success(it + 1) })
    }

    @Test
    fun `mapError transforms only the failure`() {
        assertEquals(Result.Failure(4), failure.mapError { it.length })
        assertEquals(Result.Success(2), success.mapError { it.length })
    }

    @Test
    fun `onSuccess and onFailure run side effects only on their branch and return the same result`() {
        val seen = mutableListOf<String>()

        assertEquals(success, success.onSuccess { seen += "s$it" }.onFailure { seen += "f$it" })
        assertEquals(failure, failure.onSuccess { seen += "s$it" }.onFailure { seen += "f$it" })

        assertEquals(listOf("s2", "fboom"), seen)
    }
}
