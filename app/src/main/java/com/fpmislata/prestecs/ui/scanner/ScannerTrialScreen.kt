package com.fpmislata.prestecs.ui.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fpmislata.prestecs.R

/**
 * Temporary screen to try the scanner on a phone until the loan and return
 * flows use it. Lists the codes read, newest first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerTrialScreen(title: String, onBack: () -> Unit) {
    val codes = rememberSaveable(saver = CodesSaver) { mutableStateListOf<String>() }
    val feedback = rememberScanFeedback()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScanPanel(
                onCode = { code ->
                    feedback.accepted()
                    codes.add(0, code)
                },
                manualLabel = stringResource(R.string.scanner_code_label),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.scanner_trial_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn {
                itemsIndexed(codes) { index, code ->
                    ListItem(
                        headlineContent = { Text(code) },
                        leadingContent = { Text("${codes.size - index}") },
                    )
                }
            }
        }
    }
}

private val CodesSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)
