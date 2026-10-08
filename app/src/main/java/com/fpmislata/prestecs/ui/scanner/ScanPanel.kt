package com.fpmislata.prestecs.ui.scanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fpmislata.prestecs.R

/**
 * Camera scanner plus an on-demand field to type the code. Typed and scanned
 * codes both arrive through [onCode]. The field folds away when the keyboard
 * closes; what was typed stays for next time. [manualLabel] names what to type
 * (laptop code, student...).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanPanel(onCode: (String) -> Unit, manualLabel: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    // Kept here, not in the field, so folding the field away keeps the draft.
    var draft by rememberSaveable { mutableStateOf("") }
    // While typing, the keyboard leaves little room: hide the camera so the
    // field stays in view.
    val imeVisible = WindowInsets.isImeVisible
    val typing = showManualEntry && imeVisible

    // Closing the keyboard means done typing: fold the field back into the
    // button so it doesn't sit next to the camera. Only after the keyboard has
    // been seen, since it takes a moment to open.
    var imeSeen by remember { mutableStateOf(false) }
    LaunchedEffect(showManualEntry, imeVisible) {
        when {
            !showManualEntry -> imeSeen = false

            imeVisible -> imeSeen = true

            imeSeen -> {
                showManualEntry = false
                imeSeen = false
            }
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!typing) {
            val viewfinder = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
            CameraPermissionGate(modifier = viewfinder) {
                QrScanner(onScan = onCode, enabled = enabled, modifier = viewfinder)
            }
        }

        // Typing works without the camera permission too.
        if (showManualEntry) {
            ManualCodeEntry(
                text = draft,
                onTextChange = { draft = it },
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
