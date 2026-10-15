package com.fpmislata.prestecs.data

import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.core.network.ApiError
import com.fpmislata.prestecs.core.session.Session
import com.fpmislata.prestecs.data.auth.AuthRepository
import com.fpmislata.prestecs.data.auth.LoginResult
import com.fpmislata.prestecs.testing.TestBackend
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {
    private lateinit var backend: TestBackend
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        backend = TestBackend()
        repository = AuthRepository(backend.apiClients, backend.sessionRepository, backend.json)
    }

    @After
    fun tearDown() = backend.close()

    @Test
    fun `gets a Moodle token, checks it against the API and stores it`() = runBlocking {
        backend.enqueue(200, """{"token":"t0k3n","privatetoken":null}""")
        backend.enqueue(200, """{"found":false}""")

        val result = repository.logIn(Environment.STAGING, "  nom.cognom ", "s3cret&=")

        assertEquals(LoginResult.Success, result)

        val tokenRequest = backend.takeRequest()
        assertEquals("POST", tokenRequest.method)
        assertEquals("/moodle/login/token.php", tokenRequest.url.encodedPath)
        assertEquals(
            "username=nom.cognom&password=s3cret%26%3D&service=moodle_mobile_app",
            tokenRequest.body?.utf8(),
        )

        val check = backend.takeRequest()
        assertEquals("/api/lookup.php", check.url.encodedPath)
        assertEquals("Bearer t0k3n", check.headers["Authorization"])

        assertEquals(Session(Environment.STAGING, "t0k3n"), backend.sessionRepository.currentSession())
    }

    @Test
    fun `wrong password`() = runBlocking {
        backend.enqueue(200, """{"error":"Invalid login, please try again","errorcode":"invalidlogin"}""")

        assertEquals(LoginResult.InvalidCredentials, repository.logIn(Environment.STAGING, "nom", "bad"))
        assertEquals(1, backend.server.requestCount)
        assertNull(backend.sessionRepository.currentSession())
    }

    @Test
    fun `other Moodle errors keep Moodle's message`() = runBlocking {
        backend.enqueue(200, """{"error":"Web services are disabled","errorcode":"enablewsdescription"}""")

        assertEquals(
            LoginResult.MoodleError("Web services are disabled"),
            repository.logIn(Environment.STAGING, "nom", "pw"),
        )
    }

    @Test
    fun `token rejected by the API is not stored`() = runBlocking {
        backend.enqueue(200, """{"token":"t0k3n"}""")
        backend.enqueue(401, """{"error":"Invalid or expired token"}""")

        assertEquals(
            LoginResult.Failure(ApiError.Unauthorized),
            repository.logIn(Environment.STAGING, "nom", "pw"),
        )
        assertNull(backend.sessionRepository.currentSession())
    }

    @Test
    fun `local backend uses the mock token without Moodle`() = runBlocking {
        backend.enqueue(200, """{"found":false}""")

        assertEquals(LoginResult.Success, repository.logIn(Environment.LOCAL, "", ""))

        val check = backend.takeRequest()
        assertEquals("/api/lookup.php", check.url.encodedPath)
        assertEquals("Bearer ${Environment.LOCAL_TOKEN}", check.headers["Authorization"])
        assertEquals(1, backend.server.requestCount)
        assertEquals(
            Session(Environment.LOCAL, Environment.LOCAL_TOKEN),
            backend.sessionRepository.currentSession(),
        )
    }
}
