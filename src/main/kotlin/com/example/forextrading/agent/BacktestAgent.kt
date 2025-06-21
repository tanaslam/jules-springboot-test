package com.example.forextrading.agent

import com.example.forextrading.model.StrategyParameters
import com.example.forextrading.model.TradingSignal
import com.example.forextrading.service.MarketDataService
import com.example.forextrading.service.SignalAnalysisService
import com.example.forextrading.service.StrategyConfigService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.ta4j.core.BarSeries
import java.time.ZoneId
// import java.time.temporal.ChronoUnit // Not directly used in the final logic

@Component
class BacktestAgent(
    private val marketDataService: MarketDataService,
    private val signalAnalysisService: SignalAnalysisService,
    private val strategyConfigService: StrategyConfigService,
    @Qualifier("applicationScope") private val coroutineScope: CoroutineScope,
    @Value("\${backtesting.daysToBacktest:30}") private val daysToBacktest: Int,
    @Value("\${trading.defaults.interval:15min}") private val defaultInterval: String,
    @Value("\${trading.defaults.symbols}") private val defaultSymbols: List<String>,
    @Value("\${scheduling.backtestCron}") private val backtestCron: String,

    // Parameter variants from application.yml
    @Value("\${backtesting.parameterVariants.rsiThresholds}")
    private val rsiThresholdVariantsStrings: List<String>, // e.g., ["30,70", "35,65"]

    @Value("\${backtesting.parameterVariants.smaPeriods}")
    private val smaPeriodVariantsStrings: List<String>, // e.g., ["10,30", "15,40"]

    @Value("\${trading.strategy.fibRetracementLevels}") // Injected as List<Double>
    private val configuredFibLevels: List<Double>
) {
    private val logger = LoggerFactory.getLogger(BacktestAgent::class.java)

    data class BacktestResult(
        val parameters: StrategyParameters,
        val signalsCount: Int, // Just count for brevity, full list can be large
        val winRate: Double,
        val trades: Int
    )

    @Scheduled(cron = "\${scheduling.backtestCron}")
    fun runBacktestScheduled() {
        logger.info("BacktestAgent triggered by cron: [{}]. Starting backtesting process.", backtestCron)
        // Launch the suspend function in the provided CoroutineScope
        coroutineScope.launch {
            try {
                performBacktesting()
            } catch (e: Exception) {
                logger.error("Error during scheduled backtest execution: {}", e.message, e)
            }
        }
    }

    suspend fun performBacktesting() {
        if (defaultSymbols.isEmpty()) {
            logger.warn("No default symbols configured for backtesting. Skipping.")
            return
        }
        if (!::configuredFibLevels.isInitialized || configuredFibLevels.isEmpty()) {
            logger.error("Fixed Fibonacci levels not loaded or empty. Check 'trading.strategy.fibRetracementLevels' in config. Skipping backtest.")
            return
        }

        logger.info("Starting backtest for symbols: {}, interval: {}, days: {}", defaultSymbols, defaultInterval, daysToBacktest)

        // Generate all parameter combinations
        val strategyVariants = mutableListOf<StrategyParameters>()
        try {
            val parsedRsiThresholds = rsiThresholdVariantsStrings.map {
                val parts = it.split(",").map(String::toInt)
                if (parts.size != 2) throw IllegalArgumentException("RSI threshold variant '$it' must have 2 parts")
                parts
            }
            val parsedSmaPeriods = smaPeriodVariantsStrings.map {
                val parts = it.split(",").map(String::toInt)
                if (parts.size != 2) throw IllegalArgumentException("SMA period variant '$it' must have 2 parts")
                parts
            }

            for (rsiPair in parsedRsiThresholds) {
                for (smaPair in parsedSmaPeriods) {
                     strategyVariants.add(
                        StrategyParameters(
                            rsiBuyThreshold = rsiPair[0],
                            rsiSellThreshold = rsiPair[1],
                            smaShortPeriod = smaPair[0],
                            smaLongPeriod = smaPair[1],
                            fibRetracementLevels = configuredFibLevels // Using directly injected List<Double>
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing parameter variants from application.yml: {}. Check backtesting.parameterVariants configuration.", e.message, e)
            return
        }


        if (strategyVariants.isEmpty()){
            logger.warn("No strategy variants generated for backtesting. Check config or parsing logic.")
            return
        }
        logger.info("Generated {} strategy variants for backtesting.", strategyVariants.size)

        val targetSymbol = defaultSymbols[0] // Using the first symbol for backtesting

        val intervalMinutes = defaultInterval.replace("min", "").toIntOrNull() ?: run {
            logger.error("Invalid interval format for {}. Cannot calculate bars per day. Defaulting to 15.", defaultInterval)
            15
        }
        val barsPerDay = (24 * 60) / intervalMinutes
        val outputSize = barsPerDay * daysToBacktest + 200 // +200 for indicator warm-up

        logger.info("Fetching historical data for {} (approx. {} bars for {} days at {}min interval)...", targetSymbol, outputSize, daysToBacktest, intervalMinutes)
        val historicalData = marketDataService.fetchAndPrepareBarSeries(targetSymbol, defaultInterval, outputSize)

        if (historicalData.isEmpty || historicalData.barCount < (outputSize * 0.8)) { // Check if we got a reasonable amount of data
            logger.error("Insufficient historical data for {} (got {} bars, needed ~{}). Skipping backtest.", targetSymbol, historicalData.barCount, outputSize)
            return
        }
        logger.info("Historical data fetched for {}: {} bars.", targetSymbol, historicalData.barCount)

        val results = coroutineScope {
            strategyVariants.map { params ->
                async {
                    logger.debug("Backtesting with params: {}", params)
                    val signals = signalAnalysisService.generateSignals(targetSymbol, historicalData, params)
                    val (wins, totalTrades) = simulateTradesAndGetWinRate(historicalData, signals)
                    val winRate = if (totalTrades > 0) wins.toDouble() / totalTrades else 0.0
                    logger.info("Params: {} -> Signals: {}, Trades: {}, Wins: {}, WinRate: {:.2f}%", params, signals.size, totalTrades, wins, winRate * 100)
                    BacktestResult(params, signals.size, winRate, totalTrades)
                }
            }.awaitAll()
        }

        val bestResult = results.filter { it.trades > 5 }.maxByOrNull { it.winRate } // Require some trades for significance

        if (bestResult != null && bestResult.winRate > 0.0) { // Ensure there's a meaningful best result
            logger.info(
                "Backtesting complete. Optimal parameters found for {}: {} (Win Rate: {:.2f}%, Trades: {}, Signals: {}). Updating strategy configuration.",
                targetSymbol, bestResult.parameters, bestResult.winRate * 100, bestResult.trades, bestResult.signalsCount
            )
            strategyConfigService.updateParameters(bestResult.parameters)
        } else {
            logger.warn("Backtesting did not yield a conclusive positive result (e.g. win rate > 0% with >5 trades). Current strategy parameters will not be changed.")
        }
    }

    private fun simulateTradesAndGetWinRate(
        series: BarSeries,
        signals: List<TradingSignal>
    ): Pair<Int, Int> { // Returns (wins, totalTrades)
        var wins = 0
        var totalTrades = 0

        if (series.isEmpty) return Pair(0, 0)
        val seriesZone = series.getBar(0).endTime.zone // Get ZoneId from the series

        for (signal in signals) {
            val signalTimestamp = signal.timestamp
            // Find the bar index that CONTAINS the signal's timestamp (signal occurs during this bar or at its open)
            // More accurately, a signal is generated based on the close of a bar, so it applies to the *next* bar.
            // Let's find the bar whose *end time* is the signal's timestamp.
            val signalBarIndex = series.barData.indexOfFirst { it.endTime.toInstant() == signalTimestamp }

            if (signalBarIndex < 0 || signalBarIndex == series.endIndex) {
                 // logger.trace("Signal timestamp {} not found precisely or is on last bar. Attempting nearest.", signalTimestamp)
                 // Fallback: find first bar whose beginTime is >= signal timestamp
                 // val fallbackIndex = series.barData.indexOfFirst { !it.beginTime.toInstant().isBefore(signalTimestamp) }
                 // if(fallbackIndex < 0 || fallbackIndex == series.endIndex) continue
                 // signalBarIndex = fallbackIndex
                continue // If signal timestamp doesn't align or is too late.
            }

            // Entry is assumed at the open of the bar *after* the signal bar.
            val entryBarIndex = signalBarIndex + 1
            if (entryBarIndex > series.endIndex) continue // Not enough data to enter trade

            totalTrades++

            // Look ahead N bars for outcome
            val lookAheadBars = 20 // How many bars to wait for TP/SL; This should ideally be dynamic or very long.
            var tradeOutcomeFound = false

            for (i in 0 until lookAheadBars) {
                val currentEvalBarIndex = entryBarIndex + i
                if (currentEvalBarIndex > series.endIndex) break // End of series

                val currentBar = series.getBar(currentEvalBarIndex)
                val highPrice = currentBar.highPrice.doubleValue()
                val lowPrice = currentBar.lowPrice.doubleValue()

                if (signal.direction == "BUY") {
                    if (lowPrice <= signal.stopLoss) { // SL hit first (check SL before TP on same bar)
                        tradeOutcomeFound = true
                        break
                    }
                    if (highPrice >= signal.takeProfit) {
                        wins++
                        tradeOutcomeFound = true
                        break
                    }
                } else if (signal.direction == "SELL") {
                    if (highPrice >= signal.stopLoss) { // SL hit first
                        tradeOutcomeFound = true
                        break
                    }
                    if (lowPrice <= signal.takeProfit) {
                        wins++
                        tradeOutcomeFound = true
                        break
                    }
                }
            }
            // If no TP/SL hit within lookAheadBars, it's counted as a loss or undecided (not a win here).
        }
        return Pair(wins, totalTrades)
    }
}
