package com.example.forextrading.service

import com.example.forextrading.model.StrategyParameters
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
// import org.ta4j.core.ZonedDateTimeBar // Not used directly in helper
import java.time.Duration
import java.time.ZonedDateTime
import java.time.ZoneOffset

class SignalAnalysisServiceImplTest {

    private lateinit var signalAnalysisService: SignalAnalysisService
    private lateinit var defaultParams: StrategyParameters

    @BeforeEach
    fun setUp() {
        signalAnalysisService = SignalAnalysisServiceImpl() // Test the actual implementation
        defaultParams = StrategyParameters(
            rsiBuyThreshold = 40,
            rsiSellThreshold = 60,
            smaShortPeriod = 20, // Default from StrategyParameters model
            smaLongPeriod = 50,  // Default from StrategyParameters model
            fibRetracementLevels = listOf(0.382, 0.5, 0.618) // Default from StrategyParameters model
        )
    }

    // Helper function to create a BarSeries with controlled OHLCV and time progression
    private fun createDetailedTestBarSeries(barsData: List<Map<String, Number>>, duration: Duration = Duration.ofDays(1)): BarSeries {
        val series = BaseBarSeriesBuilder().withName("DetailedTestSeries").build()
        // Ensure a consistent starting point for time to make tests predictable
        var startTime = ZonedDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

        barsData.forEach { barMap ->
            series.addBar(
                duration, // Duration of each bar
                startTime,  // End time of the bar
                barMap["open"]!!.toDouble(),
                barMap["high"]!!.toDouble(),
                barMap["low"]!!.toDouble(),
                barMap["close"]!!.toDouble(),
                barMap["volume"]?.toDouble() ?: 0.0
            )
            // For ta4j, the ZonedDateTime passed to addBar is typically the END time of the bar.
            // So, the next bar's end time should be current endTime + duration.
            startTime = startTime.plus(duration)
        }
        return series
    }


    @Test
    @DisplayName("Should return empty list if bar series is empty")
    fun `generateSignals with empty series returns empty list`() {
        val emptySeries = BaseBarSeriesBuilder().withName("EmptySeries").build()
        val signals = signalAnalysisService.generateSignals("EURUSD", emptySeries, defaultParams)
        assertTrue(signals.isEmpty(), "Should return empty list for empty series")
    }

    @Test
    @DisplayName("Should return empty list if bar series has insufficient data for SMAs or RSI")
    fun `generateSignals with insufficient data for indicators returns empty list`() {
        // Max lookback for RSI (14) or defaultParams.smaLongPeriod
        val requiredForIndicators = maxOf(14, defaultParams.smaLongPeriod)
        val data = List(requiredForIndicators - 1) {
            mapOf("open" to 1.0, "high" to 1.1, "low" to 0.9, "close" to 1.0, "volume" to 100.0)
        }
        val series = createDetailedTestBarSeries(data)
        val signals = signalAnalysisService.generateSignals("EURUSD", series, defaultParams)
        assertTrue(signals.isEmpty(), "Should return empty list for insufficient indicator data. Series bars: ${series.barCount}, Required: $requiredForIndicators")
    }

    @Test
    @DisplayName("Should return empty list if bar series has insufficient data for Fibonacci swing detection")
    fun `generateSignals with insufficient data for Fibonacci returns empty list`() {
        val fibLookback = defaultParams.smaLongPeriod * 2
        val requiredForIndicators = maxOf(14, defaultParams.smaLongPeriod)

        // Number of bars: enough for indicators, but one less than needed for Fib lookback.
        val numBars = fibLookback - 1

        // This test is only valid if numBars is actually greater than or equal to what indicators need.
        if (numBars < requiredForIndicators) {
            // This can happen if smaLongPeriod is very small, making fibLookback also small.
            // e.g. smaLongPeriod=5, fibLookback=10, numBars=9. But RSI needs 14.
            // In such a case, the "insufficient for indicators" test would cover it.
            // For this test to specifically target Fibonacci, we assume indicator requirements are met.
            System.err.println("Skipping Fibonacci insufficiency test: numBars ($numBars) is less than requiredForIndicators ($requiredForIndicators). This scenario is covered by indicator sufficiency tests.")
            return
        }

        val data = List(numBars) {
            mapOf("open" to 1.0, "high" to 1.1, "low" to 0.9, "close" to 1.0, "volume" to 100.0)
        }
        val series = createDetailedTestBarSeries(data)

        val signals = signalAnalysisService.generateSignals("EURUSD", series, defaultParams)
        assertTrue(signals.isEmpty(), "Should return empty list for insufficient Fibonacci data. Series bars: ${series.barCount}, Required Fib lookback: $fibLookback (SMA Long: ${defaultParams.smaLongPeriod}, RSI: 14)")
    }

