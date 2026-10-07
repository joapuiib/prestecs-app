package com.fpmislata.prestecs.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

@Serializable
private data class ErrorBody(
    val error: String? = null,
    @SerialName("error_id") val errorId: String? = null,
)

/**
 * Runs a Retrofit call and maps the outcome to [ApiResult].
 *
 * Batch endpoints answer 422 when some items failed, with the same body as a
 * 200; pass [bodyOn422] to decode it as a success.
 */
suspend fun <T> apiCall(
    json: Json,
    bodyOn422: KSerializer<T>? = null,
    call: suspend () -> Response<T>,
): ApiResult<T> {
    val response = try {
        call()
    } catch (e: IOException) {
        return ApiResult.Failure(ApiError.Network)
    } catch (e: SerializationException) {
        return ApiResult.Failure(ApiError.Unexpected(status = null, message = e.message))
    }

    val status = response.code()
    if (response.isSuccessful) {
        val body = response.body()
            ?: return ApiResult.Failure(ApiError.Unexpected(status, "Empty body"))
        return ApiResult.Success(body)
    }

    val errorText = try {
        response.errorBody()?.string().orEmpty()
    } catch (e: IOException) {
        return ApiResult.Failure(ApiError.Network)
    }

    if (status == 422 && bodyOn422 != null) {
        return try {
            ApiResult.Success(json.decodeFromString(bodyOn422, errorText))
        } catch (e: IllegalArgumentException) {
            // Includes SerializationException.
            ApiResult.Failure(ApiError.Unexpected(status, e.message))
        }
    }

    val error = try {
        json.decodeFromString(ErrorBody.serializer(), errorText)
    } catch (e: IllegalArgumentException) {
        // SerializationException is an IllegalArgumentException: not JSON
        // (e.g. an HTML error page from the web server).
        ErrorBody()
    }

    return ApiResult.Failure(
        when (status) {
            400 -> ApiError.BadRequest(error.error)
            401 -> ApiError.Unauthorized
            403 -> ApiError.Forbidden
            in 500..599 -> ApiError.Server(error.errorId)
            else -> ApiError.Unexpected(status, error.error)
        },
    )
}
