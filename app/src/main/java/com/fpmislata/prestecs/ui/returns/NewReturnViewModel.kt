package com.fpmislata.prestecs.ui.returns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.data.prestecs.PrestecsRepository
import com.fpmislata.prestecs.domain.PortatilCode
import com.fpmislata.prestecs.domain.StudentQr
import com.fpmislata.prestecs.ui.batch.ERROR_MESSAGE_MILLIS
import com.fpmislata.prestecs.ui.batch.FLASH_MILLIS
import com.fpmislata.prestecs.ui.batch.ScanEvent
import com.fpmislata.prestecs.ui.batch.SubmitOutcome
import com.fpmislata.prestecs.ui.batch.failedItems
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Who has the laptop, as far as the API knows. A laptop without an active loan
 * has nothing to return: it is dropped from the list, not shown here.
 */
sealed interface ReturnLookup {
    data object Pending : ReturnLookup

    data class Found(val estudiant: String, val prestecData: String?) : ReturnLookup

    data class Failed(val error: ApiError) : ReturnLookup
}

data class ReturnItem(val portatil: String, val lookup: ReturnLookup)

/** Outcome of the last scan, shown under the scanner. */
sealed interface ReturnScanMessage {
    val accepted: Boolean

    data class NotFound(val portatil: String) : ReturnScanMessage {
        override val accepted = false
    }

    data class LookupFailed(val portatil: String, val error: ApiError) : ReturnScanMessage {
        override val accepted = false
    }

    /** A student card scanned where a laptop is expected. */
    data object StudentCard : ReturnScanMessage {
        override val accepted = false
    }

    data class AlreadyInBatch(val portatil: String) : ReturnScanMessage {
        override val accepted = false
    }

    data object TooLong : ReturnScanMessage {
        override val accepted = false
    }

    data object BatchFull : ReturnScanMessage {
        override val accepted = false
    }
}

data class NewReturnUiState(
    val items: List<ReturnItem> = emptyList(),
    /** Laptop found a moment ago: shown green for [FLASH_MILLIS]. */
    val highlighted: String? = null,
    val message: ReturnScanMessage? = null,
    val isSubmitting: Boolean = false,
    val outcome: SubmitOutcome? = null,
) {
    /** Only laptops with an active loan are sent. */
    val toReturn: List<String> get() = items.filter { it.lookup is ReturnLookup.Found }.map { it.portatil }

    /** Laptops that won't be sent because the lookup failed. */
    val excludedCount: Int get() = items.count { it.lookup is ReturnLookup.Failed }

    val isLookingUp: Boolean get() = items.any { it.lookup == ReturnLookup.Pending }

    // Wait for every lookup: a pending one could still turn out not found,
    // and the confirm dialog must count only what will be returned.
    val canSubmit: Boolean get() = toReturn.isNotEmpty() && !isLookingUp && !isSubmitting
}

/**
 * Return batch: scan laptops one after another; each is looked up at once to
 * show who had it, and those without an active loan are dropped. Same rules
 * as the web form (devolucio.js).
 */
