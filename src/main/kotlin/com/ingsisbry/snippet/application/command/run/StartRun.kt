package com.ingsisbry.snippet.application.command.run

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.run.RunListener
import com.ingsisbry.snippet.application.port.out.run.RunSession
import com.ingsisbry.snippet.domain.Snippet

/** Opens an interactive run of the snippet's current version (US#10). The caller must already have READ. */
fun interface StartRun {
    fun execute(
        snippet: Snippet,
        listener: RunListener,
    ): Result<SnippetError, RunSession>
}
