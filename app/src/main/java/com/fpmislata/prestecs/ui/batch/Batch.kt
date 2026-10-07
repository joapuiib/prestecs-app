package com.fpmislata.prestecs.ui.batch

import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.data.api.dto.BatchMessageDto
import com.fpmislata.prestecs.data.api.dto.Severity

// Shared by the loan and return batches.

/** How long a rejected scan stays on screen, as on the web form. */
const val ERROR_MESSAGE_MILLIS = 3_000L

/** How long a new row stays green, as on the web form. */
const val FLASH_MILLIS = 1_200L

/** What the API answered to the last save. */
sealed interface SubmitOutcome {
    /** All items, or only some (`success == false`): see [messages]. */
    data class Saved(val success: Boolean, val messages: List<BatchMessageDto>) : SubmitOutcome

    /** Nothing was saved. */
    data class Failed(val error: ApiError) : SubmitOutcome
}

/** For the scan sound and vibration. One-shot: each scan beeps once. */
enum class ScanEvent { ACCEPTED, REJECTED }

/**
 * Items the API didn't register, to keep them in the batch so they can be
 * fixed or removed. Failures point at an item by laptop code or by its
 * 1-based position among the items sent.
 */
fun <T> failedItems(sent: List<T>, messages: List<BatchMessageDto>, portatilOf: (T) -> String): List<T> {
    val failures = messages.filter { it.severity == Severity.DANGER }
    val failedPortatils = failures.mapNotNullTo(HashSet()) { it.portatil }
    val failedPositions = failures.mapNotNullTo(HashSet()) { it.row }
    return sent.filterIndexed { index, item ->
        portatilOf(item) in failedPortatils || (index + 1) in failedPositions
    }
}
