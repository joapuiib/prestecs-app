package com.fpmislata.prestecs.ui.returns

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.data.api.dto.BatchMessageDto
import com.fpmislata.prestecs.data.api.dto.LookupDto
import com.fpmislata.prestecs.data.api.dto.ReturnsResponse
import com.fpmislata.prestecs.data.api.dto.Severity
import com.fpmislata.prestecs.testing.FakePrestecsRepository
import com.fpmislata.prestecs.testing.MainDispatcherRule
import com.fpmislata.prestecs.ui.batch.ScanEvent
import com.fpmislata.prestecs.ui.batch.SubmitOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NewReturnViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** Laptops lent out, by code: the fake lookup answers from here. */
    private val lent = mutableMapOf("C1 - P01" to "Maria", "C1 - P02" to "Joan", "C1 - P03" to "Anna")

    private val repository = FakePrestecsRepository().apply {
        onLookup = { portatil ->
            ApiResult.Success(
                lent[portatil]?.let { LookupDto(found = true, estudiant = it, prestecData = "2026-10-07 08:00:00") }
                    ?: LookupDto(found = false),
            )
        }
    }
    private val savedState = SavedStateHandle()
    private val viewModel = NewReturnViewModel(repository, savedState)
    private val state get() = viewModel.state.value

    private fun lookupOf(portatil: String) = state.items.single { it.portatil == portatil }.lookup

    @Test
    fun `scanned laptops show who has them`() {
        viewModel.onCode(" C1 - P01 ")

        assertEquals(ReturnLookup.Found("Maria", "2026-10-07 08:00:00"), lookupOf("C1 - P01"))
        assertEquals(ReturnScanMessage.Found("C1 - P01", "Maria"), state.message)
        assertEquals(listOf("C1 - P01"), state.toReturn)
    }

    @Test
    fun `laptops without an active loan are listed but not sent`() {
        viewModel.onCode("C1 - P01")
        viewModel.onCode("C9 - P99")

        assertEquals(ReturnLookup.NotFound, lookupOf("C9 - P99"))
        assertEquals(ReturnScanMessage.NotFound("C9 - P99"), state.message)
        assertEquals(listOf("C1 - P01"), state.toReturn)
        assertEquals(1, state.excludedCount)
    }

    @Test
    fun `failed lookups are not sent either`() {
        repository.onLookup = { ApiResult.Failure(ApiError.Network) }

        viewModel.onCode("C1 - P01")

        assertEquals(ReturnLookup.Failed(ApiError.Network), lookupOf("C1 - P01"))
        assertTrue(state.toReturn.isEmpty())
        assertFalse(state.canSubmit)
    }

    @Test
    fun `scan results beep accepted or rejected`() = runTest {
        viewModel.events.test {
            viewModel.onCode("C1 - P01")
            assertEquals(ScanEvent.ACCEPTED, awaitItem())
            viewModel.onCode("C9 - P99")
            assertEquals(ScanEvent.REJECTED, awaitItem())
            viewModel.onCode("C1 - P01")
            assertEquals(ScanEvent.REJECTED, awaitItem())
        }
    }

    @Test
    fun `duplicates are rejected`() {
        viewModel.onCode("C1 - P01")

        viewModel.onCode("C1 - P01")

        assertEquals(1, state.items.size)
        assertEquals(ReturnScanMessage.AlreadyInBatch("C1 - P01"), state.message)
    }

    @Test
    fun `saving waits for pending lookups`() {
        val slow = CompletableDeferred<ApiResult<LookupDto>>()
        val fast = repository.onLookup
        repository.onLookup = { portatil -> if (portatil == "C1 - P02") slow.await() else fast(portatil) }
        viewModel.onCode("C1 - P01")
        viewModel.onCode("C1 - P02")

        assertTrue(state.isLookingUp)
        assertFalse(state.canSubmit)

        slow.complete(ApiResult.Success(LookupDto(found = false)))
        assertTrue(state.canSubmit)
    }

    @Test
    fun `removing a laptop cancels its lookup`() {
        val never = CompletableDeferred<ApiResult<LookupDto>>()
        repository.onLookup = { never.await() }
        viewModel.onCode("C1 - P01")

        viewModel.removeItem("C1 - P01")

        assertTrue(state.items.isEmpty())
        assertFalse(state.isLookingUp)
    }

    @Test
    fun `batches stop at 50 laptops`() {
        repeat(50) { viewModel.onCode("C1 - P%02d".format(it)) }

        viewModel.onCode("C2 - P01")

        assertEquals(50, state.items.size)
        assertEquals(ReturnScanMessage.BatchFull, state.message)
    }

    @Test
    fun `saving sends only laptops with a loan and removes the returned ones`() {
        viewModel.onCode("C1 - P01")
        viewModel.onCode("C1 - P02")
        viewModel.onCode("C9 - P99")
        var sent: List<String>? = null
        repository.onReturns = { portatils ->
            sent = portatils
            ApiResult.Success(
                ReturnsResponse(
                    success = false,
                    returnedCount = 1,
                    messages = listOf(
                        message("devolucio_already_returned", Severity.DANGER, portatil = "C1 - P02"),
                        message("devolucio_success", Severity.SUCCESS),
                    ),
                ),
            )
        }

        viewModel.submit()

        assertEquals(listOf("C1 - P01", "C1 - P02"), sent)
        // P01 was returned; P02 failed and P99 was never sent: both stay.
        assertEquals(listOf("C1 - P02", "C9 - P99"), state.items.map { it.portatil })
        assertEquals(false, (state.outcome as SubmitOutcome.Saved).success)
    }

    @Test
    fun `a failed save keeps everything`() {
        viewModel.onCode("C1 - P01")
        repository.onReturns = { ApiResult.Failure(ApiError.Forbidden) }

        viewModel.submit()

        assertEquals(1, state.items.size)
        assertEquals(SubmitOutcome.Failed(ApiError.Forbidden), state.outcome)
    }

    @Test
    fun `restored laptops are looked up again when the screen resumes`() {
        viewModel.onCode("C1 - P01")
        viewModel.onCode("C9 - P99")
        var lookups = 0
        val lookup = repository.onLookup
        repository.onLookup = { lookups++; lookup(it) }

        val restored = NewReturnViewModel(repository, savedState)
        assertEquals(
            listOf(ReturnLookup.Pending, ReturnLookup.Pending),
            restored.state.value.items.map { it.lookup },
        )

        restored.resumeLookups()
        restored.resumeLookups()

        assertEquals(2, lookups)
        assertEquals(listOf("C1 - P01"), restored.state.value.toReturn)
    }

    private fun message(code: String, severity: Severity, portatil: String? = null) =
        BatchMessageDto(severity = severity, code = code, message = code, portatil = portatil)
}
