package com.fpmislata.prestecs.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.data.api.dto.BatchMessageDto

/**
 * Text for a batch result message in the app's language. The API's own
 * `message` is Valencian only, so known codes get our strings; unknown codes
 * fall back to the server text.
 */
@Composable
fun BatchMessageDto.text(): String {
    val portatil = portatil.orEmpty()
    val row = row ?: 0
    val count = count ?: 0
    return when (code) {
        "prestec_success" -> pluralStringResource(R.plurals.result_prestec_success, count, count)
        "prestec_empty" -> stringResource(R.string.result_prestec_empty)
        "prestec_row_incomplete" -> stringResource(R.string.result_row_incomplete, row)
        "prestec_row_too_long" -> stringResource(R.string.result_row_too_long, row)
        "prestec_portatil_format" -> stringResource(R.string.result_portatil_format, portatil)
        "prestec_batch_duplicate" -> stringResource(R.string.result_batch_duplicate, portatil)
        "prestec_already_active" -> stringResource(R.string.result_already_active, portatil)
        "devolucio_success" -> pluralStringResource(R.plurals.result_devolucio_success, count, count)
        "devolucio_empty" -> stringResource(R.string.result_devolucio_empty)
        "devolucio_too_long" -> stringResource(R.string.result_too_long, portatil)
        "devolucio_not_found" -> stringResource(R.string.no_active_loan, portatil)
        "devolucio_already_returned" -> stringResource(R.string.result_already_returned, portatil)
        else -> message
    }
}
