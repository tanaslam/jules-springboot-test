package com.example.forextrading.model

data class ScanRequest(
    val symbols: List<String>,
    val interval: String // e.g., "15min", "1h", "1day"
)
