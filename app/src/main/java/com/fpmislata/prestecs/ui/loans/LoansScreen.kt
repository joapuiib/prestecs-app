package com.fpmislata.prestecs.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.ui.components.label
import com.fpmislata.prestecs.ui.theme.PrestecsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    environment: Environment,
    onLogOut: () -> Unit,
    onNewLoan: () -> Unit,
    onNewReturn: () -> Unit,
) {
    Scaffold(
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
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onNewLoan, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.new_loan))
                }
                FilledTonalButton(onClick = onNewReturn, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.new_return))
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.placeholder_coming_soon))
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

@Preview(showBackground = true)
@Composable
private fun LoansScreenPreview() {
    PrestecsTheme {
        LoansScreen(
            environment = Environment.STAGING,
            onLogOut = {},
            onNewLoan = {},
            onNewReturn = {},
        )
    }
}
