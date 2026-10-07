package com.fpmislata.prestecs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentQrTest {
    @Test
    fun `JSON card becomes NIA - SURNAME, NAME`() {
        val qr = """{"version":"0.1","nia":"12345678","name":"Maria","surname":"Garcia Puig","espec":"DAW"}"""

        assertEquals("12345678 - Garcia Puig, Maria", StudentQr.parse(qr))
    }

    @Test
    fun `numeric NIA is accepted`() {
        assertEquals("123 - Garcia, Maria", StudentQr.parse("""{"nia":123,"name":"Maria","surname":"Garcia"}"""))
    }

    @Test
    fun `old cards and typed text are kept, trimmed`() {
        assertEquals("12345678 MARIA GARCIA", StudentQr.parse("  12345678 MARIA GARCIA \n"))
    }

    @Test
    fun `JSON missing a field is kept as text`() {
        val qr = """{"nia":"12345678","name":"Maria"}"""

        assertEquals(qr, StudentQr.parse(qr))
    }

    @Test
    fun `broken JSON is kept as text`() {
        assertEquals("{nia: 1", StudentQr.parse("{nia: 1"))
    }

    @Test
    fun `laptop codes need the spaced dash`() {
        assertTrue(PortatilCode.isValid("C1 - P01"))
        assertFalse(PortatilCode.isValid("C1-P01"))
        assertFalse(PortatilCode.isValid("12345678 MARIA"))
    }
}
