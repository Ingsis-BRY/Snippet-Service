package com.ingsisbry.snippet.adapter.run

/** The browser side of the interactive run (US#10): `wss://<host>/api/snippets/{id}/run`. */
object RunProtocol {
    /** The subprotocol the server selects; the browser offers it next to the token. */
    const val SUBPROTOCOL = "snippet-run.v1"

    /** The browser sends its access token as the subprotocol `bearer.<jwt>`, so it never appears in a URL. */
    const val BEARER_PREFIX = "bearer."

    const val PATH_PATTERN = "/snippets/*/run"

    val PATH_REGEX = Regex("^/snippets/([^/]+)/run$")
}
