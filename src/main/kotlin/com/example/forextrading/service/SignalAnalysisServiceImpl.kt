package com.example.forextrading.service

import com.example.forextrading.model.StrategyParameters
import com.example.forextrading.model.TradingSignal
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.ta4j.core.BarSeries
// import org.ta4j.core.indicators.EMAIndicator // Not used in the provided logic
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
// import org.ta4j.core.num.Num // Not directly used, but getValue returns it.
// import java.time.Instant // Not directly used here, but in TradingSignal via ZonedDateTime.toInstant()
import kotlin.math.abs

@Service
class SignalAnalysisServiceImpl : SignalAnalysisService {

    private val logger = LoggerFactory.getLogger(SignalAnalysisServiceImpl::class.java)

    override fun generateSignals(
        symbol: String,
        barSeries: BarSeries,
        params: StrategyParameters,
        riskRewardRatio: Pair<Int, Int>
    ): List<TradingSignal> {
        if (barSeries.isEmpty || barSeries.barCount < params.smaLongPeriod || barSeries.barCount < 14) { // 14 for RSI
            logger.warn(
                "BarSeries for {} is empty or has insufficient data ({} bars) for analysis. Required: Max({}, 14) for SMA long period and RSI. Skipping signal generation.",
                symbol, barSeries.barCount, params.smaLongPeriod
            )
            return emptyList()
        }

        val closePrice = ClosePriceIndicator(barSeries)

        // RSI
        val rsiIndicator = RSIIndicator(closePrice, 14) // Standard 14-period RSI

        // SMAs
        val smaShort = SMAIndicator(closePrice, params.smaShortPeriod)
        val smaLong = SMAIndicator(closePrice, params.smaLongPeriod)

        val signals = mutableListOf<TradingSignal>()

        val currentIndex = barSeries.endIndex
        // Ensure all indicators have values at currentIndex
        if (currentIndex < params.smaLongPeriod -1 || currentIndex < params.smaShortPeriod -1 || currentIndex < 14 -1) {
             logger.warn("Not enough data for all indicators at current index {} for symbol {}. SMA Short requires {}, SMA Long requires {}, RSI requires {}. Bar count is {}. Skipping.",
                currentIndex, symbol, params.smaShortPeriod, params.smaLongPeriod, 14, barSeries.barCount)
            return emptyList()
        }


        val currentPrice = closePrice.getValue(currentIndex).doubleValue()
        val currentRsi = rsiIndicator.getValue(currentIndex).doubleValue()
        val currentSmaShort = smaShort.getValue(currentIndex).doubleValue()
        val currentSmaLong = smaLong.getValue(currentIndex).doubleValue()

        // Fibonacci Retracement Levels
        val lookbackPeriodForSwing = params.smaLongPeriod * 2
        if (currentIndex < lookbackPeriodForSwing -1) { // Adjusted for 0-based index
            logger.warn(
                "Not enough data ({} bars available, need {} bars) for Fibonacci swing detection on symbol {}. Current index {}. Skipping.",
                barSeries.barCount, lookbackPeriodForSwing, symbol, currentIndex
            )
            return emptyList()
        }

        // Determine swing high/low over the lookback period up to the bar *before* current
        // because the current bar is what we are evaluating against the Fib levels formed by prior swings.
        // However, the problem description implies using current price against Fib levels derived from a recent swing.
        // Let's stick to the problem's description for swing definition window.
        val swingWindowStartIndex = currentIndex - lookbackPeriodForSwing + 1
        val swingWindowEndIndex = currentIndex

        var swingHigh = Double.MIN_VALUE
        var swingLow = Double.MAX_VALUE
        var swingHighIndex = -1
        var swingLowIndex = -1

        for (i in swingWindowStartIndex..swingWindowEndIndex) {
            val high = barSeries.getBar(i).highPrice.doubleValue()
            val low = barSeries.getBar(i).lowPrice.doubleValue()
            if (high > swingHigh) {
                swingHigh = high
                // swingHighIndex = i // Not strictly needed for Fib calculation if only values are used
            }
            if (low < swingLow) {
                swingLow = low
                // swingLowIndex = i // Not strictly needed
            }
        }

        // The problem description implies swingLowIndex and swingHighIndex are used to determine trend direction
        // for Fib application. This requires finding the *indices* of actual min/max in the window.
        // Re-iterating for indices:
        swingHigh = Double.MIN_VALUE
        swingLow = Double.MAX_VALUE
        for (i in swingWindowStartIndex..swingWindowEndIndex) {
            val high = barSeries.getBar(i).highPrice.doubleValue()
            val low = barSeries.getBar(i).lowPrice.doubleValue()
            if (high > swingHigh) {
                swingHigh = high
                swingHighIndex = i
            }
            if (low < swingLow) {
                swingLow = low
                swingLowIndex = i
            }
        }


        if (swingHighIndex == -1 || swingLowIndex == -1 || swingHigh == swingLow) {
            logger.warn(
                "Could not determine a valid swing high/low for Fibonacci on symbol {}. Swing High: {}, Swing Low: {}. Window: {} to {}. Skipping.",
                symbol, swingHigh, swingLow, swingWindowStartIndex, swingWindowEndIndex
            )
            return emptyList()
        }

        val targetFibLevel = 0.618 // As per requirement

        // BUY Signal Condition: RSI oversold, short SMA above long SMA, price near Fib retracement of an UPTREND
        if (currentRsi < params.rsiBuyThreshold && currentSmaShort > currentSmaLong) {
            if (swingLowIndex < swingHighIndex) { // Confirmed uptrend move (low then high)
                val fibRetracementPrice = swingHigh - (swingHigh - swingLow) * targetFibLevel
                if (isPriceNear(currentPrice, fibRetracementPrice, barSeries.getBar(currentIndex).lowPrice.doubleValue(), true)) {
                    val entryPrice = currentPrice // Or barSeries.getBar(currentIndex).closePrice.doubleValue()
                    val stopLoss = entryPrice * (1 - 0.01) // Simple 1% SL
                    val riskAmount = entryPrice - stopLoss
                    val takeProfit = entryPrice + riskAmount * riskRewardRatio.second / riskRewardRatio.first

                    signals.add(
                        TradingSignal(
                            timestamp = barSeries.getBar(currentIndex).endTime.toInstant(),
                            symbol = symbol,
                            direction = "BUY",
                            entry = entryPrice,
                            stopLoss = stopLoss,
                            takeProfit = takeProfit,
                            riskRewardRatio = "${riskRewardRatio.first}:${riskRewardRatio.second}",
                            rsi = currentRsi,
                            sma20 = currentSmaShort, // Value of SMA with params.smaShortPeriod
                            sma50 = currentSmaLong,  // Value of SMA with params.smaLongPeriod
                            fibLevel = targetFibLevel,
                            outcome = null
                        )
                    )
                    logger.info(
                        "BUY signal generated for {}: Entry={}, SL={}, TP={}, RSI={}, Configured SMA Short/Long={}/{}, FibLevel={}",
                        symbol, entryPrice, stopLoss, takeProfit, currentRsi, currentSmaShort, currentSmaLong, targetFibLevel
                    )
                }
            }
        }

        // SELL Signal Condition: RSI overbought, short SMA below long SMA, price near Fib retracement of a DOWNTREND
        if (currentRsi > params.rsiSellThreshold && currentSmaShort < currentSmaLong) {
            if (swingHighIndex < swingLowIndex) { // Confirmed downtrend move (high then low)
                val fibRetracementPrice = swingLow + (swingHigh - swingLow) * targetFibLevel
                if (isPriceNear(currentPrice, fibRetracementPrice, barSeries.getBar(currentIndex).highPrice.doubleValue(), false)) {
                    val entryPrice = currentPrice // Or barSeries.getBar(currentIndex).closePrice.doubleValue()
                    val stopLoss = entryPrice * (1 + 0.01) // Simple 1% SL
                    val riskAmount = stopLoss - entryPrice
                    val takeProfit = entryPrice - riskAmount * riskRewardRatio.second / riskRewardRatio.first

                    signals.add(
                        TradingSignal(
                            timestamp = barSeries.getBar(currentIndex).endTime.toInstant(),
                            symbol = symbol,
                            direction = "SELL",
                            entry = entryPrice,
                            stopLoss = stopLoss,
                            takeProfit = takeProfit,
                            riskRewardRatio = "${riskRewardRatio.first}:${riskRewardRatio.second}",
                            rsi = currentRsi,
                            sma20 = currentSmaShort, // Value of SMA with params.smaShortPeriod
                            sma50 = currentSmaLong,  // Value of SMA with params.smaLongPeriod
                            fibLevel = targetFibLevel,
                            outcome = null
                        )
                    )
                    logger.info(
                        "SELL signal generated for {}: Entry={}, SL={}, TP={}, RSI={}, Configured SMA Short/Long={}/{}, FibLevel={}",
                        symbol, entryPrice, stopLoss, takeProfit, currentRsi, currentSmaShort, currentSmaLong, targetFibLevel
                    )
                }
            }
        }
        return signals
    }

