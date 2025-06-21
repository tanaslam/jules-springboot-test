package com.example.forextrading.service

import com.example.forextrading.model.StrategyParameters
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service
class StrategyConfigServiceImpl : StrategyConfigService {

    private val logger = LoggerFactory.getLogger(StrategyConfigServiceImpl::class.java)

    @Value("\${trading.strategy.rsiBuyThreshold:40}")
    private var initialRsiBuyThreshold: Int = 40

    @Value("\${trading.strategy.rsiSellThreshold:60}")
    private var initialRsiSellThreshold: Int = 60

    @Value("\${trading.strategy.smaShortPeriod:20}")
    private var initialSmaShortPeriod: Int = 20

    @Value("\${trading.strategy.smaLongPeriod:50}")
    private var initialSmaLongPeriod: Int = 50

    // Attempting to inject List<Double> directly from YAML list
    @Value("\${trading.strategy.fibRetracementLevels}")
    private lateinit var initialFibRetracementLevels: List<Double>

    // Fallback if direct List<Double> injection fails or is empty
    private val defaultFibLevels = listOf(0.382, 0.5, 0.618)


    // In-memory storage for the current strategy parameters
    private lateinit var currentStrategyParameters: StrategyParameters
    private val lock = ReentrantReadWriteLock()

    @PostConstruct
    private fun initialize() {
        var effectiveFibLevels = defaultFibLevels // Start with default

        // Check if initialFibRetracementLevels was injected and is not empty
        if (::initialFibRetracementLevels.isInitialized && initialFibRetracementLevels.isNotEmpty()) {
            effectiveFibLevels = initialFibRetracementLevels
            logger.info("Successfully loaded fibRetracementLevels from configuration: {}", effectiveFibLevels)
        } else {
            logger.warn("fibRetracementLevels not found or empty in configuration, or injection failed. Falling back to default values: {}", defaultFibLevels)
        }

        currentStrategyParameters = StrategyParameters(
            rsiBuyThreshold = initialRsiBuyThreshold,
            rsiSellThreshold = initialRsiSellThreshold,
            smaShortPeriod = initialSmaShortPeriod,
            smaLongPeriod = initialSmaLongPeriod,
            fibRetracementLevels = effectiveFibLevels
        )
        logger.info("Initialized StrategyConfigService with parameters: {}", currentStrategyParameters)
    }

    override fun getCurrentParameters(): StrategyParameters {
        return lock.read {
            currentStrategyParameters
        }
    }

    override fun updateParameters(newParams: StrategyParameters) {
        lock.write {
            currentStrategyParameters = newParams
        }
        logger.info("Strategy parameters updated to: {}", newParams)
    }
}
