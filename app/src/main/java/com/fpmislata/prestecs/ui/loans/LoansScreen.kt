package com.fpmislata.prestecs.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.ui.theme.PrestecsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(onNewLoan: () -> Unit, onNewReturn: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
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

@Preview(showBackground = true)
@Composable
private fun LoansScreenPreview() {
    PrestecsTheme {
        LoansScreen(onNewLoan = {}, onNewReturn = {})
    }
}