    // --- Placeholder tests for specific signal conditions ---

        @Test
        @DisplayName("Should generate BUY signal when all conditions are met accurately")
        fun `generateSignals for BUY condition with precise data`() {
            // Strategy Parameters for this test (can use defaultParams or customize)
            val params = defaultParams.copy(
                rsiBuyThreshold = 40,
                smaShortPeriod = 5, // Shorter SMAs for quicker reaction in test data
                smaLongPeriod = 10  // Shorter SMAs for quicker reaction in test data
            )
            // Minimum bars for SMA_long=10, RSI=14, FibLookback=10*2=20
            // RSI needs 14 bars. Long SMA needs 10 bars. Fib lookback is 20 bars.
            // So, the swing high/low must be within the last 20 bars.
            // Indicators need enough data before that.
            // Total required bars = Max(RSI_period, SMA_long_period, Fib_lookback_period) for values at currentIndex.
            // The actual window for swing detection is from (currentIndex - fibLookback + 1) to currentIndex.
            // Let's ensure at least fibLookback + max(smaLongPeriod, rsiPeriod) bars.
            val rsiPeriod = 14
            val fibLookback = params.smaLongPeriod * 2
            val requiredBarsForIndicators = maxOf(rsiPeriod, params.smaLongPeriod) // e.g. 14
            // Total bars needed: fibLookback (for the swing window itself)
            // + requiredBarsForIndicators (for indicators to be valid at the start of the swing window or before)
            // This is a bit hand-wavy; simpler: ensure overall series is long enough, e.g. fibLookback + 20 buffer
            val totalRequiredBars = fibLookback + 20 // approx 40 bars

            val barsData = mutableListOf<Map<String, Number>>()
            // Initial period to stabilize indicators and provide history before swing
            // Price: 1.1000
            (1..(totalRequiredBars - 20)).forEach { _ -> // Leave 20 bars for swing and signal
                barsData.add(mapOf("open" to 1.1000, "high" to 1.1050, "low" to 1.0950, "close" to 1.1000, "volume" to 100))
            }

            // Create Swing Low (price ~1.0800)
            // This will be roughly at index (totalRequiredBars - 20)
            barsData.add(mapOf("open" to 1.1000, "high" to 1.1000, "low" to 1.0800, "close" to 1.0820, "volume" to 150)) // Swing Low bar
            val swingLowPrice = 1.0800

            // Upward move to create Swing High (next 5 bars, price ~1.1200)
            // These bars should push SMA5 above SMA10 gradually if starting from crossover/below
            barsData.add(mapOf("open" to 1.0820, "high" to 1.0950, "low" to 1.0810, "close" to 1.0930, "volume" to 120))
            barsData.add(mapOf("open" to 1.0930, "high" to 1.1050, "low" to 1.0920, "close" to 1.1030, "volume" to 120))
            barsData.add(mapOf("open" to 1.1030, "high" to 1.1150, "low" to 1.1020, "close" to 1.1130, "volume" to 120))
            barsData.add(mapOf("open" to 1.1130, "high" to 1.1200, "low" to 1.1120, "close" to 1.1180, "volume" to 160)) // Swing High bar
            val swingHighPrice = 1.1200

            // Retracement downwards, SMA crossover (SMA5 should cross SMA10 if not already above), RSI drop
            // These bars should drop price significantly to lower RSI and hit Fib.
            // SMA5 > SMA10 should be established or maintained.
            // Closing prices need to drop to get RSI low.
            barsData.add(mapOf("open" to 1.1180, "high" to 1.1180, "low" to 1.1050, "close" to 1.1060, "volume" to 130))
            barsData.add(mapOf("open" to 1.1060, "high" to 1.1070, "low" to 1.0980, "close" to 1.0990, "volume" to 140)) // RSI dropping
            barsData.add(mapOf("open" to 1.0990, "high" to 1.0995, "low" to 1.0920, "close" to 1.0930, "volume" to 150)) // RSI should be low

            val fibLevel = 0.618
            val targetFibPrice = swingHighPrice - (swingHighPrice - swingLowPrice) * fibLevel // Approx 1.1200 - (0.0400 * 0.618) = 1.1200 - 0.02472 = 1.09528

            // Final bar: Price touches the Fib level, RSI low, SMA short > SMA long
            // Low of this bar must hit targetFibPrice (or be very close below it).
            // Close price should be near the Fib level as per `isPriceNear` logic.
            // isPriceNear checks: relevantBarExtremityPrice <= targetFibPrice + tolerance && currentClose >= targetFibPrice - tolerance && currentClose <= targetFibPrice + (targetFibPrice * 0.005)
            val signalBarLow = targetFibPrice - 0.0001 // ensure low pierces the Fib level
            val signalBarClose = targetFibPrice + 0.00005 // close slightly above, but within tolerance of isPriceNear
            barsData.add(mapOf("open" to 1.0930, "high" to 1.0980, "low" to signalBarLow, "close" to signalBarClose, "volume" to 200)) // The signal bar!

            // Ensure we have exactly totalRequiredBars. If barsData is longer, trim from start. If shorter, this test is flawed.
            // The logic above aims to build just enough. Let's verify.
            // If current barsData.size is less than totalRequiredBars, the initial padding was miscalculated.
             if (barsData.size < totalRequiredBars) {
                 val currentSize = barsData.size
                 (1..(totalRequiredBars - currentSize)).forEach { _ ->
                     barsData.add(0, mapOf("open" to 1.1000, "high" to 1.1050, "low" to 1.0950, "close" to 1.1000, "volume" to 100)) // Prepend
                 }
             }


            val series = createDetailedTestBarSeries(barsData, Duration.ofMinutes(15)) // Use smaller duration for more realistic SMA/RSI

            // --- Debugging block (Uncomment to use if test fails) ---
            // println("Series bar count: ${series.barCount}")
            // val closePrice = org.ta4j.core.indicators.helpers.ClosePriceIndicator(series)
            // val rsi = org.ta4j.core.indicators.RSIIndicator(closePrice, rsiPeriod)
            // val smaS = org.ta4j.core.indicators.SMAIndicator(closePrice, params.smaShortPeriod)
            // val smaL = org.ta4j.core.indicators.SMAIndicator(closePrice, params.smaLongPeriod)
            // println("Default Params for test: RSI Buy=${params.rsiBuyThreshold}, SMA S=${params.smaShortPeriod}, SMA L=${params.smaLongPeriod}, FibLookback=${fibLookback}")
            // println("Target Fib Price for BUY: $targetFibPrice (based on SwingLow $swingLowPrice, SwingHigh $swingHighPrice)")
            // val endIndex = series.endIndex
            // if (endIndex >= 0) {
            //     println("Last bar (idx $endIndex): O=${series.getBar(endIndex).openPrice}, H=${series.getBar(endIndex).highPrice}, L=${series.getBar(endIndex).lowPrice}, C=${series.getBar(endIndex).closePrice}")
            //     if (endIndex >= rsiPeriod -1) println("  RSI: ${rsi.getValue(endIndex)}") else println("  RSI: not enough data")
            //     if (endIndex >= params.smaShortPeriod -1) println("  SMA Short: ${smaS.getValue(endIndex)}") else println("  SMA Short: not enough data")
            //     if (endIndex >= params.smaLongPeriod -1) println("  SMA Long: ${smaL.getValue(endIndex)}") else println("  SMA Long: not enough data")
            //     // Check swing high/low detection if possible (this is internal to the service)
            // }
            // --- End Debugging block ---

            val signals = signalAnalysisService.generateSignals("EURUSD", series, params)

            assertEquals(1, signals.size, "Should generate one BUY signal. Signals found: ${signals.joinToString { it.direction + "@" + it.entry }}")
            val signal = signals[0]
            assertEquals("BUY", signal.direction)
            assertEquals("EURUSD", signal.symbol)

            val expectedEntry = series.getBar(series.endIndex).closePrice.doubleValue()
            assertEquals(expectedEntry, signal.entry, 0.00001, "Entry price should be the close of the last bar")

            val rsiIndicator = org.ta4j.core.indicators.RSIIndicator(org.ta4j.core.indicators.helpers.ClosePriceIndicator(series), rsiPeriod)
            val currentRsi = rsiIndicator.getValue(series.endIndex).doubleValue()
            assertTrue(currentRsi < params.rsiBuyThreshold, "RSI ($currentRsi) should be less than ${params.rsiBuyThreshold}")

            val smaShortIndicator = org.ta4j.core.indicators.SMAIndicator(org.ta4j.core.indicators.helpers.ClosePriceIndicator(series), params.smaShortPeriod)
            val smaLongIndicator = org.ta4j.core.indicators.SMAIndicator(org.ta4j.core.indicators.helpers.ClosePriceIndicator(series), params.smaLongPeriod)
            val currentSmaShort = smaShortIndicator.getValue(series.endIndex).doubleValue()
            val currentSmaLong = smaLongIndicator.getValue(series.endIndex).doubleValue()
            assertTrue(currentSmaShort > currentSmaLong, "SMA Short ($currentSmaShort) should be greater than SMA Long ($currentSmaLong)")

            assertEquals(fibLevel, signal.fibLevel, 0.001, "Fibonacci level in signal should be correct")

            val expectedStopLoss = expectedEntry * (1 - 0.01) // Consistent with SignalAnalysisService's 1% SL rule
            val riskAmount = expectedEntry - expectedStopLoss
            val expectedTakeProfit = expectedEntry + riskAmount * 2

            assertEquals(expectedStopLoss, signal.stopLoss, 0.00001, "Stop loss calculation is incorrect")
            assertEquals(expectedTakeProfit, signal.takeProfit, 0.00001, "Take profit calculation is incorrect")
            assertEquals("1:2", signal.riskRewardRatio)
        }

