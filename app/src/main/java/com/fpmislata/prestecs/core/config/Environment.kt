package com.fpmislata.prestecs.core.config

/**
 * Backend the app talks to. Release builds always use [PROD]; debug builds can
 * switch from the login screen.
 *
 * [moodleUrl] is the Moodle site that issues tokens (`login/token.php`). It is
 * null for [LOCAL]: the local backend has no Moodle and accepts only
 * [LOCAL_TOKEN] (`MockMoodleProvider` in fpmislata-aplicacions).
 */
enum class Environment(val apiUrl: String, val moodleUrl: String?) {
    PROD(
        apiUrl = "https://www.fpmislata.com/moodle/fpmislata/aplicacions/prestecs/api/",
        moodleUrl = MOODLE_URL,
    ),
    STAGING(
        apiUrl = "https://www.fpmislata.com/moodle/fpmislata/aplicacions_test/prestecs/api/",
        moodleUrl = MOODLE_URL,
    ),

    // 10.0.2.2 is the host machine as seen from the Android emulator, where
    // `docker compose up aplicacions` serves the backend on port 8000.
    LOCAL(
        apiUrl = "http://10.0.2.2:8000/www/prestecs/api/",
        moodleUrl = null,
    ),
    ;

    companion object {
        const val LOCAL_TOKEN = "mock-ws-token"
    }
}

private const val MOODLE_URL = "https://www.fpmislata.com/moodle/"
