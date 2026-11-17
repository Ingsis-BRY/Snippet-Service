package com.ingsisbry.snippet.adapter.web.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.ingsisbry.snippet.application.query.rules.RuleView

/** The UI's `Rule`: [id] and [name] are both the engine's rule key. */
data class RuleResponse(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val value: Any?,
    val default: Any?,
    val options: List<String>?,
    val min: Int?,
    val max: Int?,
    val since: String,
) {
    companion object {
        fun from(view: RuleView): RuleResponse {
            val definition = view.definition
            return RuleResponse(
                id = definition.key,
                name = definition.key,
                description = definition.description,
                type = definition.type.toJsonValue(),
                isActive = view.active,
                value = view.value,
                default = definition.default,
                options = definition.options,
                min = definition.min,
                max = definition.max,
                since = definition.since,
            )
        }
    }
}
