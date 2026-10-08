package com.fpmislata.prestecs.testing

import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.data.api.dto.CreatePrestecsResponse
import com.fpmislata.prestecs.data.api.dto.Estat
import com.fpmislata.prestecs.data.api.dto.LookupDto
import com.fpmislata.prestecs.data.api.dto.PaginationDto
import com.fpmislata.prestecs.data.api.dto.PrestecDto
import com.fpmislata.prestecs.data.api.dto.PrestecRowDto
import com.fpmislata.prestecs.data.api.dto.PrestecsPageDto
import com.fpmislata.prestecs.data.api.dto.ReturnsResponse
import com.fpmislata.prestecs.data.prestecs.PrestecsRepository

/** In-memory [PrestecsRepository]. Set the handlers each test needs. */
class FakePrestecsRepository : PrestecsRepository {
    data class ListCall(val estats: Set<Estat>, val page: Int)

    val listCalls = mutableListOf<ListCall>()

    var onList: suspend (ListCall) -> ApiResult<PrestecsPageDto> = { error("list not expected") }
    var onCreate: suspend (List<PrestecRowDto>) -> ApiResult<CreatePrestecsResponse> = { error("create not expected") }
    var onReturns: suspend (List<String>) -> ApiResult<ReturnsResponse> = { error("returns not expected") }
    var onLookup: suspend (String) -> ApiResult<LookupDto> = { error("lookup not expected") }

    override suspend fun list(estats: Set<Estat>, page: Int, perPage: Int): ApiResult<PrestecsPageDto> {
        val call = ListCall(estats, page)
        listCalls += call
        return onList(call)
    }

    override suspend fun create(rows: List<PrestecRowDto>) = onCreate(rows)

    override suspend fun registerReturns(portatils: List<String>) = onReturns(portatils)

    override suspend fun lookup(portatil: String) = onLookup(portatil)
}

fun prestec(id: Long, estat: Estat = Estat.PRESTAT) = PrestecDto(
    id = id,
    carro = "C1",
    portatil = "C1 - P%02d".format(id),
    estudiant = "Estudiant $id",
    prestecData = "2026-10-07 08:00:00",
    estat = estat,
)

/** A page of [ids] out of [total] loans in [totalPages] pages. */
fun page(ids: List<Long>, page: Int, totalPages: Int, total: Int = ids.size) = ApiResult.Success(
    PrestecsPageDto(
        prestecs = ids.map { prestec(it) },
        pagination = PaginationDto(page = page, perPage = 50, total = total, totalPages = totalPages),
    ),
)
