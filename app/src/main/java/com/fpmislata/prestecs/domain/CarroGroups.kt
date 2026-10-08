package com.fpmislata.prestecs.domain

import com.fpmislata.prestecs.data.api.dto.Estat
import com.fpmislata.prestecs.data.api.dto.PrestecDto
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** An open loan with how many whole days ago it was lent (0 = today). */
data class OpenLoan(val prestec: PrestecDto, val ageDays: Int) {
    val isOverdue: Boolean get() = prestec.estat == Estat.NO_RETORNAT

    /** Lowercase text the search matches against. Same fields as the web. */
    val searchText: String = listOf(
        prestec.carro,
        prestec.portatil,
        prestec.estudiant,
        prestec.prestecProfessor.orEmpty(),
    )
        .joinToString(" ")
        .lowercase()
}

/** Open loans of one carro, oldest first. */
data class CarroGroup(val carro: String, val loans: List<OpenLoan>) {
    /** Lent today. */
    val active: Int get() = loans.count { !it.isOverdue }

    /** Lent on a previous day and not returned. */
    val overdue: Int get() = loans.count { it.isOverdue }
}

/**
 * The home screen's cards, as on the web: one group per carro in natural
 * alphabetical order ("2A1" before "10B"), the oldest loan first inside each.
 * Returned loans are left out: they only appear in the history.
 */
fun groupByCarro(prestecs: List<PrestecDto>, today: LocalDate): List<CarroGroup> = prestecs
    .filter { it.estat != Estat.RETORNAT }
    .map { OpenLoan(it, ageDays(it.prestecData, today)) }
    .groupBy { it.prestec.carro }
    .map { (carro, loans) ->
        CarroGroup(
            carro = carro,
            loans = loans.sortedWith(compareBy<OpenLoan> { it.prestec.prestecData }.thenBy { it.prestec.id }),
        )
    }
    .sortedWith { a, b -> naturalCompare(a.carro, b.carro) }

/**
 * Narrows [groups] like the web search: a loan stays if it matches [query]
 * (case-insensitive, anywhere in carro, laptop, student or teacher) and, with
 * [onlyOverdue], if it is not returned from a previous day. Groups left empty
 * disappear.
 */
fun filterGroups(groups: List<CarroGroup>, query: String, onlyOverdue: Boolean): List<CarroGroup> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty() && !onlyOverdue) return groups
    return groups.mapNotNull { group ->
        val loans = group.loans.filter {
            (needle.isEmpty() || needle in it.searchText) && (!onlyOverdue || it.isOverdue)
        }
        if (loans.isEmpty()) null else group.copy(loans = loans)
    }
}

/** Whole days between the lending day and [today], never negative. */
private fun ageDays(prestecData: String, today: LocalDate): Int {
    val lent = runCatching { LocalDate.parse(prestecData.take(DATE_LENGTH)) }.getOrNull() ?: return 0
    return ChronoUnit.DAYS.between(lent, today).toInt().coerceAtLeast(0)
}

private const val DATE_LENGTH = 10

private val CHUNK = Regex("\\d+|\\D+")

/** Case-insensitive order where digit runs compare as numbers. */
private fun naturalCompare(a: String, b: String): Int {
    val left = CHUNK.findAll(a).map { it.value }.toList()
    val right = CHUNK.findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(left.size, right.size)) {
        val x = left[i]
        val y = right[i]
        val result = if (x[0].isDigit() && y[0].isDigit()) {
            x.toBigInteger().compareTo(y.toBigInteger())
        } else {
            x.compareTo(y, ignoreCase = true)
        }
        if (result != 0) return result
    }
    return left.size.compareTo(right.size)
}
