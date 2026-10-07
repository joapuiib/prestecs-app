package com.fpmislata.prestecs.ui.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.data.api.dto.Severity
import com.fpmislata.prestecs.ui.components.message
import com.fpmislata.prestecs.ui.components.text
import com.fpmislata.prestecs.ui.scanner.rememberScanFeedback
import com.fpmislata.prestecs.ui.theme.success
import kotlinx.coroutines.flow.Flow

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
