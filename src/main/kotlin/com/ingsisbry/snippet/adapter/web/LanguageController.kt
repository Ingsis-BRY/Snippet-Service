package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.dto.response.LanguageResponse
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.query.language.ListLanguages
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("!worker")
@RequestMapping("/languages")
class LanguageController(
    private val listLanguages: ListLanguages,
    private val errorMapper: SnippetErrorMapper,
) {
    @GetMapping
    fun list(): ResponseEntity<*> =
        listLanguages
            .execute()
            .fold(
                onSuccess = { languages -> ResponseEntity.ok(languages.map(LanguageResponse::from)) },
                onFailure = errorMapper::toResponse,
            )
}
