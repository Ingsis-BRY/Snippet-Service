package com.ingsisbry.snippet.adapter.auth0

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Auth0UserQueryTest {
    private val me = "auth0|me"

    @Test
    fun `blank text lists everyone but the requester`() {
        assertEquals("NOT user_id:\"auth0|me\"", Auth0UserQuery.build("  ", me))
    }

    @Test
    fun `short text is matched as a prefix`() {
        assertEquals(
            "(name:us* OR email:us*) AND NOT user_id:\"auth0|me\"",
            Auth0UserQuery.build("us", me),
        )
    }

    @Test
    fun `text of three characters or more is matched anywhere`() {
        assertEquals(
            "(name:*dos* OR email:*dos*) AND NOT user_id:\"auth0|me\"",
            Auth0UserQuery.build("dos", me),
        )
    }

    @Test
    fun `special characters of the text are escaped`() {
        assertEquals(
            "(name:*a\\ b\\@c\\.d\\:\\)* OR email:*a\\ b\\@c\\.d\\:\\)*) AND NOT user_id:\"auth0|me\"",
            Auth0UserQuery.build("a b@c.d:)", me),
        )
    }

    @Test
    fun `quotes in the excluded id can't close the phrase`() {
        assertEquals("NOT user_id:\"x\\\" OR *\"", Auth0UserQuery.build("", "x\" OR *"))
    }
}
