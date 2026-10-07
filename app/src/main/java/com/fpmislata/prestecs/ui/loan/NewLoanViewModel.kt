package com.fpmislata.prestecs.ui.loan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.data.api.dto.PrestecRowDto
import com.fpmislata.prestecs.data.prestecs.PrestecsRepository
import com.fpmislata.prestecs.domain.PortatilCode
import com.fpmislata.prestecs.domain.StudentQr
import com.fpmislata.prestecs.ui.batch.ScanEvent
import com.fpmislata.prestecs.ui.batch.SubmitOutcome
import com.fpmislata.prestecs.ui.batch.failedItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Where the two-step scan (laptop, then student card) is. */
sealed interface LoanStep {
    data object WaitingPortatil : LoanStep

    /** Asking the API whether the laptop is already lent. */
    data class CheckingPortatil(val portatil: String) : LoanStep

    data class WaitingEstudiant(val portatil: String) : LoanStep
}

/** Outcome of the last scan, shown under the scanner. */
sealed interface ScanMessage {
    val accepted: Boolean

    data class PortatilAvailable(val portatil: String) : ScanMessage {
        override val accepted = true
    }

    data class Added(val row: PrestecRowDto) : ScanMessage {
        override val accepted = true
    }

    data class InvalidCode(val code: String) : ScanMessage {
        override val accepted = false
    }

    data object TooLong : ScanMessage {
        override val accepted = false
    }

    data class AlreadyInBatch(val portatil: String) : ScanMessage {
        override val accepted = false
    }

    data class AlreadyLent(val portatil: String, val estudiant: String) : ScanMessage {
        override val accepted = false
    }

    data class LookupFailed(val error: ApiError) : ScanMessage {
        override val accepted = false
    }

    data object StudentIsLaptop : ScanMessage {
        override val accepted = false
    }

    data object BatchFull : ScanMessage {
        override val accepted = false
    }
}

data class NewLoanUiState(
    val step: LoanStep = LoanStep.WaitingPortatil,
    val rows: List<PrestecRowDto> = emptyList(),
    val message: ScanMessage? = null,
    val isSubmitting: Boolean = false,
    val outcome: SubmitOutcome? = null,
) {
    val canScan: Boolean get() = step !is LoanStep.CheckingPortatil && !isSubmitting
    val canSubmit: Boolean get() = rows.isNotEmpty() && step !is LoanStep.CheckingPortatil && !isSubmitting
}

/**
 * Loan batch: scan a laptop, check it's not lent, scan the student card,
 * repeat; then save all rows at once. Same rules as the web form (prestec.js).
 */
@HiltViewModel
class NewLoanViewModel @Inject constructor(
    private val repository: PrestecsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    // The batch survives the process being killed in the background.
    private val _state = MutableStateFlow(NewLoanUiState(rows = restoreRows()))
    val state: StateFlow<NewLoanUiState> = _state.asStateFlow()

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events: Flow<ScanEvent> = _events.receiveAsFlow()

    /** A scanned or typed code, for whichever step is active. */
    fun onCode(code: String) {
        val current = _state.value
        if (!current.canScan) return
        when (val step = current.step) {
            LoanStep.WaitingPortatil -> onPortatil(code.trim(), current.rows)
            is LoanStep.WaitingEstudiant -> onEstudiant(code, step.portatil)
            is LoanStep.CheckingPortatil -> Unit
        }
    }

    /** Drops the laptop waiting for a student and starts over. */
    fun cancelPending() {
        _state.update { it.copy(step = LoanStep.WaitingPortatil, message = null) }
    }

    fun removeRow(portatil: String) {
        setRows(_state.value.rows.filterNot { it.portatil == portatil })
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val sent = current.rows
        _state.update { it.copy(isSubmitting = true, outcome = null, message = null) }
        viewModelScope.launch {
            when (val result = repository.create(sent)) {
                is ApiResult.Success -> {
                    val response = result.value
                    setRows(failedItems(sent, response.messages) { it.portatil })
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

    private fun onPortatil(portatil: String, rows: List<PrestecRowDto>) {
        val rejection = when {
            portatil.isEmpty() -> return
            rows.size >= PrestecsRepository.MAX_BATCH_SIZE -> ScanMessage.BatchFull
            portatil.length > PortatilCode.MAX_LENGTH -> ScanMessage.TooLong
            !PortatilCode.isValid(portatil) -> ScanMessage.InvalidCode(portatil)
            rows.any { it.portatil == portatil } -> ScanMessage.AlreadyInBatch(portatil)
            else -> null
        }
        if (rejection != null) return show(rejection)

        _state.update { it.copy(step = LoanStep.CheckingPortatil(portatil), message = null, outcome = null) }
        viewModelScope.launch {
            val result = repository.lookup(portatil)
            val lookup = (result as? ApiResult.Success)?.value
            val next = if (lookup?.found == false) LoanStep.WaitingEstudiant(portatil) else LoanStep.WaitingPortatil
            _state.update { it.copy(step = next) }
            show(
                when {
                    result is ApiResult.Failure -> ScanMessage.LookupFailed(result.error)
                    lookup?.found == true -> ScanMessage.AlreadyLent(portatil, lookup.estudiant.orEmpty())
                    else -> ScanMessage.PortatilAvailable(portatil)
                },
            )
        }
    }

    private fun onEstudiant(code: String, portatil: String) {
        val estudiant = StudentQr.parse(code)
        val rejection = when {
            estudiant.isEmpty() -> return
            estudiant == portatil -> ScanMessage.StudentIsLaptop
            estudiant.length > PortatilCode.MAX_LENGTH -> ScanMessage.TooLong
            else -> null
        }
        if (rejection != null) return show(rejection)

        val row = PrestecRowDto(portatil = portatil, estudiant = estudiant)
        setRows(_state.value.rows + row)
        _state.update { it.copy(step = LoanStep.WaitingPortatil) }
        show(ScanMessage.Added(row))
    }

    private fun show(message: ScanMessage) {
        _state.update { it.copy(message = message) }
        _events.trySend(if (message.accepted) ScanEvent.ACCEPTED else ScanEvent.REJECTED)
    }

    private fun setRows(rows: List<PrestecRowDto>) {
        savedState[ROWS_KEY] = Json.encodeToString(RowsSerializer, rows)
        _state.update { it.copy(rows = rows) }
    }

    private fun restoreRows(): List<PrestecRowDto> =
        savedState.get<String>(ROWS_KEY)?.let { Json.decodeFromString(RowsSerializer, it) }.orEmpty()

    private companion object {
        const val ROWS_KEY = "rows"
        val RowsSerializer = ListSerializer(PrestecRowDto.serializer())
    }
}

