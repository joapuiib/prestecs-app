package com.fpmislata.prestecs.core.network

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    /** No connection, timeout, DNS failure... */
    data object Network : ApiError

    /** Token missing, expired or revoked (401). The session is cleared. */
    data object Unauthorized : ApiError

    /** The user can't perform this action (403). */
    data object Forbidden : ApiError

    /** Malformed request (400). [message] is English, for debugging only. */
    data class BadRequest(val message: String?) : ApiError

    /** Unexpected server error (5xx). Show [errorId] so it can be reported. */
    data class Server(val errorId: String?) : ApiError

    /** Any other status, or a body that couldn't be parsed. */
    data class Unexpected(val status: Int?, val message: String?) : ApiError
}
