package com.ingsisbry.snippet.adapter.web.dto.response

import com.ingsisbry.snippet.application.query.user.UserPage

data class UserPageResponse(
    val users: List<UserResponse>,
    val page: Int,
    val size: Int,
    val total: Long,
) {
    data class UserResponse(
        val id: String,
        val name: String,
    )

    companion object {
        fun from(page: UserPage): UserPageResponse =
            UserPageResponse(
                users = page.users.map { UserResponse(id = it.id, name = it.name) },
                page = page.page,
                size = page.size,
                total = page.total,
            )
    }
}
