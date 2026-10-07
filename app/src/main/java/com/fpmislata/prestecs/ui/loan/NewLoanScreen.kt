package com.fpmislata.prestecs.ui.loan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.data.api.dto.PrestecRowDto
import com.fpmislata.prestecs.data.prestecs.PrestecsRepository
import com.fpmislata.prestecs.ui.batch.ConfirmDialog
import com.fpmislata.prestecs.ui.batch.OutcomeCard
import com.fpmislata.prestecs.ui.batch.SaveBar
import com.fpmislata.prestecs.ui.batch.ScanEventsFeedback
import com.fpmislata.prestecs.ui.batch.SuccessColor
import com.fpmislata.prestecs.ui.components.message
import com.fpmislata.prestecs.ui.scanner.ScanPanel
import com.fpmislata.prestecs.ui.theme.PrestecsTheme

@Composable
fun NewLoanScreen(onBack: () -> Unit, viewModel: NewLoanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScanEventsFeedback(viewModel.events)

    NewLoanContent(
        state = state,
        onCode = viewModel::onCode,
        onCancelPending = viewModel::cancelPending,
        onRemoveRow = viewModel::removeRow,
        onSubmit = viewModel::submit,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewLoanContent(
    state: NewLoanUiState,
    onCode: (String) -> Unit,
    onCancelPending: () -> Unit,
    onRemoveRow: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmSubmit by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    // Scanned rows are lost on leaving: ask first.
    val leave: () -> Unit = { if (state.rows.isNotEmpty()) confirmDiscard = true else onBack() }
    BackHandler(enabled = state.rows.isNotEmpty()) { confirmDiscard = true }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_loan)) },
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
                label = stringResource(R.string.loan_save, state.rows.size),
                enabled = state.canSubmit,
                isSubmitting = state.isSubmitting,
                onSave = { confirmSubmit = true },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepIndicator(step = state.step, onCancelPending = onCancelPending)
            ScanPanel(
                onCode = onCode,
                manualLabel = stringResource(
                    if (state.step is LoanStep.WaitingEstudiant) {
                        R.string.loan_step_estudiant
                    } else {
                        R.string.loan_step_portatil
                    },
                ),
                enabled = state.canScan,
                modifier = Modifier.fillMaxWidth(),
            )
            state.message?.let { ScanMessageText(it) }
            state.outcome?.let { outcome ->
                OutcomeCard(outcome, failedHint = stringResource(R.string.loan_failed_rows_hint))
            }
            BatchList(rows = state.rows, onRemoveRow = onRemoveRow, modifier = Modifier.weight(1f))
        }
    }

    if (confirmSubmit) {
        ConfirmDialog(
            title = stringResource(R.string.action_confirm),
            text = pluralStringResource(R.plurals.loan_confirm_text, state.rows.size, state.rows.size),
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
            title = stringResource(R.string.loan_discard_title),
            text = stringResource(R.string.loan_discard_text),
            confirmLabel = stringResource(R.string.action_discard),
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/**
 * The two values of the row being scanned. Like the web form: yellow while
 * waiting for that scan, green once done.
 */
@Composable
private fun StepIndicator(step: LoanStep, onCancelPending: () -> Unit) {
    val portatil = when (step) {
        is LoanStep.CheckingPortatil -> step.portatil
        is LoanStep.WaitingEstudiant -> step.portatil
        LoanStep.WaitingPortatil -> null
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StepSlot(
            label = stringResource(R.string.loan_step_portatil),
            value = portatil ?: stringResource(R.string.loan_scan_portatil),
            status = when (step) {
                LoanStep.WaitingPortatil -> SlotStatus.ACTIVE
                is LoanStep.CheckingPortatil -> SlotStatus.CHECKING
                is LoanStep.WaitingEstudiant -> SlotStatus.DONE
            },
            modifier = Modifier.weight(1f),
        )
        StepSlot(
            label = stringResource(R.string.loan_step_estudiant),
            value = stringResource(R.string.loan_scan_estudiant),
            status = if (step is LoanStep.WaitingEstudiant) SlotStatus.ACTIVE else SlotStatus.IDLE,
            modifier = Modifier.weight(1f),
        )
        if (step is LoanStep.WaitingEstudiant) {
            IconButton(onClick = onCancelPending) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.loan_cancel_pending),
                )
            }
        }
    }
}

private enum class SlotStatus { IDLE, ACTIVE, CHECKING, DONE }

@Composable
private fun StepSlot(label: String, value: String, status: SlotStatus, modifier: Modifier = Modifier) {
    // Bootstrap warning/success tones, as in the web form.
    val (container, border) = when (status) {
        SlotStatus.ACTIVE, SlotStatus.CHECKING -> Color(0xFFFFF3CD) to Color(0xFFFFC107)
        SlotStatus.DONE -> Color(0xFFD1E7DD) to Color(0xFF198754)
        SlotStatus.IDLE -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.outlineVariant
    }
    val content = if (status == SlotStatus.IDLE) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF1D2330)
    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        border = BorderStroke(2.dp, border),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status == SlotStatus.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = content)
                }
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (status == SlotStatus.DONE) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ScanMessageText(message: ScanMessage) {
    val text = when (message) {
        is ScanMessage.PortatilAvailable -> stringResource(R.string.scan_portatil_available, message.portatil)
        is ScanMessage.Added -> stringResource(R.string.scan_loan_added, message.row.portatil, message.row.estudiant)
        is ScanMessage.InvalidCode -> stringResource(R.string.scan_invalid_code, message.code)
        ScanMessage.TooLong -> stringResource(R.string.scan_too_long)
        is ScanMessage.AlreadyInBatch -> stringResource(R.string.scan_already_in_batch, message.portatil)
        is ScanMessage.AlreadyLent -> stringResource(R.string.scan_already_lent, message.portatil, message.estudiant)
        is ScanMessage.LookupFailed -> message.error.message()
        ScanMessage.StudentIsLaptop -> stringResource(R.string.scan_student_is_laptop)
        ScanMessage.BatchFull -> stringResource(
            R.string.scan_batch_full,
            PrestecsRepository.MAX_BATCH_SIZE,
        )
    }
    Text(
        text,
        color = if (message.accepted) SuccessColor else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun BatchList(rows: List<PrestecRowDto>, onRemoveRow: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            stringResource(R.string.loan_batch_title, rows.size, PrestecsRepository.MAX_BATCH_SIZE),
            style = MaterialTheme.typography.titleSmall,
        )
        LazyColumn {
            // Newest first: the row just scanned stays in view.
            items(rows.asReversed(), key = { it.portatil }) { row ->
                ListItem(
                    headlineContent = { Text(row.portatil, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(row.estudiant) },
                    trailingContent = {
                        IconButton(onClick = { onRemoveRow(row.portatil) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.loan_remove_row, row.portatil),
                            )
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NewLoanPreview() {
    PrestecsTheme {
        NewLoanContent(
            state = NewLoanUiState(
                step = LoanStep.WaitingEstudiant("C1 - P03"),
                rows = listOf(
                    PrestecRowDto("C1 - P01", "12345678 - Garcia, Maria"),
                    PrestecRowDto("C1 - P02", "87654321 - Pérez, Joan"),
                ),
                message = ScanMessage.PortatilAvailable("C1 - P03"),
            ),
            onCode = {},
            onCancelPending = {},
            onRemoveRow = {},
            onSubmit = {},
            onBack = {},
        )
    }
}
