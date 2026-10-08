package com.fpmislata.prestecs.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.fpmislata.prestecs.domain.CarroGroup
import com.fpmislata.prestecs.domain.OpenLoan
import com.fpmislata.prestecs.domain.groupByCarro
import com.fpmislata.prestecs.ui.components.CenteredText
import com.fpmislata.prestecs.ui.components.ErrorMessage
import com.fpmislata.prestecs.ui.components.formatApiDate
import com.fpmislata.prestecs.ui.components.label
import com.fpmislata.prestecs.ui.theme.PrestecsTheme
import java.time.LocalDate

/** Rows a card shows before folding the rest behind "show more". */
private const val VISIBLE_ROWS = 5

@Composable
fun LoansScreen(
    environment: Environment,
    onLogOut: () -> Unit,
    onNewLoan: () -> Unit,
    onNewReturn: () -> Unit,
    onHistory: () -> Unit,
    snackbarMessage: String? = null,
    onSnackbarShown: () -> Unit = {},
    viewModel: LoansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Clear it first so it isn't shown again after a rotation.
    LaunchedEffect(snackbarMessage) {
        if (!snackbarMessage.isNullOrEmpty()) {
            onSnackbarShown()
            snackbarHostState.showSnackbar(snackbarMessage)
        }
    }

    // Reload whenever the screen becomes visible: on first display, back from
    // a new loan or return, and when the app returns to the foreground.
    LifecycleStartEffect(viewModel) {
        viewModel.refresh()
        onStopOrDispose {}
    }

    LoansContent(
        state = state,
        environment = environment,
        onQueryChange = viewModel::onQueryChange,
        onToggleOverdue = viewModel::toggleOnlyOverdue,
        onRefresh = viewModel::refresh,
        onLogOut = onLogOut,
        onNewLoan = onNewLoan,
        onNewReturn = onNewReturn,
        onHistory = onHistory,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansContent(
    state: LoansUiState,
    environment: Environment,
    onQueryChange: (String) -> Unit,
    onToggleOverdue: () -> Unit,
    onRefresh: () -> Unit,
    onLogOut: () -> Unit,
    onNewLoan: () -> Unit,
    onNewReturn: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        // Make it obvious when not working on real data.
                        if (environment != Environment.PROD) {
                            Text(
                                environment.label(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                actions = { OverflowMenu(onLogOut = onLogOut) },
            )
        },
        bottomBar = { ActionsBar(onNewLoan = onNewLoan, onNewReturn = onNewReturn) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(query = state.query, onQueryChange = onQueryChange)
            StatsRow(state = state, onToggleOverdue = onToggleOverdue, onHistory = onHistory)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Cards(state = state, onRetry = onRefresh)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.loans_search_hint)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.loans_search_clear),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsRow(state: LoansUiState, onToggleOverdue: () -> Unit, onHistory: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.loans_stat_active, state.activeCount),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Tapping it narrows the cards to what wasn't returned, like the web.
        FilterChip(
            selected = state.onlyOverdue,
            onClick = onToggleOverdue,
            label = {
                Text(
                    stringResource(R.string.loans_stat_overdue, state.overdueCount),
                    color = if (state.overdueCount > 0) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            },
        )
        OutlinedButton(onClick = onHistory) { Text(stringResource(R.string.loans_history)) }
    }
}

@Composable
private fun Cards(state: LoansUiState, onRetry: () -> Unit) {
    val groups = state.visibleGroups
    // One column on a phone, more on a tablet. Always scrollable, even when
    // empty, so pull to refresh works.
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
    ) {
        if (state.error != null) {
            item(key = "error", span = StaggeredGridItemSpan.FullLine) {
                ErrorMessage(error = state.error, onRetry = onRetry)
            }
        } else if (groups.isEmpty() && state.isLoaded) {
            item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                CenteredText(
                    stringResource(if (state.isFiltering) R.string.loans_no_results else R.string.loans_none_active),
                )
            }
        }

        items(groups, key = { it.carro }) { group ->
            CarroCard(group = group, foldRows = !state.isFiltering)
        }
    }
}

