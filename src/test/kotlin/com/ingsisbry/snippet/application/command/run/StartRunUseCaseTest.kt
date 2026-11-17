package com.ingsisbry.snippet.application.command.run

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.language.TestSpec
import com.ingsisbry.snippet.application.port.out.run.InteractiveRunner
import com.ingsisbry.snippet.application.port.out.run.RunEvent
import com.ingsisbry.snippet.application.port.out.run.RunListener
import com.ingsisbry.snippet.application.port.out.run.RunSession
import com.ingsisbry.snippet.domain.Snippet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class StartRunUseCaseTest {
    private val snippet =
        Snippet.create(
            id = UUID.randomUUID(),
            name = "hello",
            description = "",
            language = "printscript",
            languageVersion = "1.1",
            owner = "auth0|franco",
            content = "println(1);",
            versionId = UUID.randomUUID(),
            createdAt = Instant.parse("2026-11-17T00:00:00Z"),
        )

    private val listener =
        object : RunListener {
            override fun onEvent(event: RunEvent) = Unit

            override fun onClosed(error: SnippetError?) = Unit
        }

    private val session =
        object : RunSession {
            override fun start() = Unit

            override fun startTest(test: TestSpec) = Unit

            override fun input(value: String) = Unit

            override fun close() = Unit
        }

    @Test
    fun `runs the snippet's current content with its language and version`() {
        val calls = mutableListOf<List<Any>>()
        val useCase =
            StartRunUseCase(
                InteractiveRunner { language, version, code, listener ->
                    calls += listOf(language, version, code, listener)
                    Result.Success(session)
                },
            )

        val result = useCase.execute(snippet, listener)

        assertSame(session, (result as Result.Success).value)
        assertEquals(listOf(listOf("printscript", "1.1", "println(1);", listener)), calls)
    }

    @Test
    fun `an engine that can't be reached is reported as is`() {
        val unavailable = LanguageServiceUnavailable("could not connect to the engine")
        val useCase = StartRunUseCase { _, _, _, _ -> Result.Failure(unavailable) }

        assertEquals(Result.Failure(unavailable), useCase.execute(snippet, listener))
    }
}
