package com.ingsisbry.snippet.adapter.web.dto.response

/** A status as the API writes it: the enum name lower-cased, `_` as `-` (`NOT_COMPLIANT` is `not-compliant`). */
fun Enum<*>.toJsonValue(): String = name.lowercase().replace('_', '-')