        @Test
        @DisplayName("Should generate SELL signal when all conditions are met accurately")
        fun `generateSignals for SELL condition with precise data`() {
            val params = defaultParams.copy(
                rsiSellThreshold = 60,
                smaShortPeriod = 5, // Shorter SMAs for quicker reaction
                smaLongPeriod = 10
            )
            val rsiPeriod = 14
            val fibLookback = params.smaLongPeriod * 2 // = 20 bars for swing detection
            val totalRequiredBars = fibLookback + 20 // Ensure enough bars for indicators and swing window, approx 40

            val barsData = mutableListOf<Map<String, Number>>()
            // Initial stable period
            (1..(totalRequiredBars - 20)).forEach { _ -> // Leave 20 bars for swing and signal
                barsData.add(mapOf("open" to 1.1000, "high" to 1.1050, "low" to 1.0950, "close" to 1.1000, "volume" to 100))
            }

            // Create Swing High (price ~1.1200)
            barsData.add(mapOf("open" to 1.1000, "high" to 1.1200, "low" to 1.0980, "close" to 1.1180, "volume" to 150)) // Swing High bar
            val swingHighPrice = 1.1200

            // Downward move to create Swing Low (next 5 bars, price ~1.0800)
            // These bars should push SMA5 below SMA10 gradually
            barsData.add(mapOf("open" to 1.1180, "high" to 1.1190, "low" to 1.1100, "close" to 1.1120, "volume" to 120))
            barsData.add(mapOf("open" to 1.1120, "high" to 1.1130, "low" to 1.1000, "close" to 1.1020, "volume" to 120))
            barsData.add(mapOf("open" to 1.1020, "high" to 1.1030, "low" to 1.0900, "close" to 1.0920, "volume" to 120))
            barsData.add(mapOf("open" to 1.0920, "high" to 1.0930, "low" to 1.0800, "close" to 1.0820, "volume" to 160)) // Swing Low bar
            val swingLowPrice = 1.0800

            // Retracement upwards, SMA short < SMA long should be maintained/established, RSI rise
            // Closing prices need to rise to get RSI high.
            barsData.add(mapOf("open" to 1.0820, "high" to 1.0950, "low" to 1.0820, "close" to 1.0940, "volume" to 130))
            barsData.add(mapOf("open" to 1.0940, "high" to 1.1050, "low" to 1.0930, "close" to 1.1040, "volume" to 140)) // RSI rising
            barsData.add(mapOf("open" to 1.1040, "high" to 1.1095, "low" to 1.1030, "close" to 1.1080, "volume" to 150)) // RSI should be high

            val fibLevel = 0.618
            val targetFibPrice = swingLowPrice + (swingHighPrice - swingLowPrice) * fibLevel // Approx 1.0800 + (0.0400 * 0.618) = 1.0800 + 0.02472 = 1.10472

            // Final bar: Price touches the Fib level, RSI high, SMA short < SMA long
            // High of this bar must hit targetFibPrice (or be very close above it).
            // Close price should be near the Fib level as per `isPriceNear` logic for SELL.
            // isPriceNear (SELL): (relevantBarExtremityPrice >= targetFibPrice - tolerance) && (currentClose <= targetFibPrice + tolerance) && (currentClose >= targetFibPrice - (targetFibPrice * 0.005))
            val signalBarHigh = targetFibPrice + 0.0001 // Ensure high pierces the Fib level
            val signalBarClose = targetFibPrice - 0.00005 // Close slightly below, but within tolerance
            barsData.add(mapOf("open" to 1.1080, "high" to signalBarHigh, "low" to 1.1020, "close" to signalBarClose, "volume" to 200)) // The signal bar!

            // Ensure we have enough data for all indicators
            if (barsData.size < totalRequiredBars) {
                val currentSize = barsData.size
                (1..(totalRequiredBars - currentSize)).forEach { _ ->
                     barsData.add(0, mapOf("open" to 1.1000, "high" to 1.1050, "low" to 1.0950, "close" to 1.1000, "volume" to 100)) // Prepend
                }
            }

            val series = createDetailedTestBarSeries(barsData, Duration.ofMinutes(15))

            // --- Debugging block (Uncomment to use if test fails) ---
            // println("Series bar count: ${series.barCount}")
            // val closePrice = org.ta4j.core.indicators.helpers.ClosePriceIndicator(series)
            // val rsi = org.ta4j.core.indicators.RSIIndicator(closePrice, rsiPeriod)
            // val smaS = org.ta4j.core.indicators.SMAIndicator(closePrice, params.smaShortPeriod)
            // val smaL = org.ta4j.core.indicators.SMAIndicator(closePrice, params.smaLongPeriod)
            // println("Default Params for test: RSI Sell=${params.rsiSellThreshold}, SMA S=${params.smaShortPeriod}, SMA L=${params.smaLongPeriod}, FibLookback=${fibLookback}")
            // println("Target Fib Price for SELL: $targetFibPrice (based on SwingHigh $swingHighPrice, SwingLow $swingLowPrice)")
            // val endIndex = series.endIndex
            // if (endIndex >= 0) {
            //     println("Last bar (idx $endIndex): O=${series.getBar(endIndex).openPrice}, H=${series.getBar(endIndex).highPrice}, L=${series.getBar(endIndex).lowPrice}, C=${series.getBar(endIndex).closePrice}")
            //     if (endIndex >= rsiPeriod -1) println("  RSI: ${rsi.getValue(endIndex)}") else println("  RSI: not enough data")
            //     if (endIndex >= params.smaShortPeriod -1) println("  SMA Short: ${smaS.getValue(endIndex)}") else println("  SMA Short: not enough data")
            //     if (endIndex >= params.smaLongPeriod -1) println("  SMA Long: ${smaL.getValue(endIndex)}") else println("  SMA Long: not enough data")
            // }
            // --- End Debugging block ---

            val signals = signalAnalysisService.generateSignals("GBPUSD", series, params)

            assertEquals(1, signals.size, "Should generate one SELL signal. Signals: ${signals.joinToString { it.direction + "@" + it.entry }}")
            val signal = signals[0]
            assertEquals("SELL", signal.direction)
            assertEquals("GBPUSD", signal.symbol)

            val expectedEntry = series.getBar(series.endIndex).closePrice.doubleValue()
            assertEquals(expectedEntry, signal.entry, 0.00001, "Entry price should be the close of the last bar")

            val rsiIndicator = org.ta4j.core.indicators.RSIIndicator(org.ta4j.core.indicators.helpers.ClosePriceIndicator(series), rsiPeriod)
            val currentRsi = rsiIndicator.getValue(series.endIndex).doubleValue()
            assertTrue(currentRsi > params.rsiSellThreshold, "RSI ($currentRsi) should be greater than ${params.rsiSellThreshold}")

            val smaShortIndicator = org.ta4j.core.indicators.SMAIndicator(org.ta4j.core.indicators.helpers.ClosePriceIndicator(series), params.smaShortPeriod)
            val smaLongIndicator = org.ta4j.core.indicators.SMAIndicator(org.ta4j.core.indicators.helpers.ClosePriceIndicator(series), params.smaLongPeriod)
            val currentSmaShort = smaShortIndicator.getValue(series.endIndex).doubleValue()
            val currentSmaLong = smaLongIndicator.getValue(series.endIndex).doubleValue()
            assertTrue(currentSmaShort < currentSmaLong, "SMA Short ($currentSmaShort) should be less than SMA Long ($currentSmaLong)")

            assertEquals(fibLevel, signal.fibLevel, 0.001, "Fibonacci level in signal should be correct")

            val expectedStopLoss = expectedEntry * (1 + 0.01) // Consistent with SignalAnalysisService's 1% SL rule for SELL
            val riskAmount = expectedStopLoss - expectedEntry
            val expectedTakeProfit = expectedEntry - riskAmount * 2

            assertEquals(expectedStopLoss, signal.stopLoss, 0.00001, "Stop loss calculation is incorrect for SELL")
            assertEquals(expectedTakeProfit, signal.takeProfit, 0.00001, "Take profit calculation is incorrect for SELL")
            assertEquals("1:2", signal.riskRewardRatio)
        }

