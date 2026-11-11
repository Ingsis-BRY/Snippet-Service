package com.ingsisbry.snippet.adapter.persistence.query.filter

import com.ingsisbry.snippet.adapter.persistence.query.filter.parameters.LanguageFilter
import com.ingsisbry.snippet.adapter.persistence.query.filter.parameters.NameFilter
import com.ingsisbry.snippet.adapter.persistence.query.filter.parameters.TextPredicateFactory
import com.ingsisbry.snippet.adapter.persistence.query.filter.parameters.ValidityFilter
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SnippetFilterRegistryTest {
    private val textPredicateFactory = TextPredicateFactory()
    private val registry =
        SnippetFilterRegistry(
            listOf(NameFilter(textPredicateFactory), LanguageFilter(textPredicateFactory), ValidityFilter()),
        )

    @Test
    fun `known filters with valid values pass validation`() {
        assertEquals(
            Result.Success(Unit),
            registry.validate(mapOf("name" to "foo", "language" to "Java", "valid" to "true")),
        )
    }

    @Test
    fun `an unknown filter is an invalid input`() {
        assertEquals(Result.Failure(InvalidInput("Unknown filter 'foo'")), registry.validate(mapOf("foo" to "bar")))
    }

    @Test
    fun `a filter can reject its own value`() {
        assertEquals(
            Result.Failure(InvalidInput("The valid filter must be either true or false")),
            registry.validate(mapOf("valid" to "maybe")),
        )
    }

    @Test
    fun `duplicate parameter names are rejected at startup`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnippetFilterRegistry(listOf(ValidityFilter(), ValidityFilter()))
        }
    }
}
