package com.fpmislata.prestecs.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Text sent as `estudiant` for a scanned student card. Same rule as the web
 * (`parseEstudiantQR` in prestec.js):
 *
 * - New cards hold JSON `{"version", "nia", "name", "surname", "espec"}`,
 *   sent as `NIA - SURNAME, NAME`.
 * - Old cards and typed values are plain text, sent as they are (trimmed).
 *   So is JSON without `nia`, `name` and `surname`.
 */
object StudentQr {
    fun parse(raw: String): String {
        val text = raw.trim()
        if (!text.startsWith("{")) return text

        val json = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: return text

        fun field(name: String) = (json[name] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val nia = field("nia")
        val name = field("name")
        val surname = field("surname")
        if (nia.isEmpty() || name.isEmpty() || surname.isEmpty()) return text

        return "$nia${PortatilCode.SEPARATOR}$surname, $name"
    }
}
