package com.fpmislata.prestecs.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.core.network.ApiError

/** User-facing text for an [ApiError]. */
@Composable
fun ApiError.message(): String = when (this) {
    ApiError.Network -> stringResource(R.string.error_network)

    ApiError.Unauthorized -> stringResource(R.string.error_unauthorized)

    ApiError.Forbidden -> stringResource(R.string.error_forbidden)

    is ApiError.Server ->
        errorId
            ?.let { stringResource(R.string.error_server_with_id, it) }
            ?: stringResource(R.string.error_server)

    is ApiError.BadRequest, is ApiError.Unexpected -> stringResource(R.string.error_unexpected)
}
