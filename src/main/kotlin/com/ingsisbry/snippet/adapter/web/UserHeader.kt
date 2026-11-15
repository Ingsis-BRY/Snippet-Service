package com.ingsisbry.snippet.adapter.web

/** Temporary identity of the caller until Auth0 is integrated; it will come from the JWT `sub` claim. */
object UserHeader {
    const val NAME = "X-User-Id"
}
