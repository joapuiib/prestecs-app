package com.fpmislata.prestecs.ui.loans

import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.data.api.dto.Estat
import com.fpmislata.prestecs.data.api.dto.PrestecsPageDto
import com.fpmislata.prestecs.testing.FakePrestecsRepository
import com.fpmislata.prestecs.testing.FakePrestecsRepository.ListCall
import com.fpmislata.prestecs.testing.MainDispatcherRule
import com.fpmislata.prestecs.testing.page
import kotlinx.coroutines.CompletableDeferred
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
    private val viewModel = LoansViewModel(repository)

    private val ids get() = viewModel.state.value.prestecs.map { it.id }

    @Test
    fun `refresh loads the first page`() {
        repository.onList = { page(listOf(3, 2), page = 1, totalPages = 2, total = 4) }

        viewModel.refresh()

        with(viewModel.state.value) {
            assertEquals(listOf(3L, 2L), ids)
            assertEquals(4, total)
            assertTrue(hasMore)
            assertFalse(isRefreshing)
            assertNull(error)
        }
        assertEquals(listOf(ListCall(emptySet(), 1)), repository.listCalls)
    }

    @Test
    fun `load more appends pages until the last one`() {
        repository.onList = { call ->
            when (call.page) {
                1 -> page(listOf(4, 3), page = 1, totalPages = 2, total = 4)
                else -> page(listOf(2, 1), page = 2, totalPages = 2, total = 4)
            }
        }
        viewModel.refresh()

        viewModel.loadMore()

        assertEquals(listOf(4L, 3L, 2L, 1L), ids)
        assertFalse(viewModel.state.value.hasMore)

        viewModel.loadMore()
        assertEquals(2, repository.listCalls.size)
    }

    @Test
    fun `items repeated by a shifted page are shown once`() {
        repository.onList = { call ->
            when (call.page) {
                1 -> page(listOf(4, 3), page = 1, totalPages = 3, total = 5)
                // A new loan was created meanwhile: 3 moved to page 2.
                else -> page(listOf(3, 2), page = 2, totalPages = 3, total = 5)
            }
        }
        viewModel.refresh()

        viewModel.loadMore()

        assertEquals(listOf(4L, 3L, 2L), ids)
    }

    @Test
    fun `changing the filter reloads from page 1 with that filter`() {
        repository.onList = { page(listOf(1), page = 1, totalPages = 1) }
        viewModel.refresh()

        viewModel.onFilterChange(Estat.NO_RETORNAT, selected = true)
        viewModel.onFilterChange(Estat.PRESTAT, selected = true)
        viewModel.onFilterChange(Estat.NO_RETORNAT, selected = false)

        assertEquals(ListCall(setOf(Estat.PRESTAT), 1), repository.listCalls.last())
        assertEquals(setOf(Estat.PRESTAT), viewModel.state.value.filter)
    }

    @Test
    fun `a slow page for the old filter is discarded`() {
        val slow = CompletableDeferred<ApiResult<PrestecsPageDto>>()
        repository.onList = { call -> if (call.estats.isEmpty()) slow.await() else page(listOf(7), 1, 1) }
        viewModel.refresh()

        viewModel.onFilterChange(Estat.PRESTAT, selected = true)
        slow.complete(page(listOf(1, 2, 3), page = 1, totalPages = 1))

        assertEquals(listOf(7L), ids)
    }

    @Test
    fun `first page error is shown and refresh recovers`() {
        repository.onList = { ApiResult.Failure(ApiError.Network) }
        viewModel.refresh()

        assertEquals(ApiError.Network, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isRefreshing)

        repository.onList = { page(listOf(1), page = 1, totalPages = 1) }
        viewModel.refresh()

        assertNull(viewModel.state.value.error)
        assertEquals(listOf(1L), ids)
    }

    @Test
    fun `load more error stops loading until retried`() {
        repository.onList = { call ->
            if (call.page == 1) page(listOf(2), page = 1, totalPages = 2) else ApiResult.Failure(ApiError.Server("x"))
        }
        viewModel.refresh()

        viewModel.loadMore()
        assertEquals(ApiError.Server("x"), viewModel.state.value.loadMoreError)
        assertEquals(listOf(2L), ids)

        viewModel.loadMore()
        assertEquals(2, repository.listCalls.size)

        repository.onList = { page(listOf(1), page = 2, totalPages = 2) }
        viewModel.retryLoadMore()

        assertNull(viewModel.state.value.loadMoreError)
        assertEquals(listOf(2L, 1L), ids)
    }

    @Test
    fun `refresh after loading more starts over`() {
        repository.onList = { call -> page(listOf(10L - call.page), page = call.page, totalPages = 3) }
        viewModel.refresh()
        viewModel.loadMore()

        viewModel.refresh()
        viewModel.loadMore()

        assertEquals(listOf(9L, 8L), ids)
        assertEquals(listOf(1, 2, 1, 2), repository.listCalls.map { it.page })
    }
}
