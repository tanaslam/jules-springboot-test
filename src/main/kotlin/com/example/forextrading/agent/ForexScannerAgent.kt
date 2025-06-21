package com.example.forextrading.agent

import com.example.forextrading.model.TradingSignal
import com.example.forextrading.service.CsvExporterService
import com.example.forextrading.service.MarketDataService
import com.example.forextrading.service.SignalAnalysisService
import com.example.forextrading.service.StrategyConfigService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component // Using @Component as a general Spring bean
                                             // If Spring AI has a specific @Agent annotation, it should be used.

@Component // Or a more specific Spring AI Agent annotation if available
class ForexScannerAgent(
    private val marketDataService: MarketDataService,
    private val signalAnalysisService: SignalAnalysisService,
    private val strategyConfigService: StrategyConfigService,
    private val csvExporterService: CsvExporterService
) {
    private val logger = LoggerFactory.getLogger(ForexScannerAgent::class.java)

    // This method signature might need to adapt to Spring AI's MCP agent calling conventions.
    // It might return a specific Spring AI Message type or be an @AiMessageHandler.
    // For now, it takes a map and returns a list of signals.
    suspend fun scan(context: Map<String, Any>): List<TradingSignal> {
        // Safely extract and validate context parameters
        val symbols = context["symbols"] as? List<*> // Use List<*> for initial cast
        val interval = context["interval"] as? String

        if (symbols == null || symbols.any { it !is String } || interval == null) {
            logger.error("Invalid context for ForexScannerAgent: 'symbols' must be a List<String> and 'interval' must be a String. Context: {}", context)
            return emptyList()
        }

        @Suppress("UNCHECKED_CAST")
        val validSymbols = symbols as List<String>

        val currentStrategyParams = strategyConfigService.getCurrentParameters()
        // Output size for fetching historical data
        // Max of (RSI period, SMA short, SMA long) + (Fib lookback period, e.g., SMA long * 2) + some buffer
        // RSI is 14. Fib lookback is params.smaLongPeriod * 2
        val indicatorLookback = maxOf(14, currentStrategyParams.smaShortPeriod, currentStrategyParams.smaLongPeriod)
        val fibonacciLookback = currentStrategyParams.smaLongPeriod * 2
        val outputSize = indicatorLookback + fibonacciLookback + 50 // Added buffer

        logger.info("ForexScannerAgent activated. Scanning symbols: {}, interval: {}, outputSize: {}", validSymbols, interval, outputSize)
        logger.debug("Using strategy parameters: {}", currentStrategyParams)

        val allSignals = mutableListOf<TradingSignal>()

        // Process symbols concurrently using coroutines
        coroutineScope {
            val deferredSignals = validSymbols.map { symbol ->
                async {
                    try {
                        logger.debug("Fetching market data for symbol: {}", symbol)
                        val barSeries = marketDataService.fetchAndPrepareBarSeries(symbol, interval, outputSize)

                        // Check if barSeries is not null and has enough data
                        // SignalAnalysisService also checks, but good to have an early exit
                        if (barSeries.isEmpty || barSeries.barCount < currentStrategyParams.smaLongPeriod || barSeries.barCount < 14) {
                             logger.warn("Insufficient data for symbol {} ({} bars) after fetching. Required at least Max({}, 14) bars. Skipping analysis.",
                                symbol, barSeries.barCount, currentStrategyParams.smaLongPeriod)
                            return@async emptyList<TradingSignal>()
                        }

                        logger.debug("Generating signals for symbol: {}", symbol)
                        val signalsForSymbol = signalAnalysisService.generateSignals(
                            symbol = symbol,
                            barSeries = barSeries,
                            params = currentStrategyParams
                            // Risk/Reward Ratio is defaulted in SignalAnalysisService
                        )

                        if (signalsForSymbol.isNotEmpty()) {
                            logger.info("Found {} signal(s) for symbol {}", signalsForSymbol.size, symbol)
                            csvExporterService.logSignals(signalsForSymbol) // Log signals to CSV
                        }
                        signalsForSymbol
                    } catch (e: Exception) {
                        logger.error("Error processing symbol {}: {}", symbol, e.message, e)
                        emptyList<TradingSignal>() // Return empty list for this symbol on error
                    }
                }
            }
            // Collect signals from all coroutines
            deferredSignals.awaitAll().forEach { signalsList ->
                allSignals.addAll(signalsList)
            }
        }

        logger.info("ForexScannerAgent scan complete. Total signals generated: {}", allSignals.size)
        return allSignals
    }
}
