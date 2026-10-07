package com.fpmislata.prestecs.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Response of Moodle's `login/token.php`. Always HTTP 200: on success it has
 * [token], on failure [error] and [errorcode] (e.g. `invalidlogin`).
 */
@Serializable
data class MoodleTokenDto(
    val token: String? = null,
    val error: String? = null,
    val errorcode: String? = null,
)