@HiltViewModel
class NewReturnViewModel @Inject constructor(
    private val repository: PrestecsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    // The scanned laptops survive the process being killed. Their lookups are
    // not saved: they come back pending and resumeLookups() asks again.
    private val _state = MutableStateFlow(
        NewReturnUiState(
            items = savedState.get<ArrayList<String>>(PORTATILS_KEY).orEmpty()
                .map { ReturnItem(it, ReturnLookup.Pending) },
        ),
    )
    val state: StateFlow<NewReturnUiState> = _state.asStateFlow()

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events: Flow<ScanEvent> = _events.receiveAsFlow()

    private val lookups = mutableMapOf<String, Job>()
    private var messageJob: Job? = null
    private var flashJob: Job? = null

    /**
     * Starts the lookups of restored laptops. The screen calls it when shown;
     * repeated calls do nothing.
     */
    fun resumeLookups() {
        _state.value.items
            .filter { it.lookup == ReturnLookup.Pending && lookups[it.portatil]?.isActive != true }
            .forEach { lookUp(it.portatil, announce = false) }
    }

    /** A scanned or typed laptop code. */
    fun onCode(code: String) {
        val portatil = code.trim()
        val current = _state.value
        if (portatil.isEmpty() || current.isSubmitting) return
        val rejection = when {
            StudentQr.isCard(portatil) -> ReturnScanMessage.StudentCard
            current.items.size >= PrestecsRepository.MAX_BATCH_SIZE -> ReturnScanMessage.BatchFull
            portatil.length > PortatilCode.MAX_LENGTH -> ReturnScanMessage.TooLong
            current.items.any { it.portatil == portatil } -> ReturnScanMessage.AlreadyInBatch(portatil)
            else -> null
        }
        if (rejection != null) return show(rejection)

        _state.update { it.copy(outcome = null) }
        add(portatil)
    }

    fun removeItem(portatil: String) {
        lookups.remove(portatil)?.cancel()
        setItems(_state.value.items.filterNot { it.portatil == portatil })
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val sent = current.toReturn
        _state.update { it.copy(isSubmitting = true, outcome = null, message = null) }
        viewModelScope.launch {
            when (val result = repository.registerReturns(sent)) {
                is ApiResult.Success -> {
                    val response = result.value
                    // Remove what was returned; keep what failed and what
                    // wasn't sent (no active loan) so the teacher sees it.
                    val failed = failedItems(sent, response.messages) { it }.toSet()
                    val returned = sent.toSet() - failed
                    setItems(_state.value.items.filterNot { it.portatil in returned })
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            outcome = SubmitOutcome.Saved(response.success, response.messages),
                        )
                    }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(isSubmitting = false, outcome = SubmitOutcome.Failed(result.error))
                }
            }
        }
    }

    private fun add(portatil: String) {
        setItems(_state.value.items + ReturnItem(portatil, ReturnLookup.Pending))
        lookUp(portatil, announce = true)
    }

    /** [announce]: show and beep the result, for a laptop just scanned. */
    private fun lookUp(portatil: String, announce: Boolean) {
        lookups[portatil] = viewModelScope.launch {
            // null: the laptop has no active loan.
            val lookup: ReturnLookup? = when (val result = repository.lookup(portatil)) {
                is ApiResult.Failure -> ReturnLookup.Failed(result.error)

                is ApiResult.Success -> if (result.value.found) {
                    ReturnLookup.Found(result.value.estudiant.orEmpty(), result.value.prestecData)
                } else {
                    null
                }
            }
            lookups.remove(portatil)
            if (lookup == null) {
                // No active loan: nothing to return, so the row doesn't stay.
                setItems(_state.value.items.filterNot { it.portatil == portatil })
                if (announce) show(ReturnScanMessage.NotFound(portatil))
                return@launch
            }
            _state.update { state ->
                state.copy(
                    items = state.items.map { if (it.portatil == portatil) it.copy(lookup = lookup) else it },
                )
            }
            if (!announce) return@launch
            when (lookup) {
                // The row, flashed green, is the confirmation: no message.
                is ReturnLookup.Found -> {
                    flash(portatil)
                    _events.trySend(ScanEvent.ACCEPTED)
                }

                is ReturnLookup.Failed -> show(ReturnScanMessage.LookupFailed(portatil, lookup.error))

                ReturnLookup.Pending -> Unit
            }
        }
    }

    /** Rejections fade after [ERROR_MESSAGE_MILLIS], like the web form. */
    private fun show(message: ReturnScanMessage) {
        messageJob?.cancel()
        _state.update { it.copy(message = message) }
        _events.trySend(if (message.accepted) ScanEvent.ACCEPTED else ScanEvent.REJECTED)
        if (!message.accepted) {
            messageJob = viewModelScope.launch {
                delay(ERROR_MESSAGE_MILLIS)
                _state.update { if (it.message == message) it.copy(message = null) else it }
            }
        }
    }

    private fun flash(portatil: String) {
        flashJob?.cancel()
        _state.update { it.copy(highlighted = portatil) }
        flashJob = viewModelScope.launch {
            delay(FLASH_MILLIS)
            _state.update { it.copy(highlighted = null) }
        }
    }

    private fun setItems(items: List<ReturnItem>) {
        savedState[PORTATILS_KEY] = ArrayList(items.map { it.portatil })
        _state.update { it.copy(items = items) }
    }

    private companion object {
        const val PORTATILS_KEY = "portatils"
    }
}
