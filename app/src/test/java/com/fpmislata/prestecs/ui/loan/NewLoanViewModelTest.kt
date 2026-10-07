package com.fpmislata.prestecs.ui.loan

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.data.api.dto.BatchMessageDto
import com.fpmislata.prestecs.data.api.dto.CreatePrestecsResponse
import com.fpmislata.prestecs.data.api.dto.LookupDto
import com.fpmislata.prestecs.data.api.dto.PrestecRowDto
import com.fpmislata.prestecs.data.api.dto.Severity
import com.fpmislata.prestecs.testing.FakePrestecsRepository
import com.fpmislata.prestecs.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NewLoanViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakePrestecsRepository().apply {
        onLookup = { ApiResult.Success(LookupDto(found = false)) }
    }
    private val savedState = SavedStateHandle()
    private val viewModel = NewLoanViewModel(repository, savedState)
    private val state get() = viewModel.state.value

    private fun scanRow(portatil: String, estudiant: String) {
        viewModel.onCode(portatil)
        viewModel.onCode(estudiant)
    }

    @Test
    fun `laptop then student card adds a row`() {
        viewModel.onCode(" C1 - P01 ")
        assertEquals(LoanStep.WaitingEstudiant("C1 - P01"), state.step)
        assertEquals(ScanMessage.PortatilAvailable("C1 - P01"), state.message)

        viewModel.onCode("""{"nia":"12345678","name":"Maria","surname":"Garcia"}""")

        val row = PrestecRowDto("C1 - P01", "12345678 - Garcia, Maria")
        assertEquals(listOf(row), state.rows)
        assertEquals(LoanStep.WaitingPortatil, state.step)
        assertEquals(ScanMessage.Added(row), state.message)
    }

    @Test
    fun `scan results beep accepted or rejected`() = runTest {
        viewModel.events.test {
            viewModel.onCode("C1 - P01")
            assertEquals(ScanEvent.ACCEPTED, awaitItem())
            viewModel.onCode("C1 - P01")
            assertEquals(ScanEvent.REJECTED, awaitItem())
        }
    }

    @Test
    fun `codes without the spaced dash are rejected without asking the API`() {
        var lookups = 0
        repository.onLookup = { lookups++; ApiResult.Success(LookupDto(found = false)) }

        viewModel.onCode("C1-P01")

        assertEquals(ScanMessage.InvalidCode("C1-P01"), state.message)
        assertEquals(LoanStep.WaitingPortatil, state.step)
        assertEquals(0, lookups)
    }

    @Test
    fun `a laptop already in the batch is rejected without asking the API`() {
        scanRow("C1 - P01", "Maria")
        var lookups = 0
        repository.onLookup = { lookups++; ApiResult.Success(LookupDto(found = false)) }

        viewModel.onCode("C1 - P01")

        assertEquals(ScanMessage.AlreadyInBatch("C1 - P01"), state.message)
        assertEquals(0, lookups)
    }

    @Test
    fun `a laptop already lent is rejected`() {
        repository.onLookup = { ApiResult.Success(LookupDto(found = true, estudiant = "Joan Pérez")) }

        viewModel.onCode("C1 - P01")

        assertEquals(ScanMessage.AlreadyLent("C1 - P01", "Joan Pérez"), state.message)
        assertEquals(LoanStep.WaitingPortatil, state.step)
    }

    @Test
    fun `lookup errors are shown and the laptop can be scanned again`() {
        repository.onLookup = { ApiResult.Failure(ApiError.Network) }

        viewModel.onCode("C1 - P01")

        assertEquals(ScanMessage.LookupFailed(ApiError.Network), state.message)
        assertEquals(LoanStep.WaitingPortatil, state.step)
    }

    @Test
    fun `scans are ignored while the laptop is being checked`() {
        val pending = CompletableDeferred<ApiResult<LookupDto>>()
        repository.onLookup = { pending.await() }

        viewModel.onCode("C1 - P01")
        viewModel.onCode("Maria")
        assertEquals(LoanStep.CheckingPortatil("C1 - P01"), state.step)

        pending.complete(ApiResult.Success(LookupDto(found = false)))
        assertEquals(LoanStep.WaitingEstudiant("C1 - P01"), state.step)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun `scanning the laptop again as the student is rejected`() {
        viewModel.onCode("C1 - P01")

        viewModel.onCode("C1 - P01")

        assertEquals(ScanMessage.StudentIsLaptop, state.message)
        assertEquals(LoanStep.WaitingEstudiant("C1 - P01"), state.step)
    }

    @Test
    fun `cancel drops the laptop waiting for a student`() {
        viewModel.onCode("C1 - P01")

        viewModel.cancelPending()

        assertEquals(LoanStep.WaitingPortatil, state.step)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun `batches stop at 50 rows`() {
        repeat(50) { scanRow("C1 - P%02d".format(it), "Estudiant $it") }

        viewModel.onCode("C2 - P01")

        assertEquals(50, state.rows.size)
        assertEquals(ScanMessage.BatchFull, state.message)
    }

    @Test
    fun `rows can be removed`() {
        scanRow("C1 - P01", "Maria")
        scanRow("C1 - P02", "Joan")

        viewModel.removeRow("C1 - P01")

        assertEquals(listOf("C1 - P02"), state.rows.map { it.portatil })
    }

    @Test
    fun `saving everything clears the batch`() {
        scanRow("C1 - P01", "Maria")
        val success = message("prestec_success", Severity.SUCCESS, count = 1)
        var sent: List<PrestecRowDto>? = null
        repository.onCreate = { rows ->
            sent = rows
            ApiResult.Success(CreatePrestecsResponse(success = true, createdCount = 1, messages = listOf(success)))
        }

        viewModel.submit()

        assertEquals(listOf(PrestecRowDto("C1 - P01", "Maria")), sent)
        assertTrue(state.rows.isEmpty())
        assertEquals(SubmitOutcome.Saved(success = true, messages = listOf(success)), state.outcome)
    }

    @Test
    fun `rows that failed stay in the batch`() {
        scanRow("C1 - P01", "Maria")
        scanRow("C1 - P02", "Joan")
        scanRow("C1 - P03", "Anna")
        repository.onCreate = {
            ApiResult.Success(
                CreatePrestecsResponse(
                    success = false,
                    createdCount = 1,
                    messages = listOf(
                        message("prestec_already_active", Severity.DANGER, portatil = "C1 - P01"),
                        message("prestec_row_too_long", Severity.DANGER, row = 3),
                        message("prestec_success", Severity.SUCCESS, count = 1),
                    ),
                ),
            )
        }

        viewModel.submit()

        assertEquals(listOf("C1 - P01", "C1 - P03"), state.rows.map { it.portatil })
        assertEquals(false, (state.outcome as SubmitOutcome.Saved).success)
    }

    @Test
    fun `a failed save keeps every row`() {
        scanRow("C1 - P01", "Maria")
        repository.onCreate = { ApiResult.Failure(ApiError.Server("abc")) }

        viewModel.submit()

        assertEquals(1, state.rows.size)
        assertEquals(SubmitOutcome.Failed(ApiError.Server("abc")), state.outcome)
    }

    @Test
    fun `the batch survives the process being killed`() {
        scanRow("C1 - P01", "Maria")

        val restored = NewLoanViewModel(repository, savedState)

        assertEquals(listOf(PrestecRowDto("C1 - P01", "Maria")), restored.state.value.rows)
        assertNull(restored.state.value.message)
    }

    private fun message(code: String, severity: Severity, portatil: String? = null, row: Int? = null, count: Int? = null) =
        BatchMessageDto(severity = severity, code = code, message = code, portatil = portatil, row = row, count = count)
}
