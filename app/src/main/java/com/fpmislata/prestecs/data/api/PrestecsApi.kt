package com.fpmislata.prestecs.data.api

import com.fpmislata.prestecs.data.api.dto.CreatePrestecsRequest
import com.fpmislata.prestecs.data.api.dto.CreatePrestecsResponse
import com.fpmislata.prestecs.data.api.dto.LookupDto
import com.fpmislata.prestecs.data.api.dto.PrestecsPageDto
import com.fpmislata.prestecs.data.api.dto.ReturnsRequest
import com.fpmislata.prestecs.data.api.dto.ReturnsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface PrestecsApi {
    /** [estat]: comma-separated `Estat.apiValue`s, or null for all. */
    @GET("prestecs.php")
    suspend fun list(
        @Query("estat") estat: String?,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
    ): Response<PrestecsPageDto>

    @POST("prestecs.php")
    suspend fun create(@Body body: CreatePrestecsRequest): Response<CreatePrestecsResponse>

    @POST("devolucions.php")
    suspend fun registerReturns(@Body body: ReturnsRequest): Response<ReturnsResponse>

    @GET("lookup.php")
    suspend fun lookup(@Query("portatil") portatil: String): Response<LookupDto>
}
