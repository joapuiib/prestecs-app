package com.fpmislata.prestecs.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.data.api.dto.Estat
import com.fpmislata.prestecs.data.api.dto.PrestecDto
import com.fpmislata.prestecs.data.prestecs.PrestecsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    /** Empty means all states. */
    val filter: Set<Estat> = emptySet(),
    val prestecs: List<PrestecDto> = emptyList(),
    /** Loans matching [filter] on the server; null until the first page arrives. */
    val total: Int? = null,
    /** Loading the first page, initially or on pull to refresh. */
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** The first page failed. */
    val error: ApiError? = null,
    /** A later page failed; loading more stops until [HistoryViewModel.retryLoadMore]. */
    val loadMoreError: ApiError? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(private val repository: PrestecsRepository) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    // Only one load at a time: a refresh or filter change cancels the
    // previous one so a stale page never lands in the list.
    private var loadJob: Job? = null
    private var loadedPages = 0

    /** Reloads from the first page. The screen calls it whenever it is shown. */
    fun refresh() {
        _state.update { it.copy(isRefreshing = true, isLoadingMore = false, error = null, loadMoreError = null) }
        load(page = 1)
    }

    fun onFilterChange(estat: Estat, selected: Boolean) {
        _state.update {
            val filter = if (selected) it.filter + estat else it.filter - estat
            // Drop the old results: they don't match the new filter.
            it.copy(filter = filter, prestecs = emptyList(), total = null, hasMore = false)
        }
        refresh()
    }

    /** Called when the end of the list is reached. */
    fun loadMore() {
        val current = _state.value
        if (loadJob?.isActive == true || !current.hasMore || current.loadMoreError != null) return
        _state.update { it.copy(isLoadingMore = true) }
        load(page = loadedPages + 1)
    }

    fun retryLoadMore() {
        _state.update { it.copy(loadMoreError = null) }
        loadMore()
    }

    private fun load(page: Int) {
        loadJob?.cancel()
        val filter = _state.value.filter
        loadJob = viewModelScope.launch {
            val result = repository.list(filter, page)
            _state.update { current ->
                when (result) {
                    is ApiResult.Success -> {
                        val loaded = result.value
                        // Pages are offsets: loans created meanwhile shift them,
                        // so a later page can repeat items already shown.
                        val prestecs = if (page == 1) {
                            loaded.prestecs
                        } else {
                            (current.prestecs + loaded.prestecs).distinctBy { it.id }
                        }
                        current.copy(
                            prestecs = prestecs,
                            total = loaded.pagination.total,
                            hasMore = page < loaded.pagination.totalPages,
                            isRefreshing = false,
                            isLoadingMore = false,
                        )
                    }

                    is ApiResult.Failure -> if (page == 1) {
                        current.copy(isRefreshing = false, error = result.error)
                    } else {
                        current.copy(isLoadingMore = false, loadMoreError = result.error)
                    }
                }
            }
            if (result is ApiResult.Success) loadedPages = page
        }
    }
}
