package com.example.forextrading.model

data class StrategyParameters(
    val rsiBuyThreshold: Int = 30, // Default value, can be overridden by config
    val rsiSellThreshold: Int = 70, // Default value
    val smaShortPeriod: Int = 20, // Default value
    val smaLongPeriod: Int = 50, // Default value
    val fibRetracementLevels: List<Double> = listOf(0.382, 0.5, 0.618) // Default values
)
