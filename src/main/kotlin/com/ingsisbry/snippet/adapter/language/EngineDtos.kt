package com.ingsisbry.snippet.adapter.language

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.ingsisbry.snippet.application.error.SourcePosition
import com.ingsisbry.snippet.application.error.SourceSpan

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineVersionsResponse(
    val language: String,
    val versions: List<String>,
    val default: String?,
)

internal data class EngineValidateRequest(
    val code: String,
    val version: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineValidateResponse(
    val valid: Boolean,
    val error: EngineIssue?,
)

/** A problem in the code, as every engine reports it. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineIssue(
    val rule: String,
    val message: String,
    val span: EngineSpan?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineSpan(
    val start: EnginePosition,
    val end: EnginePosition,
) {
    fun toSourceSpan(): SourceSpan = SourceSpan(start.toSourcePosition(), end.toSourcePosition())
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EnginePosition(
    val line: Int,
    val column: Int,
) {
    fun toSourcePosition(): SourcePosition = SourcePosition(line, column)
}

/** The engine's 400 `ProblemDetail`; only the fields we act on. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineProblem(
    val title: String?,
    val detail: String?,
)
