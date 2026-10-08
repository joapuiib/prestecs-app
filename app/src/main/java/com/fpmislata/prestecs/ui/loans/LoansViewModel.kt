package com.fpmislata.prestecs.ui.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.data.prestecs.PrestecsRepository
import com.fpmislata.prestecs.domain.CarroGroup
import com.fpmislata.prestecs.domain.filterGroups
import com.fpmislata.prestecs.domain.groupByCarro
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoansUiState(
    /** Every open loan, by carro. */
    val groups: List<CarroGroup> = emptyList(),
    val query: String = "",
    val onlyOverdue: Boolean = false,
    /** Loading, initially or on pull to refresh. */
    val isRefreshing: Boolean = false,
    /** Whether a load has finished at least once, so "empty" isn't shown too early. */
    val isLoaded: Boolean = false,
    val error: ApiError? = null,
) {
    /** [groups] narrowed by the search and the overdue toggle. */
    val visibleGroups: List<CarroGroup> get() = filterGroups(groups, query, onlyOverdue)

    /** Lent today. */
    val activeCount: Int get() = groups.sumOf { it.active }

    /** Lent on a previous day and not returned. */
    val overdueCount: Int get() = groups.sumOf { it.overdue }

    /** Folded rows are shown in full while searching, like the web. */
    val isFiltering: Boolean get() = query.isNotBlank() || onlyOverdue
}

/**
 * Home screen: open loans grouped by carro, as on the web. All of them are
 * loaded at once (they are few) and searched and filtered here.
 */
@HiltViewModel
class LoansViewModel @Inject constructor(private val repository: PrestecsRepository) : ViewModel() {

    /** Defines "today" for the loans' age: the web uses Madrid time. */
    internal var clock: Clock = Clock.system(ZoneId.of("Europe/Madrid"))

    private val _state = MutableStateFlow(LoansUiState())
    val state: StateFlow<LoansUiState> = _state.asStateFlow()

    // A refresh replaces the one in flight.
    private var loadJob: Job? = null

    /** Reloads. The screen calls it whenever it is shown. */
    fun refresh() {
        _state.update { it.copy(isRefreshing = true, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = repository.listOpen()
            _state.update { current ->
                when (result) {
                    is ApiResult.Success -> current.copy(
                        groups = groupByCarro(result.value, LocalDate.now(clock)),
                        isRefreshing = false,
                        isLoaded = true,
                    )

                    is ApiResult.Failure -> current.copy(isRefreshing = false, error = result.error)
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun toggleOnlyOverdue() {
        _state.update { it.copy(onlyOverdue = !it.onlyOverdue) }
    }
}
