package com.ingsisbry.snippet.application.port.out.run

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

/** Receives what a run reports. Called from the runner's own threads. */
interface RunListener {
    fun onEvent(event: RunEvent)

    /** The session ended; [error] is null when it was closed through [RunSession.close]. */
    fun onClosed(error: SnippetError?)
}

/** One open connection to a language engine, which can run the same code several times, one run at a time. */
interface RunSession {
    /** Starts a run; the engine rejects it while another run is in progress. */
    fun start()

    /** Answers the current prompt; answers sent early are queued by the engine. */
    fun input(value: String)

    /** Ends the session and cancels any run still in progress. */
    fun close()
}

fun interface InteractiveRunner {
    fun open(
        language: String,
        languageVersion: String,
        code: String,
        listener: RunListener,
    ): Result<SnippetError, RunSession>
}