        @Test
        @DisplayName("Should not generate BUY signal if Fibonacci condition not met, even if RSI and SMA are favorable")
        fun `generateSignals no BUY signal if Fibonacci condition fails`() {
            val params = defaultParams.copy(
                rsiBuyThreshold = 40,
                smaShortPeriod = 5,
                smaLongPeriod = 10
            )
            val rsiPeriod = 14 // Standard RSI period
            val fibLookback = params.smaLongPeriod * 2
            val totalRequiredBars = fibLookback + 20 // approx 40 bars for stability and swing

            val barsData = mutableListOf<Map<String, Number>>()
            // Initial stable period
            (1..(totalRequiredBars - 20)).forEach { _ ->
                barsData.add(mapOf("open" to 1.1000, "high" to 1.1050, "low" to 1.0950, "close" to 1.1000, "volume" to 100))
            }

            // Create Swing Low
            barsData.add(mapOf("open" to 1.1000, "high" to 1.1000, "low" to 1.0800, "close" to 1.0820, "volume" to 150))
            val swingLowPrice = 1.0800

            // Upward move to create Swing High
            barsData.add(mapOf("open" to 1.0820, "high" to 1.0950, "low" to 1.0810, "close" to 1.0930, "volume" to 120))
            barsData.add(mapOf("open" to 1.0930, "high" to 1.1050, "low" to 1.0920, "close" to 1.1030, "volume" to 120))
            barsData.add(mapOf("open" to 1.1030, "high" to 1.1150, "low" to 1.1020, "close" to 1.1130, "volume" to 120))
            barsData.add(mapOf("open" to 1.1130, "high" to 1.1200, "low" to 1.1120, "close" to 1.1180, "volume" to 160))
            val swingHighPrice = 1.1200

            // Retracement downwards, SMA short > SMA long, RSI low
            barsData.add(mapOf("open" to 1.1180, "high" to 1.1180, "low" to 1.1050, "close" to 1.1060, "volume" to 130))
            barsData.add(mapOf("open" to 1.1060, "high" to 1.1070, "low" to 1.0980, "close" to 1.0990, "volume" to 140))
            barsData.add(mapOf("open" to 1.0990, "high" to 1.0995, "low" to 1.0920, "close" to 1.0930, "volume" to 150)) // RSI low, SMAs crossed for BUY

            // The target Fib level: 61.8% of (swingHigh - swingLow) from swingHigh
            val fibLevel = 0.618 // This is what the service uses
            val targetFibPrice = swingHighPrice - (swingHighPrice - swingLowPrice) * fibLevel // Approx 1.09528

            // Final bar: Price does NOT touch the Fib level. It stays well above it.
            // Low is targetFibPrice + 0.0050 (50 pips above), close is also above.
            // isPriceNear for BUY: relevantBarExtremityPrice <= targetFibPrice + tolerance && currentClose >= targetFibPrice - tolerance ...
            // If low is 1.10028 (target + 0.0050), it's > targetFibPrice + tolerance (e.g. 1.09528 + 0.001 = 1.09628)
            // So, the first part of isPriceNear (low <= targetFibPrice + tolerance) will be false.
            val lastBarLow = targetFibPrice + 0.0050
            val lastBarClose = targetFibPrice + 0.0045
            barsData.add(mapOf("open" to 1.0930, "high" to lastBarLow + 0.0010, "low" to lastBarLow, "close" to lastBarClose, "volume" to 200))

            // Ensure enough bars for indicators
            if (barsData.size < totalRequiredBars) {
                val currentSize = barsData.size
                (1..(totalRequiredBars - currentSize)).forEach { _ ->
                    barsData.add(0, mapOf("open" to 1.1000, "high" to 1.1050, "low" to 1.0950, "close" to 1.1000, "volume" to 100))
                }
            }

            val series = createDetailedTestBarSeries(barsData, Duration.ofMinutes(15))

            // Verify RSI and SMA conditions would be met to ensure the Fib part is what's failing
            val rsiIndicator = org.ta4j.core.indicators.RSIIndicator(org.ta4j.core.indicators.helpers.ClosePriceIndicator(series), rsiPeriod)
            val currentRsi = rsiIndicator.getValue(series.endIndex).doubleValue()
            assertTrue(currentRsi < params.rsiBuyThreshold, "Pre-condition failed: RSI ($currentRsi) should be less than ${params.rsiBuyThreshold}")

            val smaShortIndicator = org.ta4j.core.indicators.SMAIndicator(org.ta4j.core.indicators.helpers.ClosePriceIndicator(series), params.smaShortPeriod)
            val smaLongIndicator = org.ta4j.core.indicators.SMAIndicator(org.ta4j.core.indicators.helpers.ClosePriceIndicator(series), params.smaLongPeriod)
            val currentSmaShort = smaShortIndicator.getValue(series.endIndex).doubleValue()
            val currentSmaLong = smaLongIndicator.getValue(series.endIndex).doubleValue()
            assertTrue(currentSmaShort > currentSmaLong, "Pre-condition failed: SMA Short ($currentSmaShort) should be greater than SMA Long ($currentSmaLong)")

            // --- Debugging block (Uncomment to use if test fails) ---
            // println("Series bar count: ${series.barCount}")
            // val closePrice = org.ta4j.core.indicators.helpers.ClosePriceIndicator(series)
            // println("Target Fib Price for BUY (but should fail): $targetFibPrice (based on SwingLow $swingLowPrice, SwingHigh $swingHighPrice)")
            // val endIndex = series.endIndex
            // if (endIndex >= 0) {
            //     println("Last bar (idx $endIndex): O=${series.getBar(endIndex).openPrice}, H=${series.getBar(endIndex).highPrice}, L=${series.getBar(endIndex).lowPrice}, C=${series.getBar(endIndex).closePrice}")
            //     println("  RSI: ${rsiIndicator.getValue(endIndex)}")
            //     println("  SMA Short: ${smaShortIndicator.getValue(endIndex)}")
            //     println("  SMA Long: ${smaLongIndicator.getValue(endIndex)}")
            // }
            // --- End Debugging block ---

            val signals = signalAnalysisService.generateSignals("EURUSD", series, params)

            assertTrue(signals.isEmpty(), "Should not generate any signal as Fibonacci condition is not met. Signals found: ${signals.joinToString { it.direction + "@" + it.entry }}")
        }

    @Test
    @DisplayName("Stop Loss and Take Profit should be calculated correctly for BUY signal - Placeholder")
    fun `calculate SL and TP for BUY signal`() {
        assertTrue(true) // Placeholder
    }

    @Test
    @DisplayName("Stop Loss and Take Profit should be calculated correctly for SELL signal - Placeholder")
    fun `calculate SL and TP for SELL signal`() {
        assertTrue(true) // Placeholder
    }
}
