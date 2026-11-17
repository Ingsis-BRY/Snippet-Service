package com.ingsisbry.snippet.adapter.web.dto.request

/** [input] and [output] may be omitted: a test without inputs that prints nothing. */
data class TestCaseRequest(
    val name: String,
    val input: List<String>? = null,
    val output: List<String>? = null,
)
