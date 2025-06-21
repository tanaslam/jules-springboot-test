package com.example.forextrading.model

import java.time.Instant

data class OhlcvData(
    val timestamp: Instant,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long? = null // Volume can be optional
)
