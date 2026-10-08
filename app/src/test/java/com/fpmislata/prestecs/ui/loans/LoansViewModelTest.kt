package com.fpmislata.prestecs.ui.loans

import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.data.api.dto.Estat
import com.fpmislata.prestecs.data.api.dto.PaginationDto
import com.fpmislata.prestecs.data.api.dto.PrestecDto
import com.fpmislata.prestecs.data.api.dto.PrestecsPageDto
import com.fpmislata.prestecs.testing.FakePrestecsRepository
import com.fpmislata.prestecs.testing.FakePrestecsRepository.ListCall
import com.fpmislata.prestecs.testing.MainDispatcherRule
import com.fpmislata.prestecs.testing.page
import com.fpmislata.prestecs.testing.prestec
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoansViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakePrestecsRepository()
    private val viewModel = LoansViewModel(repository).apply {
        clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneId.of("Europe/Madrid"))
    }
    private val state get() = viewModel.state.value

    private fun loan(id: Long, carro: String, estat: Estat = Estat.PRESTAT, date: String = "2026-10-07 08:00:00") =
        prestec(id, estat).copy(carro = carro, portatil = "$carro - P%02d".format(id), prestecData = date)

    private fun serve(vararg loans: PrestecDto) {
        repository.onList = {
            ApiResult.Success(
                PrestecsPageDto(
                    loans.toList(),
                    PaginationDto(page = 1, perPage = 200, total = loans.size, totalPages = 1),
                ),
            )
        }
    }

    @Test
    fun `refresh groups the open loans by carro`() {
        serve(loan(1, "C2"), loan(2, "C1"), loan(3, "C1", Estat.NO_RETORNAT, date = "2026-10-05 08:00:00"))

        viewModel.refresh()

        assertEquals(listOf("C1", "C2"), state.groups.map { it.carro })
        assertEquals(2, state.activeCount)
        assertEquals(1, state.overdueCount)
        assertTrue(state.isLoaded)
        assertFalse(state.isRefreshing)
        assertNull(state.error)
    }

    @Test
    fun `only prestat and no-retornat are requested, all pages`() {
        repository.onList = { call ->
            val ids = if (call.page == 1) listOf(1L, 2L) else listOf(3L)
            page(ids, page = call.page, totalPages = 2, total = 3)
        }

        viewModel.refresh()

        assertEquals(
            listOf(
                ListCall(setOf(Estat.PRESTAT, Estat.NO_RETORNAT), 1),
                ListCall(setOf(Estat.PRESTAT, Estat.NO_RETORNAT), 2),
            ),
            repository.listCalls,
        )
        assertEquals(3, state.groups.sumOf { it.loans.size })
    }

    @Test
    fun `a failed page fails the refresh`() {
        repository.onList = { call ->
            if (call.page ==
                1
            ) {
                page(listOf(1), page = 1, totalPages = 2, total = 2)
            } else {
                ApiResult.Failure(ApiError.Network)
            }
        }

        viewModel.refresh()

        assertEquals(ApiError.Network, state.error)
        assertFalse(state.isRefreshing)
        assertFalse(state.isLoaded)
    }

    @Test
    fun `a failed refresh keeps what was shown`() {
        serve(loan(1, "C1"))
        viewModel.refresh()
        repository.onList = { ApiResult.Failure(ApiError.Network) }

        viewModel.refresh()

        assertEquals(ApiError.Network, state.error)
        assertEquals(listOf("C1"), state.groups.map { it.carro })
    }

    @Test
    fun `search and the overdue toggle narrow the cards`() {
        serve(loan(1, "C1"), loan(2, "C2", Estat.NO_RETORNAT, date = "2026-10-05 08:00:00"))
        viewModel.refresh()

        viewModel.onQueryChange("c1")
        assertEquals(listOf("C1"), state.visibleGroups.map { it.carro })
        assertTrue(state.isFiltering)

        viewModel.onQueryChange("")
        viewModel.toggleOnlyOverdue()
        assertEquals(listOf("C2"), state.visibleGroups.map { it.carro })

        viewModel.toggleOnlyOverdue()
        assertEquals(listOf("C1", "C2"), state.visibleGroups.map { it.carro })
        assertFalse(state.isFiltering)
        // The totals don't change with the filter.
        assertEquals(1, state.overdueCount)
    }

    @Test
    fun `age counts from today in Madrid`() {
        serve(loan(1, "C1", Estat.NO_RETORNAT, date = "2026-10-05 23:30:00"))

        viewModel.refresh()

        assertEquals(2, state.groups.single().loans.single().ageDays)
    }
}
