package com.ingsisbry.snippet.adapter.persistence.json

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.error.SourcePosition
import com.ingsisbry.snippet.application.error.SourceSpan
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** The JSON stored in TEXT columns: string lists, issues (same shape as the API's) and rule values. */
@Suppress("TooManyFunctions")
internal object PersistenceJson {
    private val json: JsonMapper = JsonMapper.builder().build()

    fun writeStrings(values: List<String>): String = json.writeValueAsString(values)

    fun readStrings(text: String?): List<String>? {
        val node = text?.let { json.readTree(it) } ?: return null
        return elements(node).map { it.asString() }
    }

    fun writeIssue(issue: CodeIssue?): String? = issue?.let { json.writeValueAsString(issueMap(it)) }

    fun readIssue(text: String?): CodeIssue? = text?.let { toIssue(json.readTree(it)) }

    fun writeIssues(issues: List<CodeIssue>): String = json.writeValueAsString(issues.map(::issueMap))

    fun readIssues(text: String?): List<CodeIssue> {
        val node = text?.let { json.readTree(it) } ?: return emptyList()
        return elements(node).mapNotNull(::toIssue)
    }

    /** An Int or a String as a JSON scalar; null stays null. */
    fun writeScalar(value: Any?): String? = value?.let { json.writeValueAsString(it) }

    fun readScalar(text: String?): Any? {
        val node = text?.let { json.readTree(it) }
        return when {
            node == null || node.isNull -> null
            node.isInt -> node.asInt()
            node.isNumber -> node.asLong()
            node.isBoolean -> node.asBoolean()
            else -> node.asString()
        }
    }

    private fun elements(node: JsonNode): List<JsonNode> = (0 until node.size()).map { node.get(it) }

    private fun issueMap(issue: CodeIssue): Map<String, Any?> =
        linkedMapOf(
            "rule" to issue.rule,
            "message" to issue.message,
            "span" to
                issue.span?.let { span ->
                    linkedMapOf("start" to positionMap(span.start), "end" to positionMap(span.end))
                },
        )

    private fun positionMap(position: SourcePosition): Map<String, Int> {
        val (line, column) = position
        return linkedMapOf("line" to line, "column" to column)
    }

    private fun toIssue(node: JsonNode): CodeIssue? {
        if (node.isNull || !node.has("rule")) return null
        val span = node.get("span")?.takeUnless { it.isNull }
        return CodeIssue(
            rule = node.get("rule").asString(),
            message = node.get("message")?.asString().orEmpty(),
            span = span?.let { SourceSpan(toPosition(it.get("start")), toPosition(it.get("end"))) },
        )
    }

    private fun toPosition(node: JsonNode): SourcePosition {
        val line = node.get("line").asInt()
        return SourcePosition(line, node.get("column").asInt())
    }
}
