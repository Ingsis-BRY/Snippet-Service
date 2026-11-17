package com.ingsisbry.snippet.support

import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.SourcePosition
import com.ingsisbry.snippet.application.error.SourceSpan

/** Where the engine reports that `let a: number = ;` breaks a rule. */
val SAMPLE_SPAN = SourceSpan(SourcePosition(line = 1, column = 17), SourcePosition(line = 1, column = 17))

/** What the engine reports for `let a: number = ;`. */
val SAMPLE_INVALID_CONTENT = InvalidSnippetContent("UnexpectedToken", "Expected an expression", SAMPLE_SPAN)
