package com.fpmislata.prestecs.core.network

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/** Debug: log request lines and status codes (no headers, no bodies). */
fun buildTypeInterceptors(): List<Interceptor> = listOf(
    HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
    },
)
