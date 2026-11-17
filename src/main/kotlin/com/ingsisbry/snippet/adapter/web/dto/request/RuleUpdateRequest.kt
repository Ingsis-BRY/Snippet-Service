package com.ingsisbry.snippet.adapter.web.dto.request

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/** One rule of `PUT /rules/{kind}`; the other fields of a `Rule` are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RuleUpdateRequest(
    val id: String,
    @param:JsonProperty("isActive")
    val isActive: Boolean = false,
    val value: Any? = null,
)
