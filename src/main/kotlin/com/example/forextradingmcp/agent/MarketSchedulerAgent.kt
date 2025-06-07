package com.example.forextradingmcp.agent

import com.example.forextradingmcp.model.McpContext
import com.example.forextradingmcp.model.StrategyParameters
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MarketSchedulerAgent(
    private val forexScannerAgent: ForexScannerAgent
) {
    private val logger = LoggerFactory.getLogger(MarketSchedulerAgent::class.java)

    // Define default parameters for the scheduled scan
    private val defaultCurrencyPairs = listOf("EUR/USD", "GBP/USD", "USD/JPY")
    private val defaultInterval = "15min" // Align with cron expression
    private val defaultOutputSize = 70    // As needed by SignalAnalysisService defaults (SMA 50 + buffer)
    private val defaultStrategyParams = StrategyParameters() // Uses defaults: RSI 14 (30/70), SMA 20/50, R:R 2.0, SL Lookback 10

    // Cron expression: "second minute hour day-of-month month day-of-week"
    // Runs at the start of every 15th minute of the hour. e.g., 00:00:00, 00:15:00, 00:30:00, 00:45:00
    @Scheduled(cron = "0 */15 * * * *")
    fun triggerForexScan() {
        logger.info("Scheduled market scan starting...")

        val context = McpContext(
            currencyPairs = defaultCurrencyPairs,
            interval = defaultInterval,
            outputSize = defaultOutputSize,
            strategyParameters = defaultStrategyParams
        )

        logger.info("Constructed McpContext for scheduled scan: {}", context)

        try {
            val signals = forexScannerAgent.scan(context)
            if (signals.isNotEmpty()) {
                logger.info("Scheduled scan generated {} signals. First signal example: {}", signals.size, signals.firstOrNull())
                // In a real application, these signals would be passed to another service
                // for order placement, notification, or storage.
                // For example: signalHandlerService.processSignals(signals)
            } else {
                logger.info("Scheduled scan completed. No signals generated for context: {}", context)
            }
        } catch (e: Exception) {
            // This catch block ensures that an unexpected error in the scan
            // does not stop the scheduler from running future scans.
            logger.error("Error during scheduled forex scan for context: {}", context, e)
        }
    }
}
