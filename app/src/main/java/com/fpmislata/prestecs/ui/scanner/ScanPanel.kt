package com.fpmislata.prestecs.ui.scanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fpmislata.prestecs.R

/**
 * Camera scanner plus an on-demand field to type the code. Typed and scanned
 * codes both arrive through [onCode]. [manualLabel] names what to type
 * (laptop code, student...).
 */
@Composable
fun ScanPanel(
    onCode: (String) -> Unit,
    manualLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var showManualEntry by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val viewfinder = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
        CameraPermissionGate(modifier = viewfinder) {
            QrScanner(onScan = onCode, enabled = enabled, modifier = viewfinder)
        }

        // Typing works without the camera permission too.
        if (showManualEntry) {
            ManualCodeEntry(
                label = manualLabel,
                onSubmit = { if (enabled) onCode(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(onClick = { showManualEntry = true }) {
                Text(stringResource(R.string.scanner_type_code))
            }
        }
    }
}
