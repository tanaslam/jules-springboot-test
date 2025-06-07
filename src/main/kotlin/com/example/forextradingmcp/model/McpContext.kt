package com.example.forextradingmcp.model

// Default values as per plan step 4 for MarketSchedulerAgent
data class StrategyParameters(
    val rsiPeriod: Int = 14,
    val rsiLow: Int = 30,
    val rsiHigh: Int = 70,
    val smaShortPeriod: Int = 20,
    val smaLongPeriod: Int = 50,
    val stopLossLookBackPeriod: Int = 10, // Default for SL calculation
    val minRiskRewardRatio: Double = 2.0
)

data class McpContext(
    val currencyPairs: List<String>,
    val interval: String, // e.g., "15min"
    val outputSize: Int = 70, // Number of bars to fetch for analysis
    val strategyParameters: StrategyParameters = StrategyParameters()
)
