package com.fpmislata.prestecs.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

private val API_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * Formats an API date (`YYYY-MM-DD hh:mm:ss`, already Madrid time) for the
 * app's language, without converting time zones. Unparseable values are shown
 * as they come.
 */
@Composable
fun formatApiDate(value: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
    }
    return try {
        LocalDateTime.parse(value, API_FORMAT).format(formatter)
    } catch (e: DateTimeParseException) {
        value
    }
}
