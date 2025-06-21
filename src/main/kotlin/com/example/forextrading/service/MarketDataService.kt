package com.example.forextrading.service

import com.example.forextrading.model.OhlcvData
import org.ta4j.core.BarSeries

interface MarketDataService {
    suspend fun fetchOhlcvData(symbol: String, interval: String, outputSize: Int): List<OhlcvData>
    suspend fun fetchAndPrepareBarSeries(symbol: String, interval: String, outputSize: Int): BarSeries
}
