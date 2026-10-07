package com.fpmislata.prestecs.core.config

data class AppConfig(
    val defaultEnvironment: Environment,
    /** Debug builds only: lets the login screen pick the backend. */
    val canSwitchEnvironment: Boolean,
)
