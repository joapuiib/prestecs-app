package com.fpmislata.prestecs.data.prestecs

import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.data.api.dto.CreatePrestecsResponse
import com.fpmislata.prestecs.data.api.dto.Estat
import com.fpmislata.prestecs.data.api.dto.LookupDto
import com.fpmislata.prestecs.data.api.dto.PrestecDto
import com.fpmislata.prestecs.data.api.dto.PrestecRowDto
import com.fpmislata.prestecs.data.api.dto.PrestecsPageDto
import com.fpmislata.prestecs.data.api.dto.ReturnsResponse

/** Loans of the signed-in user's backend. */
interface PrestecsRepository {
    /** Newest first. An empty [estats] means all states. [page] starts at 1. */
    suspend fun list(estats: Set<Estat>, page: Int, perPage: Int = DEFAULT_PAGE_SIZE): ApiResult<PrestecsPageDto>

    /**
     * Every loan not yet returned, lent today or earlier, in one list: pages
     * of [OPEN_PAGE_SIZE] are fetched until the last one. Fails as a whole if
     * any page fails.
     */
    suspend fun listOpen(): ApiResult<List<PrestecDto>> {
        val open = setOf(Estat.PRESTAT, Estat.NO_RETORNAT)
        val loans = mutableListOf<PrestecDto>()
        var page = 1
        while (true) {
            when (val result = list(open, page, OPEN_PAGE_SIZE)) {
                is ApiResult.Failure -> return result

                is ApiResult.Success -> {
                    loans += result.value.prestecs
                    if (page >= result.value.pagination.totalPages) return ApiResult.Success(loans)
                    page++
                }
            }
        }
    }

    /** At most [MAX_BATCH_SIZE] rows. Some rows may fail: see the response messages. */
    suspend fun create(rows: List<PrestecRowDto>): ApiResult<CreatePrestecsResponse>

    /** At most [MAX_BATCH_SIZE] laptops. Some may fail: see the response messages. */
    suspend fun registerReturns(portatils: List<String>): ApiResult<ReturnsResponse>

    /** Whether [portatil] has an active loan, and to whom. */
    suspend fun lookup(portatil: String): ApiResult<LookupDto>

    companion object {
        /** API limit for both loan and return batches. */
        const val MAX_BATCH_SIZE = 50
        const val DEFAULT_PAGE_SIZE = 50

        /** API maximum, to load all open loans in few requests. */
        const val OPEN_PAGE_SIZE = 200
    }
}
