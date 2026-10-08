package com.fpmislata.prestecs.domain

import com.fpmislata.prestecs.data.api.dto.Estat
import com.fpmislata.prestecs.data.api.dto.PrestecDto
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarroGroupsTest {
    private val today = LocalDate.of(2026, 10, 7)

    private fun loan(
        id: Long,
        carro: String,
        date: String = "2026-10-07 08:00:00",
        estat: Estat = Estat.PRESTAT,
        estudiant: String = "Estudiant $id",
        professor: String? = null,
    ) = PrestecDto(
        id = id,
        carro = carro,
        portatil = "$carro - P%02d".format(id),
        estudiant = estudiant,
        prestecData = date,
        prestecProfessor = professor,
        estat = estat,
    )

    @Test
    fun `carros are in natural alphabetical order`() {
        val groups = groupByCarro(listOf(loan(1, "10B"), loan(2, "2A1"), loan(3, "C1"), loan(4, "2a0")), today)

        assertEquals(listOf("2a0", "2A1", "10B", "C1"), groups.map { it.carro })
    }

    @Test
    fun `loans inside a carro are oldest first`() {
        val groups = groupByCarro(
            listOf(
                loan(1, "C1", date = "2026-10-07 09:00:00"),
                loan(2, "C1", date = "2026-10-05 10:00:00", estat = Estat.NO_RETORNAT),
                loan(3, "C1", date = "2026-10-07 08:00:00"),
            ),
            today,
        )

        assertEquals(listOf(2L, 3L, 1L), groups.single().loans.map { it.prestec.id })
    }

    @Test
    fun `returned loans are left out`() {
        val groups = groupByCarro(listOf(loan(1, "C1"), loan(2, "C2", estat = Estat.RETORNAT)), today)

        assertEquals(listOf("C1"), groups.map { it.carro })
    }

    @Test
    fun `counts split lent today from not returned`() {
        val group = groupByCarro(
            listOf(
                loan(1, "C1"),
                loan(2, "C1", date = "2026-10-06 08:00:00", estat = Estat.NO_RETORNAT),
                loan(3, "C1", date = "2026-10-01 08:00:00", estat = Estat.NO_RETORNAT),
            ),
            today,
        ).single()

        assertEquals(1, group.active)
        assertEquals(2, group.overdue)
    }

    @Test
    fun `age is whole days since the lending day`() {
        val loans = groupByCarro(
            listOf(
                loan(1, "C1", date = "2026-10-07 23:59:00"),
                loan(2, "C1", date = "2026-10-06 00:01:00", estat = Estat.NO_RETORNAT),
                loan(3, "C1", date = "2026-09-30 12:00:00", estat = Estat.NO_RETORNAT),
            ),
            today,
        ).single().loans.associate { it.prestec.id to it.ageDays }

        assertEquals(mapOf(1L to 0, 2L to 1, 3L to 7), loans)
    }

    @Test
    fun `a loan without a carro gets its own group`() {
        val groups = groupByCarro(listOf(loan(1, "")), today)

        assertEquals("", groups.single().carro)
    }

    @Test
    fun `search matches carro, laptop, student and teacher without case`() {
        val groups = groupByCarro(
            listOf(
                loan(1, "C1", estudiant = "1001 - Ferrer, Anna", professor = "Joan Puig"),
                loan(2, "C2", estudiant = "1002 - Soler, Marc"),
            ),
            today,
        )

        assertEquals(listOf("C2"), filterGroups(groups, "soler", onlyOverdue = false).map { it.carro })
        assertEquals(listOf("C1"), filterGroups(groups, " JOAN ", onlyOverdue = false).map { it.carro })
        assertEquals(listOf("C2"), filterGroups(groups, "c2 - p02", onlyOverdue = false).map { it.carro })
        assertEquals(2, filterGroups(groups, "c", onlyOverdue = false).size)
        assertTrue(filterGroups(groups, "zzz", onlyOverdue = false).isEmpty())
    }

    @Test
    fun `only overdue keeps not returned loans and drops empty groups`() {
        val groups = groupByCarro(
            listOf(
                loan(1, "C1"),
                loan(2, "C1", date = "2026-10-05 08:00:00", estat = Estat.NO_RETORNAT),
                loan(3, "C2"),
            ),
            today,
        )

        val overdue = filterGroups(groups, query = "", onlyOverdue = true)

        assertEquals(listOf("C1"), overdue.map { it.carro })
        assertEquals(listOf(2L), overdue.single().loans.map { it.prestec.id })
    }
}