    /**
     * Checks if the current market price is "near" a target Fibonacci level.
     * @param currentClose The current closing price.
     * @param targetFibPrice The Fibonacci level price.
     * @param relevantBarExtremityPrice The low price of the current bar for a BUY, or high for a SELL.
     * @param isBuySignal True if checking for a buy signal, false for a sell signal.
     */
    private fun isPriceNear(currentClose: Double, targetFibPrice: Double, relevantBarExtremityPrice: Double, isBuySignal: Boolean): Boolean {
        val tolerance = targetFibPrice * 0.001 // 0.1% tolerance, example

        return if (isBuySignal) { // Potential BUY: current price is near or just above Fib after dipping to it
            // Low of the bar went to or below Fib, and close is still near Fib
            relevantBarExtremityPrice <= targetFibPrice + tolerance && currentClose >= targetFibPrice - tolerance && currentClose <= targetFibPrice + (targetFibPrice * 0.005) // close is not too far above
        } else { // Potential SELL: current price is near or just below Fib after spiking to it
            // High of the bar went to or above Fib, and close is still near Fib
            relevantBarExtremityPrice >= targetFibPrice - tolerance && currentClose <= targetFibPrice + tolerance && currentClose >= targetFibPrice - (targetFibPrice * 0.005) // close is not too far below
        }
    }
}
