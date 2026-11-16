package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.security.CurrentUserId
import com.ingsisbry.snippet.adapter.web.dto.response.UserPageResponse
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.query.user.SearchUsers
import com.ingsisbry.snippet.application.query.user.SearchUsersQuery
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserController(
    private val searchUsers: SearchUsers,
    private val errorMapper: SnippetErrorMapper,
) {
    @GetMapping
    fun search(
        @CurrentUserId userId: String,
        @RequestParam(defaultValue = "") name: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<*> =
        searchUsers
            .execute(SearchUsersQuery(requesterId = userId, name = name, page = page, size = size))
            .fold(
                onSuccess = { ResponseEntity.ok(UserPageResponse.from(it)) },
                onFailure = errorMapper::toResponse,
            )
}
