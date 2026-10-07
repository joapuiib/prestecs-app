package com.fpmislata.prestecs.ui.loan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.ui.scanner.ScannerTrialScreen

@Composable
fun NewLoanScreen(onBack: () -> Unit) {
    ScannerTrialScreen(title = stringResource(R.string.new_loan), onBack = onBack)
}
