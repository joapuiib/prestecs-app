package com.fpmislata.prestecs.data.prestecs

import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.data.api.dto.CreatePrestecsResponse
import com.fpmislata.prestecs.data.api.dto.Estat
import com.fpmislata.prestecs.data.api.dto.LookupDto
import com.fpmislata.prestecs.data.api.dto.PrestecRowDto
import com.fpmislata.prestecs.data.api.dto.PrestecsPageDto
import com.fpmislata.prestecs.data.api.dto.ReturnsResponse

/** Loans of the signed-in user's backend. */
interface PrestecsRepository {
    /** Newest first. An empty [estats] means all states. [page] starts at 1. */
    suspend fun list(estats: Set<Estat>, page: Int, perPage: Int = DEFAULT_PAGE_SIZE): ApiResult<PrestecsPageDto>

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
    }
}