@Composable
private fun CarroCard(group: CarroGroup, foldRows: Boolean) {
    var expanded by rememberSaveable(group.carro) { mutableStateOf(false) }
    val folded = foldRows && !expanded
    val shown = if (folded) group.loans.take(VISIBLE_ROWS) else group.loans
    val hidden = group.loans.size - VISIBLE_ROWS

    OutlinedCard(Modifier.fillMaxWidth()) {
        CarroHeader(group)
        shown.forEach { loan ->
            HorizontalDivider()
            LoanRow(loan)
        }
        // Not while searching: every match is shown.
        if (foldRows && hidden > 0) {
            HorizontalDivider()
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    if (expanded) {
                        stringResource(R.string.loans_show_less)
                    } else {
                        stringResource(R.string.loans_show_more, hidden)
                    },
                )
            }
        }
    }
}

@Composable
private fun CarroHeader(group: CarroGroup) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            group.carro.ifEmpty { stringResource(R.string.loans_no_carro) },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        if (group.overdue > 0) {
            Text(
                pluralStringResource(R.plurals.loans_carro_overdue, group.overdue, group.overdue),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (group.overdue > 0 && group.active > 0) {
            Text("·", style = MaterialTheme.typography.labelMedium, color = muted)
        }
        if (group.active > 0) {
            Text(
                pluralStringResource(R.plurals.loans_carro_active, group.active, group.active),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
        }
    }
}

@Composable
private fun LoanRow(loan: OpenLoan) {
    val prestec = loan.prestec
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(prestec.portatil, fontWeight = FontWeight.Bold)
            Text(
                prestec.estudiant,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            prestec.prestecProfessor?.takeIf { it.isNotBlank() }?.let {
                Text(
                    stringResource(R.string.loans_professor, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            // Red for anything older than today: it is overdue.
            Text(
                if (loan.ageDays <= 0) {
                    stringResource(R.string.loans_age_today)
                } else {
                    pluralStringResource(R.plurals.loans_age_days, loan.ageDays, loan.ageDays)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (loan.ageDays > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (loan.ageDays > 0) MaterialTheme.colorScheme.error else muted,
            )
            Text(formatApiDate(prestec.prestecData), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@Composable
private fun ActionsBar(onNewLoan: () -> Unit, onNewReturn: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onNewLoan, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.new_loan))
            }
            FilledTonalButton(onClick = onNewReturn, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.new_return))
            }
        }
    }
}

@Composable
private fun OverflowMenu(onLogOut: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.action_more),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_log_out)) },
            onClick = {
                expanded = false
                onLogOut()
            },
        )
    }
}

private fun previewGroups() = groupByCarro(
    listOf(
        PrestecDto(
            1,
            "C1",
            "C1 - P03",
            "12345678 - Garcia, Maria",
            "2026-10-07 08:15:00",
            "Joan",
            estat = Estat.PRESTAT,
        ),
        PrestecDto(
            2,
            "C1",
            "C1 - P02",
            "87654321 - Pérez, Joan",
            "2026-10-05 09:00:00",
            "Anna",
            estat = Estat.NO_RETORNAT,
        ),
        PrestecDto(
            3,
            "C2",
            "C2 - P11",
            "11223344 - Soler, Anna",
            "2026-10-06 08:00:00",
            null,
            estat = Estat.NO_RETORNAT,
        ),
    ),
    today = LocalDate.of(2026, 10, 7),
)

@Preview(showBackground = true)
@Composable
private fun LoansPreview() {
    PrestecsTheme {
        LoansContent(
            state = LoansUiState(groups = previewGroups(), isLoaded = true),
            environment = Environment.STAGING,
            onQueryChange = {},
            onToggleOverdue = {},
            onRefresh = {},
            onLogOut = {},
            onNewLoan = {},
            onNewReturn = {},
            onHistory = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoansErrorPreview() {
    PrestecsTheme {
        LoansContent(
            state = LoansUiState(error = ApiError.Server("abc123")),
            environment = Environment.PROD,
            onQueryChange = {},
            onToggleOverdue = {},
            onRefresh = {},
            onLogOut = {},
            onNewLoan = {},
            onNewReturn = {},
            onHistory = {},
        )
    }
}
