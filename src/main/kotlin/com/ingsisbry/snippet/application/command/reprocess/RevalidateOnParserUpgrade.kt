package com.ingsisbry.snippet.application.command.reprocess

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

enum class ParserCheck {
    /** The engine does not report its library version, so there is nothing to compare. */
    UNKNOWN,

    /** Same library version as last time. */
    UNCHANGED,

    /** Another worker noticed the new version first and requeues the snippets. */
    TAKEN_BY_ANOTHER_WORKER,

    /** Every snippet was marked pending and requeued. */
    REQUEUED,
}

/**
 * Re-validates every snippet when the engine ships a new parser (Consigna): compares the engine's library version
 * with the last one seen and, when it changed, marks every snippet pending and requeues it.
 */
fun interface RevalidateOnParserUpgrade {
    fun execute(): Result<SnippetError, ParserCheck>
}
