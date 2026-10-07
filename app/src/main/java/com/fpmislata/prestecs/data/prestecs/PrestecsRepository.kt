package com.fpmislata.prestecs.data.prestecs

import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.core.network.apiCall
import com.fpmislata.prestecs.core.session.SessionRepository
import com.fpmislata.prestecs.data.api.ApiClients
import com.fpmislata.prestecs.data.api.PrestecsApi
import com.fpmislata.prestecs.data.api.dto.CreatePrestecsRequest
import com.fpmislata.prestecs.data.api.dto.CreatePrestecsResponse
import com.fpmislata.prestecs.data.api.dto.Estat
import com.fpmislata.prestecs.data.api.dto.LookupDto
import com.fpmislata.prestecs.data.api.dto.PrestecRowDto
import com.fpmislata.prestecs.data.api.dto.PrestecsPageDto
import com.fpmislata.prestecs.data.api.dto.ReturnsRequest
import com.fpmislata.prestecs.data.api.dto.ReturnsResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prestecs API calls for the signed-in user. A 401 means the token expired or
 * was revoked: the session is cleared, which sends the app back to login.
 */
@Singleton
class PrestecsRepository @Inject constructor(
    private val apiClients: ApiClients,
    private val sessionRepository: SessionRepository,
    private val json: Json,
) {
    /** Newest first. An empty [estats] means all states. */
    suspend fun list(estats: Set<Estat>, page: Int, perPage: Int = DEFAULT_PAGE_SIZE): ApiResult<PrestecsPageDto> {
        val estat = estats.takeIf { it.isNotEmpty() }
            ?.sortedBy { it.ordinal }
            ?.joinToString(",") { it.apiValue }
        return call { list(estat, page, perPage) }
    }

    suspend fun create(rows: List<PrestecRowDto>): ApiResult<CreatePrestecsResponse> {
        require(rows.size <= MAX_BATCH_SIZE) { "At most $MAX_BATCH_SIZE rows per batch" }
        return call(CreatePrestecsResponse.serializer()) { create(CreatePrestecsRequest(rows)) }
    }

    suspend fun registerReturns(portatils: List<String>): ApiResult<ReturnsResponse> {
        require(portatils.size <= MAX_BATCH_SIZE) { "At most $MAX_BATCH_SIZE laptops per batch" }
        return call(ReturnsResponse.serializer()) { registerReturns(ReturnsRequest(portatils)) }
    }

    suspend fun lookup(portatil: String): ApiResult<LookupDto> = call { lookup(portatil) }

    private suspend fun <T> call(
        bodyOn422: KSerializer<T>? = null,
        block: suspend PrestecsApi.() -> Response<T>,
    ): ApiResult<T> {
        val session = sessionRepository.currentSession()
            ?: return ApiResult.Failure(ApiError.Unauthorized)
        val api = apiClients.prestecs(session)
        val result = apiCall(json, bodyOn422) { api.block() }
        if (result is ApiResult.Failure && result.error == ApiError.Unauthorized) {
            sessionRepository.logOut()
        }
        return result
    }

    companion object {
        /** API limit for both loan and return batches. */
        const val MAX_BATCH_SIZE = 50
        const val DEFAULT_PAGE_SIZE = 50
    }
}
