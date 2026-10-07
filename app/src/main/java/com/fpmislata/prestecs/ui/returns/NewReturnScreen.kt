package com.fpmislata.prestecs.ui.returns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.data.prestecs.PrestecsRepository
import com.fpmislata.prestecs.ui.batch.ConfirmDialog
import com.fpmislata.prestecs.ui.batch.MessageLine
import com.fpmislata.prestecs.ui.batch.OutcomeCard
import com.fpmislata.prestecs.ui.batch.SaveBar
import com.fpmislata.prestecs.ui.batch.ScanEventsFeedback
import com.fpmislata.prestecs.ui.batch.ScrollToNewRow
import com.fpmislata.prestecs.ui.batch.flashColor
import com.fpmislata.prestecs.ui.components.formatApiDate
import com.fpmislata.prestecs.ui.components.message
import com.fpmislata.prestecs.ui.scanner.ScanPanel
import com.fpmislata.prestecs.ui.theme.PrestecsTheme
import com.fpmislata.prestecs.ui.theme.success

@Composable
fun NewReturnScreen(onBack: () -> Unit, viewModel: NewReturnViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScanEventsFeedback(viewModel.events)

    // Laptops restored after the process was killed need their lookups again.
    LaunchedEffect(viewModel) { viewModel.resumeLookups() }

    NewReturnContent(
        state = state,
        onCode = viewModel::onCode,
        onRemoveItem = viewModel::removeItem,
        onSubmit = viewModel::submit,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewReturnContent(
    state: NewReturnUiState,
    onCode: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmSubmit by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    // Only laptops still to be returned are worth a warning when leaving.
    val hasUnsaved = state.toReturn.isNotEmpty() || state.isLookingUp
    val leave: () -> Unit = { if (hasUnsaved) confirmDiscard = true else onBack() }
    BackHandler(enabled = hasUnsaved) { confirmDiscard = true }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_return)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            SaveBar(
                label = stringResource(R.string.return_save, state.toReturn.size),
                enabled = state.canSubmit,
                isSubmitting = state.isSubmitting,
                onSave = { confirmSubmit = true },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .fitInside(WindowInsetsRulers.Ime.current)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScanPanel(
                onCode = onCode,
                manualLabel = stringResource(R.string.loan_step_portatil),
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            MessageLine { state.message?.let { ScanMessageText(it) } }
            state.outcome?.let { outcome ->
                OutcomeCard(outcome, failedHint = stringResource(R.string.return_failed_hint))
            }
            ReturnList(state = state, onRemoveItem = onRemoveItem, modifier = Modifier.weight(1f))
        }
    }

    if (confirmSubmit) {
        val count = state.toReturn.size
        ConfirmDialog(
            title = stringResource(R.string.action_confirm),
            text = pluralStringResource(R.plurals.return_confirm_text, count, count),
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = {
                confirmSubmit = false
                onSubmit()
            },
            onDismiss = { confirmSubmit = false },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.return_discard_title),
            text = stringResource(R.string.return_discard_text),
            confirmLabel = stringResource(R.string.action_discard),
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun ScanMessageText(message: ReturnScanMessage) {
    val text = when (message) {
        is ReturnScanMessage.NotFound -> stringResource(R.string.no_active_loan, message.portatil)
        is ReturnScanMessage.LookupFailed -> "${message.portatil}: ${message.error.message()}"
        is ReturnScanMessage.AlreadyInBatch -> stringResource(R.string.scan_already_in_batch, message.portatil)
        ReturnScanMessage.StudentCard -> stringResource(R.string.scan_student_card)
        ReturnScanMessage.TooLong -> stringResource(R.string.scan_too_long)
        ReturnScanMessage.BatchFull -> stringResource(R.string.scan_batch_full, PrestecsRepository.MAX_BATCH_SIZE)
    }
    Text(
        text,
        color = if (message.accepted) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        // Read out by TalkBack as soon as a scan is processed.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ReturnList(state: NewReturnUiState, onRemoveItem: (String) -> Unit, modifier: Modifier = Modifier) {
    // Rows appear once their lookup is done: a laptop without a loan never
    // shows up, so the list doesn't jump.
    val items = state.items.filter { it.lookup != ReturnLookup.Pending }
    val listState = rememberLazyListState()
    ScrollToNewRow(listState, items.size)
    Column(modifier) {
        Text(
            stringResource(R.string.return_batch_title, items.size, PrestecsRepository.MAX_BATCH_SIZE),
            style = MaterialTheme.typography.titleSmall,
        )
        if (state.excludedCount > 0) {
            Text(
                pluralStringResource(R.plurals.return_excluded_hint, state.excludedCount, state.excludedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(state = listState) {
            items(items, key = { it.portatil }) { item ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = flashColor(item.portatil == state.highlighted)),
                    headlineContent = { Text(item.portatil, fontWeight = FontWeight.Bold) },
                    supportingContent = { LookupText(item.lookup) },
                    trailingContent = {
                        IconButton(onClick = { onRemoveItem(item.portatil) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.loan_remove_row, item.portatil),
                            )
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LookupText(lookup: ReturnLookup) {
    when (lookup) {
        ReturnLookup.Pending -> {
            val checking = stringResource(R.string.lookup_checking)
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp).semantics { contentDescription = checking },
                strokeWidth = 2.dp,
            )
        }

        is ReturnLookup.Found -> Text(
            lookup.prestecData?.let { "${lookup.estudiant} · ${formatApiDate(it)}" } ?: lookup.estudiant,
            color = MaterialTheme.colorScheme.success,
        )

        is ReturnLookup.Failed -> Text(lookup.error.message(), color = MaterialTheme.colorScheme.error)
    }
}

@Preview(showBackground = true)
@Composable
private fun NewReturnPreview() {
    PrestecsTheme {
        NewReturnContent(
            state = NewReturnUiState(
                items = listOf(
                    ReturnItem("C1 - P01", ReturnLookup.Found("12345678 - Garcia, Maria", "2026-10-07 08:15:00")),
                    ReturnItem("C1 - P04", ReturnLookup.Failed(ApiError.Network)),
                    ReturnItem("C1 - P02", ReturnLookup.Pending),
                ),
                highlighted = "C1 - P01",
                message = ReturnScanMessage.NotFound("C1 - P09"),
            ),
            onCode = {},
            onRemoveItem = {},
            onSubmit = {},
            onBack = {},
        )
    }
}
