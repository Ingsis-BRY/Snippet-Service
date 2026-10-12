package com.ingsisbry.snippet.adapter.web.dto.common.pagination

import com.ingsisbry.snippet.application.common.pagination.PageResult

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T> from(result: PageResult<T>): PageResponse<T> =
            PageResponse(
                content = result.content,
                page = result.page,
                size = result.size,
                totalElements = result.totalElements,
                totalPages = result.totalPages,
            )
    }
}
