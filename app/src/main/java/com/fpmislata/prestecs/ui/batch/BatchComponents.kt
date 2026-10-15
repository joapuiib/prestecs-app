package com.fpmislata.prestecs.ui.batch

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.data.api.dto.Severity
import com.fpmislata.prestecs.ui.components.message
import com.fpmislata.prestecs.ui.components.text
import com.fpmislata.prestecs.ui.scanner.rememberScanFeedback
import com.fpmislata.prestecs.ui.theme.success
import com.fpmislata.prestecs.ui.theme.successContainer
import kotlinx.coroutines.flow.Flow

/**
 * Leaves the screen once everything was saved, handing the success text to
 * [onSaved] for the list to show. If anything failed, stays: the errors and
 * the failed items are still needed.
 */
@Composable
fun LeaveWhenSaved(outcome: SubmitOutcome?, onSaved: (String) -> Unit) {
    val saved = outcome as? SubmitOutcome.Saved
    val text = if (saved?.success == true) {
        saved.messages.filter { it.severity == Severity.SUCCESS }.map { it.text() }.joinToString(" ")
    } else {
        null
    }
    LaunchedEffect(saved) { if (text != null) onSaved(text) }
}

/**
 * Room for one line of scan feedback, empty or not, so the list below doesn't
 * move when a message appears or fades.
 */
@Composable
fun MessageLine(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 24.dp)) { content() }
}

/** Row background: green for [FLASH_MILLIS] after the row is added, as `table-success` on the web. */
@Composable
fun flashColor(highlighted: Boolean): Color {
    val color by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.successContainer else Color.Transparent,
        label = "rowFlash",
    )
    return color
}

/** Scrolls to the end when [count] grows: new rows are added at the bottom. */
@Composable
fun ScrollToNewRow(listState: LazyListState, count: Int) {
    var previous by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > previous) listState.animateScrollToItem(count - 1)
        previous = count
    }
}

/** Plays the beep and vibration for each [ScanEvent]. */
@Composable
fun ScanEventsFeedback(events: Flow<ScanEvent>) {
    val feedback = rememberScanFeedback()
    LaunchedEffect(events, feedback) {
        events.collect { event ->
            when (event) {
                ScanEvent.ACCEPTED -> feedback.accepted()
                ScanEvent.REJECTED -> feedback.rejected()
            }
        }
    }
}

/** Result of the last save. [failedHint] explains that failed items were kept. */
@Composable
fun OutcomeCard(outcome: SubmitOutcome, failedHint: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (outcome) {
                is SubmitOutcome.Failed -> Text(outcome.error.message(), color = MaterialTheme.colorScheme.error)

                is SubmitOutcome.Saved -> {
                    outcome.messages.forEach { message ->
                        Text(
                            message.text(),
                            color = if (message.severity == Severity.SUCCESS) {
                                MaterialTheme.colorScheme.success
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    if (!outcome.success) {
                        Text(
                            failedHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SaveBar(label: String, enabled: Boolean, isSubmitting: Boolean, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Button(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(label)
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
