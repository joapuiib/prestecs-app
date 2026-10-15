package com.fpmislata.prestecs.domain

/**
 * Laptop code, as printed in the QR sticker of each laptop: `CARRO - PORTATIL`,
 * e.g. `C1 - P01`. Same rule as the backend (`PrestecPortatilCode`).
 */
object PortatilCode {
    const val SEPARATOR = " - "

    /** Longest value the API accepts for laptop and student fields. */
    const val MAX_LENGTH = 100

    fun isValid(code: String): Boolean = SEPARATOR in code
}
