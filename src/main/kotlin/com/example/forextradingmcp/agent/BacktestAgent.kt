package com.example.forextradingmcp.agent

import com.example.forextradingmcp.model.McpContext
import com.example.forextradingmcp.model.Signal
import com.example.forextradingmcp.service.MarketDataService
import com.example.forextradingmcp.service.SignalAnalysisService
import com.example.forextradingmcp.util.CsvExporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class BacktestAgent(
    private val marketDataService: MarketDataService,
    private val signalAnalysisService: SignalAnalysisService
    // CsvExporter is an object, so no injection needed
) {
    private val logger = LoggerFactory.getLogger(BacktestAgent::class.java)

    // Example: 1 month of 15-min data: (60/15 bars per hour) * 24 hours * 30 days = 2880 bars. Add buffer for indicators.
    private val defaultHistoricalDataOutputSize = 3000

    fun runBacktest(
        context: McpContext, // Contains pairs, interval, and strategy parameters
        historicalDataOutputSize: Int = defaultHistoricalDataOutputSize,
        outputDirectory: String = "backtest_results",
        outputFileNamePrefix: String = "backtest"
    ): List<Signal> {
        logger.info(
            "⏳ Starting backtest. Context: Pairs={}, Interval={}, StrategyParams={}. Data points per pair: {}. Output dir: {}",
            context.currencyPairs.joinToString(), context.interval, context.strategyParameters,
            historicalDataOutputSize, outputDirectory
        )
        val allSignals = mutableListOf<Signal>()
        val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        val runTimestamp = LocalDateTime.now().format(timestampFormat)

        for (pair in context.currencyPairs) {
            logger.info("➡️ Running backtest for pair: {}", pair)
            try {
                val barSeries = marketDataService.fetchMarketData(
                    symbol = pair,
                    interval = context.interval,
                    outputSize = historicalDataOutputSize
                )

                if (barSeries.isEmpty) {
                    logger.warn("⚠️ No historical data fetched for pair {}, skipping backtest for this pair.", pair)
                    continue
                }

                logger.info("📊 Fetched {} bars for pair {} for backtesting.", barSeries.barCount, pair)

                // Use strategy parameters from the provided context
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
                    logger.info("💡 Generated {} signals for pair {}:", pairSignals.size, pair)
                    pairSignals.forEach { signal -> logger.debug("  Signal for {}: {}", pair, signal) } // Changed to debug to avoid flooding logs
                    allSignals.addAll(pairSignals)

                    val fileName = "${outputFileNamePrefix}_${pair.replace("/", "-")}_${runTimestamp}_signals.csv"
                    // Log before exporting signals for a pair
                    logger.info("📄 Exporting {} signals for pair {} to file: {}", pairSignals.size, pair, fileName)
                    CsvExporter.exportSignals(pairSignals, outputDirectory, fileName)
                } else {
                    logger.info("➖ No signals generated for pair {} with current parameters.", pair)
                }

            } catch (e: IllegalStateException) { // Catch API key not configured error specifically
                 logger.error("🔑 Critical API Key error during backtest for pair {}: {}. Halting scan for this pair.", pair, e.message)
            } catch (e: Exception) {
                logger.error("❌ Error during backtest for pair {}: {}", pair, e.message, e)
                // Continue with the next pair
            }
        }

        logger.info("🏁 Backtest completed for all pairs. Total signals generated: {}", allSignals.size)

        // Placeholder for win-rate calculation and recalibration trigger
        if (allSignals.isEmpty() && context.currencyPairs.isNotEmpty()) {
            logger.warn(
                "🤔 No signals generated across all pairs for context: {}. Consider reviewing strategy parameters or market conditions.",
                context
            )
        } else if (allSignals.isNotEmpty()) {
            // This is where a more complex win-rate calculation would go if trade outcomes were simulated.
            // For now, we acknowledge that signals were produced.
            logger.info(
                "📊 Backtest produced {} signals. Further analysis (e.g., win rate) would require simulating trade execution based on these signals.",
                allSignals.size
            )
            // Example of what might be logged if win-rate was calculated:
            // val winRate = calculateWinRate(allSignals, context, historicalDataOutputSize) // Fictional method
            // logger.info("📉 Overall estimated win rate: {}%", winRate * 100) // Example using a relevant emoji
            // if (winRate < 0.5) { // Example threshold
            //    logger.warn("📉 Estimated win rate is below 50%. Consider recalibrating parameters: {}", context.strategyParameters)
            // }

            // Export all signals to a consolidated file as well
            val consolidatedFileName = "${outputFileNamePrefix}_ALL_PAIRS_${runTimestamp}_signals.csv"
            logger.info("📄 Exporting consolidated signals for all {} pairs to file: {}", context.currencyPairs.size, consolidatedFileName)
            CsvExporter.exportSignals(allSignals, outputDirectory, consolidatedFileName)
            logger.info("📑 Consolidated signals exported to {} directory, filename {}", outputDirectory, consolidatedFileName) // Changed emoji

        } else {
             logger.info("🏁 Backtest completed. No signals generated and no pairs were specified in the context.") // Added emoji
        }


        return allSignals
    }
    // A true win rate calculation would involve:
    // 1. Iterating through each signal.
    // 2. Using subsequent bars after the signal timestamp to see if TP or SL would be hit first.
    // 3. This requires access to the BarSeries data corresponding to each signal.
    //    It's a more involved process than what SignalAnalysisService currently does.
}
