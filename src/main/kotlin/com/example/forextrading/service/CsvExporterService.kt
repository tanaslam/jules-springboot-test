package com.example.forextrading.service

import com.example.forextrading.model.TradingSignal

interface CsvExporterService {
    fun logSignal(signal: TradingSignal)
    fun logSignals(signals: List<TradingSignal>)
    // Potentially add a method to log backtest outcomes if needed later
    // fun logBacktestOutcome(symbol: String, parameters: StrategyParameters, winRate: Double, drawdown: Double, etc...)
}
