package com.example.forextradingmcp.service

import com.example.forextradingmcp.model.Signal
import com.example.forextradingmcp.model.TradeDirection
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.num.Num
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class SignalAnalysisService {

    private val logger = LoggerFactory.getLogger(SignalAnalysisService::class.java)

    fun generateSignals(
        barSeries: BarSeries,
        rsiPeriod: Int = 14,
        rsiLow: Int = 30,
        rsiHigh: Int = 70,
        smaShortPeriod: Int = 20,
        smaLongPeriod: Int = 50,
        minRiskRewardRatio: Double = 2.0,
        stopLossLookBackPeriod: Int = 10 // smaShortPeriod / 2, or a fixed value like 10
    ): List<Signal> {

        val signals = mutableListOf<Signal>()
        val seriesName = barSeries.name ?: "Unnamed Series"

        if (barSeries.barCount < smaLongPeriod + rsiPeriod) { // Ensure enough data for all indicators
            logger.warn(
                "⚠️ [{}] Not enough bars to generate signals. Required at least {} bars, but got {}.",
                seriesName, smaLongPeriod + rsiPeriod, barSeries.barCount
            )
            return signals
        }

        val closePrice = ClosePriceIndicator(barSeries)
        val rsiIndicator = RSIIndicator(closePrice, rsiPeriod)
        val smaShortIndicator = SMAIndicator(closePrice, smaShortPeriod)
        val smaLongIndicator = SMAIndicator(closePrice, smaLongPeriod)

        logger.info(
            "📊 [{}] Generating signals with params: RSI({}, {}, {}), SMA({}, {}), MinR:R {}, SL Lookback: {}",
            seriesName, rsiPeriod, rsiLow, rsiHigh, smaShortPeriod, smaLongPeriod, minRiskRewardRatio, stopLossLookBackPeriod
        )

        // Start from an index where all indicators have values.
        // RSI needs 'rsiPeriod' bars, SMA-long needs 'smaLongPeriod' bars.
        // Also, need i-1 for previous SMA values.
        val startIndex = maxOf(smaLongPeriod, rsiPeriod)

        for (i in startIndex until barSeries.barCount) {
            val currentBar = barSeries.getBar(i)
            val currentRsi = rsiIndicator.getValue(i).doubleValue()
            val currentSmaShort = smaShortIndicator.getValue(i).doubleValue()
            val currentSmaLong = smaLongIndicator.getValue(i).doubleValue()

            // Ensure previous bar's data is accessible
            if (i == 0) { // Should not happen due to startIndex logic, but as a safeguard
                logger.debug("➖ [{}] Skipping bar {} as it's the first bar in the series (after warm-up).", seriesName, i)
                continue
            }
            val prevSmaShort = smaShortIndicator.getValue(i - 1).doubleValue()
            val prevSmaLong = smaLongIndicator.getValue(i - 1).doubleValue()

            logger.debug(
                "🔍 [{}] Bar {}: Time={}, Close={}, RSI={}, SMA_Short={}, SMA_Long={}, Prev_SMA_Short={}, Prev_SMA_Long={}",
                seriesName, i, currentBar.endTime, currentBar.closePrice.doubleValue(),
                currentRsi, currentSmaShort, currentSmaLong, prevSmaShort, prevSmaLong
            )

            val entryPrice = currentBar.closePrice.toBigDecimal()
            var signal: Signal? = null

            // Buy Signal Logic: RSI Low and SMA Crossover Up
            if (currentRsi < rsiLow && prevSmaShort < prevSmaLong && currentSmaShort > currentSmaLong) {
                logger.info("📈 [{}] Potential BUY signal at bar {}: RSI ({}) < {} AND SMA crossover ({} < {} and {} > {})",
                    seriesName, i, currentRsi, rsiLow, prevSmaShort, prevSmaLong, currentSmaShort, currentSmaLong)

                val stopLoss = calculateStopLoss(barSeries, i, stopLossLookBackPeriod, TradeDirection.LONG)
                if (stopLoss >= entryPrice) {
                     logger.warn("🚫 [{}] BUY Signal at bar {}: Stop loss ({}) is not below entry price ({}). Skipping.", seriesName, i, stopLoss, entryPrice)
                } else {
                    val risk = entryPrice - stopLoss
                    val takeProfit = entryPrice + (risk * BigDecimal.valueOf(minRiskRewardRatio))
                    signal = Signal(
                        pair = seriesName, // Assuming series name is the pair
                        direction = TradeDirection.LONG,
                        entryPrice = entryPrice,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit.setScale(entryPrice.scale(), RoundingMode.HALF_UP),
                        riskRewardRatio = BigDecimal.valueOf(minRiskRewardRatio),
                        timestamp = currentBar.endTime.toString()
                    )
                }
            }
            // Sell Signal Logic: RSI High and SMA Crossover Down
            else if (currentRsi > rsiHigh && prevSmaShort > prevSmaLong && currentSmaShort < currentSmaLong) {
                logger.info("📉 [{}] Potential SELL signal at bar {}: RSI ({}) > {} AND SMA crossover ({} > {} and {} < {})",
                    seriesName, i, currentRsi, rsiHigh, prevSmaShort, prevSmaLong, currentSmaShort, currentSmaLong)

                val stopLoss = calculateStopLoss(barSeries, i, stopLossLookBackPeriod, TradeDirection.SHORT)
                 if (stopLoss <= entryPrice) {
                    logger.warn("🚫 [{}] SELL Signal at bar {}: Stop loss ({}) is not above entry price ({}). Skipping.", seriesName, i, stopLoss, entryPrice)
                } else {
                    val risk = stopLoss - entryPrice
                    val takeProfit = entryPrice - (risk * BigDecimal.valueOf(minRiskRewardRatio))
                    signal = Signal(
                        pair = seriesName, // Assuming series name is the pair
                        direction = TradeDirection.SHORT,
                        entryPrice = entryPrice,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit.setScale(entryPrice.scale(), RoundingMode.HALF_UP),
                        riskRewardRatio = BigDecimal.valueOf(minRiskRewardRatio),
                        timestamp = currentBar.endTime.toString()
                    )
                }
            } else {
                // Log skip reason more granularly
                var skipReason = "Conditions not met."
                if (currentRsi >= rsiLow && currentRsi <= rsiHigh) skipReason = "RSI ($currentRsi) not in buy/sell zone ($rsiLow/$rsiHigh)."
                else if (currentRsi < rsiLow && !(prevSmaShort < prevSmaLong && currentSmaShort > currentSmaLong)) skipReason = "RSI ($currentRsi) in buy zone, but no SMA buy crossover."
                else if (currentRsi > rsiHigh && !(prevSmaShort > prevSmaLong && currentSmaShort < currentSmaLong)) skipReason = "RSI ($currentRsi) in sell zone, but no SMA sell crossover."

                logger.debug("➖ [{}] Signal skipped for bar {}: {}", seriesName, i, skipReason)
            }

            signal?.let {
                signals.add(it)
                logger.info(
                    "💡 [{}] {} Signal Generated at bar {}: Entry={}, SL={}, TP={}, R:R={}, Time={}",
                    seriesName, it.direction, i,
                    it.entryPrice.toPlainString(), it.stopLoss.toPlainString(), it.takeProfit.toPlainString(),
                    it.riskRewardRatio.toPlainString(), it.timestamp
                )
            }
        }

        logger.info("✅ [{}] Finished signal generation. Total signals: {}", seriesName, signals.size)
        return signals
    }

    private fun calculateStopLoss(
        barSeries: BarSeries,
        currentIndex: Int,
        lookBackPeriod: Int,
        direction: TradeDirection
    ): BigDecimal {
        val seriesName = barSeries.name ?: "Unnamed Series"
        // Ensure lookBackPeriod does not go before the start of the series
        // Start index for lookback should be at least 0
        // currentIndex - lookBackPeriod + 1 is the start of the N-bar window
        // So, the actual start index for slicing is max(0, currentIndex - lookBackPeriod + 1)
        // The end index for slicing is currentIndex + 1 (exclusive)

        val startIndex = maxOf(0, currentIndex - lookBackPeriod + 1)
        if (startIndex > currentIndex) { // Should not happen if lookBackPeriod > 0
             logger.warn("⚠️ [{}] SL calc: Start index {} is greater than current index {}. Using current bar's low/high.", seriesName, startIndex, currentIndex)
             return if (direction == TradeDirection.LONG) barSeries.getBar(currentIndex).lowPrice.toBigDecimal()
                    else barSeries.getBar(currentIndex).highPrice.toBigDecimal()
        }

        var slPrice: Num? = null

        if (direction == TradeDirection.LONG) {
            slPrice = barSeries.getBar(startIndex).lowPrice // Initialize with the first bar in window
            for (j in startIndex..currentIndex) { // Iterate over the look-back window including current bar
                if (barSeries.getBar(j).lowPrice.isLessThan(slPrice)) {
                    slPrice = barSeries.getBar(j).lowPrice
                }
            }
        } else { // SHORT
            slPrice = barSeries.getBar(startIndex).highPrice // Initialize
            for (j in startIndex..currentIndex) {
                if (barSeries.getBar(j).highPrice.isGreaterThan(slPrice)) {
                    slPrice = barSeries.getBar(j).highPrice
                }
            }
        }
        val slBigDecimal = slPrice!!.toBigDecimal()
        logger.debug("🛡️ [{}] SL Calc for bar {}: Direction={}, Lookback={}, StartIdx={}, SL Price={}",
            seriesName, currentIndex, direction, lookBackPeriod, startIndex, slBigDecimal.toPlainString())
        return slBigDecimal
    }

    private fun Num.toBigDecimal(): BigDecimal = BigDecimal.valueOf(this.doubleValue())
}
