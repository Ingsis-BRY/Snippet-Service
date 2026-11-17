package com.ingsisbry.snippet.application.query.language

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.language.LanguageCatalog
import com.ingsisbry.snippet.application.port.out.language.LanguageInfo
import org.springframework.stereotype.Service

@Service
class ListLanguagesUseCase(
    private val catalog: LanguageCatalog,
) : ListLanguages {
    override fun execute(): Result<SnippetError, List<LanguageInfo>> = catalog.languages()
}
