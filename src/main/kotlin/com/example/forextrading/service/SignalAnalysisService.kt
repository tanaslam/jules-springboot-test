package com.example.forextrading.service

import com.example.forextrading.model.StrategyParameters
import com.example.forextrading.model.TradingSignal
import org.ta4j.core.BarSeries
import java.time.Instant // Not directly used in interface, but often related

interface SignalAnalysisService {
    fun generateSignals(
        symbol: String,
        barSeries: BarSeries,
        params: StrategyParameters,
        riskRewardRatio: Pair<Int, Int> = Pair(1, 2) // Default 1:2 R:R
    ): List<TradingSignal>
}
