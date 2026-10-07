package com.fpmislata.prestecs.ui.returns

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.ui.components.PlaceholderScreen

@Composable
fun NewReturnScreen(onBack: () -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.new_return), onBack = onBack)
}
