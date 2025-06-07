package com.example.forextradingmcp.agent

import com.example.forextradingmcp.model.McpContext
import com.example.forextradingmcp.model.Signal
import com.example.forextradingmcp.service.MarketDataService
import com.example.forextradingmcp.service.SignalAnalysisService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service // Using @Service as per example, can be @Component too

@Service
class ForexScannerAgent(
    private val marketDataService: MarketDataService,
    private val signalAnalysisService: SignalAnalysisService
) {
    private val logger = LoggerFactory.getLogger(ForexScannerAgent::class.java)

    fun scan(context: McpContext): List<Signal> {
        val allSignals = mutableListOf<Signal>()
        logger.info(
            "Starting forex scan. Context: {} currency pairs, interval={}, outputSize={}, strategyParams={}",
            context.currencyPairs.size, context.interval, context.outputSize, context.strategyParameters
        )

        for (pair in context.currencyPairs) {
            logger.info("Scanning pair: {}", pair)
            try {
                val barSeries = marketDataService.fetchMarketData(
                    symbol = pair,
                    interval = context.interval,
                    outputSize = context.outputSize
                )

                if (barSeries.isEmpty) {
                    logger.warn("No market data returned or bar series is empty for pair: {}. Skipping signal analysis.", pair)
                    continue
                }

                logger.info("Fetched {} bars for pair: {}. Analyzing for signals.", barSeries.barCount, pair)

                val params = context.strategyParameters
                val pairSignals = signalAnalysisService.generateSignals(
                    barSeries = barSeries,
                    rsiPeriod = params.rsiPeriod,
                    rsiLow = params.rsiLow,
                    rsiHigh = params.rsiHigh,
                    smaShortPeriod = params.smaShortPeriod,
                    smaLongPeriod = params.smaLongPeriod,
                    minRiskRewardRatio = params.minRiskRewardRatio,
                    stopLossLookBackPeriod = params.stopLossLookBackPeriod
                )

                if (pairSignals.isNotEmpty()) {
                    logger.info("Found {} signals for pair {}: {}", pairSignals.size, pair, pairSignals.map { it.direction })
                    allSignals.addAll(pairSignals)
                } else {
                    logger.info("No signals found for pair: {}", pair)
                }

            } catch (e: IllegalStateException) { // Catch API key not configured error specifically
                 logger.error("Critical error for pair {}: {}. Halting scan for this pair. Please configure the API key.", pair, e.message)
                 // Depending on desired behavior, we might re-throw or stop all scans
                 // For now, just log and continue to next pair as per requirement "continue to the next pair"
            }catch (e: Exception) {
                logger.error("Error processing pair {}: {}. Continuing to next pair.", pair, e.message, e)
                // Continue to the next pair as per requirements
            }
        }

        logger.info("Forex scan completed. Total signals found across all pairs: {}", allSignals.size)
        return allSignals
    }
}
