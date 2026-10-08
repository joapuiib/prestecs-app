package com.fpmislata.prestecs.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.data.api.dto.Estat
import com.fpmislata.prestecs.data.api.dto.PrestecDto
import com.fpmislata.prestecs.ui.components.CenteredText
import com.fpmislata.prestecs.ui.components.ErrorMessage
import com.fpmislata.prestecs.ui.components.EstatBadge
import com.fpmislata.prestecs.ui.components.formatApiDate
import com.fpmislata.prestecs.ui.components.label
import com.fpmislata.prestecs.ui.components.message
import com.fpmislata.prestecs.ui.theme.PrestecsTheme

@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reload whenever the screen becomes visible, including when the app
    // returns to the foreground.
    LifecycleStartEffect(viewModel) {
        viewModel.refresh()
        onStopOrDispose {}
    }

    HistoryContent(
        state = state,
        onFilterChange = viewModel::onFilterChange,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onRetryLoadMore = viewModel::retryLoadMore,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    state: HistoryUiState,
    onFilterChange: (Estat, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterRow(filter = state.filter, total = state.total, onFilterChange = onFilterChange)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                HistoryList(state, onRefresh, onLoadMore, onRetryLoadMore)
            }
        }
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
) {
    // The list stays scrollable even when empty, so pull to refresh works.
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (state.error != null) {
            item(key = "error") {
                ErrorMessage(error = state.error, onRetry = onRetry)
            }
        } else if (state.prestecs.isEmpty() && state.total != null) {
            item(key = "empty") {
                CenteredText(
                    stringResource(
                        if (state.filter.isEmpty()) R.string.loans_empty else R.string.loans_empty_filtered,
                    ),
                )
            }
        }

        items(state.prestecs, key = { it.id }) { prestec ->
            LoanItem(prestec)
            HorizontalDivider()
        }

        when {
            state.loadMoreError != null -> item(key = "load-more-error") {
                ErrorMessage(error = state.loadMoreError, onRetry = onRetryLoadMore)
            }

            state.hasMore -> item(key = "load-more") {
                // Composed when scrolled into view: ask for the next page.
                // Keyed by size so a page that still leaves it visible
                // triggers the next one.
                LaunchedEffect(state.prestecs.size) { onLoadMore() }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun LoanItem(prestec: PrestecDto) {
    ListItem(
        headlineContent = { Text(prestec.portatil, fontWeight = FontWeight.Bold) },
        supportingContent = {
            Column {
                Text(prestec.estudiant)
                val returned = prestec.devolucioData
                Text(
                    if (returned != null) {
                        stringResource(R.string.loans_returned_at, formatApiDate(returned))
                    } else {
                        stringResource(R.string.loans_lent_at, formatApiDate(prestec.prestecData))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = { EstatBadge(prestec.estat) },
    )
}

@Composable
private fun FilterRow(filter: Set<Estat>, total: Int?, onFilterChange: (Estat, Boolean) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Estat.entries.forEach { estat ->
                val selected = estat in filter
                FilterChip(
                    selected = selected,
                    onClick = { onFilterChange(estat, !selected) },
                    label = { Text(estat.label()) },
                )
            }
        }
        Text(
            text = total?.let { pluralStringResource(R.plurals.loans_count, it, it) } ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

private val previewPrestecs = listOf(
    PrestecDto(
        id = 3,
        carro = "C1",
        portatil = "C1 - P03",
        estudiant = "12345678 - Garcia, Maria",
        prestecData = "2026-10-07 08:15:00",
        estat = Estat.PRESTAT,
    ),
    PrestecDto(
        id = 2,
        carro = "C1",
        portatil = "C1 - P02",
        estudiant = "87654321 - Pérez, Joan",
        prestecData = "2026-10-06 09:00:00",
        estat = Estat.NO_RETORNAT,
    ),
    PrestecDto(
        id = 1,
        carro = "C2",
        portatil = "C2 - P11",
        estudiant = "11223344 - Soler, Anna",
        prestecData = "2026-10-06 08:00:00",
        devolucioData = "2026-10-06 14:00:00",
        estat = Estat.RETORNAT,
    ),
)

@Preview(showBackground = true)
@Composable
private fun HistoryPreview() {
    PrestecsTheme {
        HistoryContent(
            state = HistoryUiState(prestecs = previewPrestecs, total = 3),
            onFilterChange = { _, _ -> },
            onRefresh = {},
            onLoadMore = {},
            onRetryLoadMore = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryErrorPreview() {
    PrestecsTheme {
        HistoryContent(
            state = HistoryUiState(filter = setOf(Estat.PRESTAT), error = ApiError.Server("abc123")),
            onFilterChange = { _, _ -> },
            onRefresh = {},
            onLoadMore = {},
            onRetryLoadMore = {},
            onBack = {},
        )
    }
}
