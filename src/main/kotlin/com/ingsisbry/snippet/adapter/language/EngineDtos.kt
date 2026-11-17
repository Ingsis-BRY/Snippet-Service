package com.ingsisbry.snippet.adapter.language

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.ingsisbry.snippet.application.error.SourcePosition
import com.ingsisbry.snippet.application.error.SourceSpan

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineVersionsResponse(
    val language: String,
    val versions: List<String>,
    val default: String?,
    /** Older engines don't send it. */
    val libraryVersion: String? = null,
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

/** One rule of `GET /rules/{kind}`; `type` is `boolean`, `integer` or `enum`. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineRuleDescriptor(
    val key: String,
    val type: String,
    val default: Any?,
    val since: String,
    val description: String = "",
    val options: List<String>? = null,
    val min: Int? = null,
    val max: Int? = null,
)

internal data class EngineLintRequest(
    val code: String,
    val version: String,
    val config: Map<String, Any>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineLintResponse(
    val valid: Boolean,
    val violations: List<EngineIssue> = emptyList(),
    val error: EngineIssue? = null,
    val ignoredRules: List<String> = emptyList(),
)

internal data class EngineFormatRequest(
    val code: String,
    val version: String,
    val config: Map<String, Any>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineFormatResponse(
    val formatted: String? = null,
    val error: EngineIssue? = null,
    val ignoredRules: List<String> = emptyList(),
)

internal data class EngineTestCase(
    val id: String,
    val inputs: List<String>,
    val expectedOutputs: List<String>,
)

internal data class EngineTestRequest(
    val code: String,
    val version: String,
    val tests: List<EngineTestCase>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineTestResult(
    val id: String,
    val passed: Boolean,
    val actualOutputs: List<String> = emptyList(),
    val error: EngineIssue? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineTestResponse(
    val results: List<EngineTestResult> = emptyList(),
)
