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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * [PrestecsRepository] over the JSON API, for the signed-in user. A 401 means
 * the token expired or was revoked: the session is cleared, which sends the
 * app back to login.
 */
@Singleton
class ApiPrestecsRepository @Inject constructor(
    private val apiClients: ApiClients,
    private val sessionRepository: SessionRepository,
    private val json: Json,
) : PrestecsRepository {
    override suspend fun list(estats: Set<Estat>, page: Int, perPage: Int): ApiResult<PrestecsPageDto> {
        val estat = estats.takeIf { it.isNotEmpty() }
            ?.sortedBy { it.ordinal }
            ?.joinToString(",") { it.apiValue }
        return call { list(estat, page, perPage) }
    }

    override suspend fun create(rows: List<PrestecRowDto>): ApiResult<CreatePrestecsResponse> {
        val max = PrestecsRepository.MAX_BATCH_SIZE
        require(rows.size <= max) { "At most $max rows per batch" }
        return call(CreatePrestecsResponse.serializer()) { create(CreatePrestecsRequest(rows)) }
    }

    override suspend fun registerReturns(portatils: List<String>): ApiResult<ReturnsResponse> {
        val max = PrestecsRepository.MAX_BATCH_SIZE
        require(portatils.size <= max) { "At most $max laptops per batch" }
        return call(ReturnsResponse.serializer()) { registerReturns(ReturnsRequest(portatils)) }
    }

    override suspend fun lookup(portatil: String): ApiResult<LookupDto> = call { lookup(portatil) }

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
}
