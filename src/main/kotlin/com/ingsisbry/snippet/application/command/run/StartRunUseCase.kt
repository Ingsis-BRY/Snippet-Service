package com.ingsisbry.snippet.application.command.run

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.run.InteractiveRunner
import com.ingsisbry.snippet.application.port.out.run.RunListener
import com.ingsisbry.snippet.application.port.out.run.RunSession
import com.ingsisbry.snippet.domain.Snippet
import org.springframework.stereotype.Service

@Service
class StartRunUseCase(
    private val runner: InteractiveRunner,
) : StartRun {
    override fun execute(
        snippet: Snippet,
        listener: RunListener,
    ): Result<SnippetError, RunSession> =
        runner.open(
            language = snippet.language,
            languageVersion = snippet.languageVersion,
            code = snippet.currentVersion.originalContent,
            listener = listener,
        )
}
