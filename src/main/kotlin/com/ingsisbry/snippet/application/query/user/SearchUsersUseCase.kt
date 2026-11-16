package com.ingsisbry.snippet.application.query.user

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.user.UserDirectory
import org.springframework.stereotype.Service

@Service
class SearchUsersUseCase(
    private val userDirectory: UserDirectory,
) : SearchUsers {
    override fun execute(query: SearchUsersQuery): Result<SnippetError, UserPage> {
        val text = query.name.trim()
        val violation =
            when {
                query.page < 0 -> "Page must be greater than or equal to 0"
                query.size !in 1..MAXIMUM_SIZE -> "Size must be between 1 and $MAXIMUM_SIZE"
                text.length > MAXIMUM_NAME_LENGTH -> "Name must be at most $MAXIMUM_NAME_LENGTH characters"
                else -> null
            }

        return if (violation != null) {
            Result.Failure(InvalidInput(violation))
        } else {
            userDirectory.search(
                text = text,
                excludedUserId = query.requesterId,
                page = query.page,
                size = query.size,
            )
        }
    }

    private companion object {
        const val MAXIMUM_SIZE = 50
        const val MAXIMUM_NAME_LENGTH = 100
    }
}
