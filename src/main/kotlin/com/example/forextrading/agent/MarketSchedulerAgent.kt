package com.example.forextrading.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MarketSchedulerAgent(
    private val forexScannerAgent: ForexScannerAgent,
    // Providing a default CoroutineScope. Consider defining a specific bean if customization is needed.
    @Qualifier("applicationScope") private val applicationScope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(MarketSchedulerAgent::class.java)

    @Value("\${trading.defaults.symbols}")
    private lateinit var defaultSymbols: List<String>

    @Value("\${trading.defaults.interval}")
    private lateinit var defaultInterval: String

    @Value("\${scheduling.marketScanCron}")
    private lateinit var marketScanCron: String // To log which cron is used

    // The cron expression is loaded from application.yml
    @Scheduled(cron = "\${scheduling.marketScanCron}")
    fun triggerScan() {
        logger.info("MarketSchedulerAgent triggered by cron: [{}]. Starting scan for default symbols and interval.", marketScanCron)

        if (!::defaultSymbols.isInitialized || defaultSymbols.isEmpty()) {
            logger.error("Default symbols not initialized or empty. Check application.yml property 'trading.defaults.symbols'. Skipping scan.")
            return
        }
        if (!::defaultInterval.isInitialized || defaultInterval.isBlank()) {
            logger.error("Default interval not initialized or blank. Check application.yml property 'trading.defaults.interval'. Skipping scan.")
            return
        }

        val context = mapOf(
            "symbols" to defaultSymbols,
            "interval" to defaultInterval
        )

        // Launch the suspend function in the provided CoroutineScope
        applicationScope.launch {
            try {
                logger.info("Invoking ForexScannerAgent with context: {}", context)
                val signals = forexScannerAgent.scan(context)
                logger.info("ForexScannerAgent scan completed via scheduler. Generated {} signals.", signals.size)
            } catch (e: Exception) {
                // Catching exceptions within the coroutine to prevent them from crashing the scope
                // or being silently lost if not handled by a CoroutineExceptionHandler.
                logger.error("Error during scheduled scan execution: {}", e.message, e)
            }
        }
    }
}
