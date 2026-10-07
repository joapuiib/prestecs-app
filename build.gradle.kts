plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless)
}

// ./gradlew spotlessApply formats; spotlessCheck fails on unformatted code.
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    // Spotless doesn't read .editorconfig reliably, so the ktlint settings are
    // passed here. Keep them in sync with .editorconfig (used by the IDE).
    val ktlintSettings = mapOf(
        "ktlint_code_style" to "android_studio",
        "max_line_length" to 120,
        // Composables are named like types (LoginScreen, EstatBadge...).
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        // Trailing commas: one-line diffs when adding arguments or entries.
        "ij_kotlin_allow_trailing_comma" to true,
        "ij_kotlin_allow_trailing_comma_on_call_site" to true,
    )
    kotlin {
        target("app/src/**/*.kt")
        ktlint(ktlintVersion).editorConfigOverride(ktlintSettings)
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(ktlintVersion).editorConfigOverride(ktlintSettings)
    }
}
