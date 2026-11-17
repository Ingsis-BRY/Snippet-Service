package com.ingsisbry.snippet.adapter.web.dto.response

import com.ingsisbry.snippet.application.port.out.language.LanguageInfo

/** [extension] is the first of [extensions], the one new files get. */
data class LanguageResponse(
    val language: String,
    val extension: String,
    val extensions: List<String>,
    val versions: List<String>,
    val defaultVersion: String,
) {
    companion object {
        fun from(info: LanguageInfo): LanguageResponse =
            LanguageResponse(
                language = info.language,
                extension = info.extensions.firstOrNull().orEmpty(),
                extensions = info.extensions,
                versions = info.versions,
                defaultVersion = info.defaultVersion,
            )
    }
}
