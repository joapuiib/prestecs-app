package com.fpmislata.prestecs.ui.returns

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.ui.scanner.ScannerTrialScreen

@Composable
fun NewReturnScreen(onBack: () -> Unit) {
    ScannerTrialScreen(title = stringResource(R.string.new_return), onBack = onBack)
}
