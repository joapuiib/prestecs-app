package com.fpmislata.prestecs.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.core.config.Environment

@Composable
fun Environment.label(): String = stringResource(
    when (this) {
        Environment.PROD -> R.string.environment_prod
        Environment.STAGING -> R.string.environment_staging
        Environment.LOCAL -> R.string.environment_local
    },
)
