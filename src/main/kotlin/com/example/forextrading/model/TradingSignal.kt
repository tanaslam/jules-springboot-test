package com.example.forextrading.model

import java.time.Instant

data class TradingSignal(
    val timestamp: Instant,
    val symbol: String,
    val direction: String, // "BUY" or "SELL"
    val entry: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val riskRewardRatio: String, // e.g., "1:2"
    val rsi: Double,
    val sma20: Double,
    val sma50: Double,
    val fibLevel: Double, // The Fibonacci level that triggered the signal
    val outcome: String? = null // e.g., "TP_HIT", "SL_HIT", "MANUAL_CLOSE", initially null
)
