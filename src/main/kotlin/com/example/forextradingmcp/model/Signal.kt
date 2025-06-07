package com.example.forextradingmcp.model

import java.math.BigDecimal // Use BigDecimal for precision

enum class TradeDirection {
    LONG, SHORT
}

data class Signal(
    val pair: String, // e.g., "EUR/USD"
    val direction: TradeDirection,
    val entryPrice: BigDecimal,
    val stopLoss: BigDecimal,
    val takeProfit: BigDecimal,
    val riskRewardRatio: BigDecimal,
    val timestamp: String // Timestamp of the bar that generated the signal
)
