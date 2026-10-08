package com.fpmislata.prestecs.data

import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.network.ApiResult
import com.fpmislata.prestecs.core.session.SessionState
import com.fpmislata.prestecs.data.api.dto.Estat
import com.fpmislata.prestecs.data.api.dto.PrestecRowDto
import com.fpmislata.prestecs.data.api.dto.Severity
import com.fpmislata.prestecs.data.prestecs.ApiPrestecsRepository
import com.fpmislata.prestecs.testing.TestBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiPrestecsRepositoryTest {
    private lateinit var backend: TestBackend
    private lateinit var repository: ApiPrestecsRepository

    @Before
    fun setUp() = runBlocking {
        backend = TestBackend()
        repository = ApiPrestecsRepository(backend.apiClients, backend.sessionRepository, backend.json)
        backend.sessionRepository.logIn(Environment.STAGING, "nom.cognom", TOKEN)
    }

    @After
    fun tearDown() = backend.close()

    @Test
    fun `list sends token and filters, and parses the page`() = runBlocking {
        backend.enqueue(200, LIST_BODY)

        val result = repository.list(setOf(Estat.NO_RETORNAT, Estat.PRESTAT), page = 2, perPage = 20)

        val page = (result as ApiResult.Success).value
        assertEquals(1, page.prestecs.size)
        with(page.prestecs.single()) {
            assertEquals(42L, id)
            assertEquals("C1 - P01", portatil)
            assertEquals(Estat.PRESTAT, estat)
            assertNull(devolucioData)
        }
        assertEquals(57, page.pagination.total)

        val request = backend.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/prestecs.php", request.url.encodedPath)
        assertEquals("Bearer $TOKEN", request.headers["Authorization"])
        assertEquals("prestat,no-retornat", request.url.queryParameter("estat"))
        assertEquals("2", request.url.queryParameter("page"))
        assertEquals("20", request.url.queryParameter("per_page"))
    }

    @Test
    fun `list without filters omits estat`() = runBlocking {
        backend.enqueue(200, LIST_BODY)

        repository.list(emptySet(), page = 1)

        val request = backend.takeRequest()
        assertNull(request.url.queryParameter("estat"))
        assertEquals("50", request.url.queryParameter("per_page"))
    }

    @Test
    fun `create posts JSON rows`() = runBlocking {
        backend.enqueue(200, CREATE_OK_BODY)

        val result = repository.create(listOf(PrestecRowDto("C1 - P01", "Maria Garcia")))

        val response = (result as ApiResult.Success).value
        assertTrue(response.success)
        assertEquals(2, response.createdCount)
        assertEquals(Severity.SUCCESS, response.messages.single().severity)

        val request = backend.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.headers["Content-Type"]!!.startsWith("application/json"))
        assertEquals(
            """{"rows":[{"portatil":"C1 - P01","estudiant":"Maria Garcia"}]}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `create with some failed rows (422) is a result with messages`() = runBlocking {
        backend.enqueue(422, CREATE_PARTIAL_BODY)

        val result = repository.create(listOf(PrestecRowDto("C1 - P01", "Maria Garcia")))

        val response = (result as ApiResult.Success).value
        assertEquals(false, response.success)
        assertEquals(1, response.createdCount)
        val failed = response.messages.first()
        assertEquals("prestec_already_active", failed.code)
        assertEquals(Severity.DANGER, failed.severity)
        assertEquals("C1 - P01", failed.portatil)
    }

    @Test
    fun `returns with some failed laptops (422) is a result with messages`() = runBlocking {
        backend.enqueue(
            422,
            """{"success":false,"returned_count":0,"messages":[{"code":"devolucio_not_found",
               "portatil":"C1 - P09","severity":"danger","message":"ERROR"}]}""",
        )

        val result = repository.registerReturns(listOf("C1 - P09"))

        val response = (result as ApiResult.Success).value
        assertEquals(0, response.returnedCount)
        assertEquals("devolucio_not_found", response.messages.single().code)
        assertEquals("""{"portatils":["C1 - P09"]}""", backend.takeRequest().body?.utf8())
    }

    @Test
    fun `lookup encodes the laptop code`() = runBlocking {
        backend.enqueue(200, """{"found":true,"estudiant":"Maria Garcia","prestec_data":"2026-10-06 08:15:00"}""")

        val result = repository.lookup("C1 - P01")

        val lookup = (result as ApiResult.Success).value
        assertTrue(lookup.found)
        assertEquals("Maria Garcia", lookup.estudiant)
        assertEquals("C1 - P01", backend.takeRequest().url.queryParameter("portatil"))
    }

    @Test
    fun `401 clears the session`() = runBlocking {
        backend.enqueue(401, """{"error":"Invalid or expired token"}""")

        val result = repository.lookup("C1 - P01")

        assertEquals(ApiResult.Failure(ApiError.Unauthorized), result)
        assertEquals(SessionState.LoggedOut(Environment.STAGING, "nom.cognom"), backend.sessionRepository.state.first())
    }

    @Test
    fun `403 keeps the session`() = runBlocking {
        backend.enqueue(403, """{"error":"Forbidden"}""")

        assertEquals(ApiResult.Failure(ApiError.Forbidden), repository.lookup("C1 - P01"))
        assertTrue(backend.sessionRepository.currentSession() != null)
    }

    @Test
    fun `500 exposes the error id`() = runBlocking {
        backend.enqueue(500, """{"error":"Internal server error","error_id":"abc123"}""")

        assertEquals(ApiResult.Failure(ApiError.Server("abc123")), repository.lookup("C1 - P01"))
    }

    @Test
    fun `500 with an HTML body still maps to a server error`() = runBlocking {
        backend.enqueue(500, "<html><body>Internal Server Error</body></html>")

        assertEquals(ApiResult.Failure(ApiError.Server(null)), repository.lookup("C1 - P01"))
    }

    @Test
    fun `400 keeps the debug message`() = runBlocking {
        backend.enqueue(400, """{"error":"Invalid estat \"actiu\""}""")

        assertEquals(
            ApiResult.Failure(ApiError.BadRequest("Invalid estat \"actiu\"")),
            repository.list(emptySet(), page = 1),
        )
    }

    @Test
    fun `connection failure is a network error`() = runBlocking {
        backend.server.close()

        assertEquals(ApiResult.Failure(ApiError.Network), repository.lookup("C1 - P01"))
    }

    @Test
    fun `without a session nothing is sent`() = runBlocking {
        backend.sessionRepository.logOut()

        assertEquals(ApiResult.Failure(ApiError.Unauthorized), repository.lookup("C1 - P01"))
        assertEquals(0, backend.server.requestCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `batches above 50 are rejected before sending`(): Unit = runBlocking {
        repository.registerReturns(List(51) { "C1 - P%02d".format(it) })
    }

    private companion object {
        const val TOKEN = "0123456789abcdef0123456789abcdef"

        // Samples from the backend's API docs.
        const val LIST_BODY = """
            {"prestecs":[{"id":42,"carro":"C1","portatil":"C1 - P01","estudiant":"Maria Garcia",
              "prestec_data":"2026-10-06 08:15:00","prestec_professor":"Nom Cognoms",
              "devolucio_data":null,"devolucio_professor":null,"estat":"prestat"}],
             "pagination":{"page":2,"per_page":20,"total":57,"total_pages":3}}"""

        const val CREATE_OK_BODY = """
            {"success":true,"created_count":2,"messages":[{"code":"prestec_success","count":2,
              "severity":"success","message":"S'han registrat 2 préstec(s) correctament."}]}"""

        const val CREATE_PARTIAL_BODY = """
            {"success":false,"created_count":1,"messages":[
              {"code":"prestec_already_active","portatil":"C1 - P01","severity":"danger",
               "message":"ERROR: ja existeix un préstec actiu per al portàtil C1 - P01."},
              {"code":"prestec_success","count":1,"severity":"success",
               "message":"S'han registrat 1 préstec(s) correctament."}]}"""
    }
}
