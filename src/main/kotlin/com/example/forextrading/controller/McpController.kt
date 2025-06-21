package com.example.forextrading.controller

import com.example.forextrading.agent.ForexScannerAgent
import com.example.forextrading.model.ScanRequest
import com.example.forextrading.model.TradingSignal
import kotlinx.coroutines.CoroutineScope
// import kotlinx.coroutines.Dispatchers // Not directly used here, scope is injected
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/mcp") // Base path for this controller, "Multi-Content Prompt" or "Manual Control Panel"
class McpController(
    private val forexScannerAgent: ForexScannerAgent,
    @Qualifier("applicationScope") private val coroutineScope: CoroutineScope // For launching suspend functions
) {
    private val logger = LoggerFactory.getLogger(McpController.class)

    @PostMapping("/scan")
    fun manualScan(@RequestBody scanRequest: ScanRequest): CompletableFuture<ResponseEntity<List<TradingSignal>>> {
        logger.info("Received manual scan request: {}", scanRequest)

        if (scanRequest.symbols.isEmpty()) {
            logger.warn("Manual scan request with empty symbols list.")
            // Immediately return a Bad Request response, completed future
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(emptyList<TradingSignal>()) // Or a more descriptive error model
            )
        }
        if (scanRequest.interval.isBlank()) {
            logger.warn("Manual scan request with blank interval.")
             return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(emptyList<TradingSignal>()) // Or a more descriptive error model
            )
        }

        // Further validation for interval format could be added here if needed
        // e.g., regex check against known patterns like "1min", "15min", "1h", etc.

        val context = mapOf(
            "symbols" to scanRequest.symbols,
            "interval" to scanRequest.interval
        )

        // As forexScannerAgent.scan is a suspend function, and Spring MVC controllers are typically blocking,
        // we launch it in a coroutine and return a CompletableFuture.
        // Spring MVC can handle CompletableFuture for asynchronous request processing.
        return coroutineScope.future {
            try {
                logger.info("Invoking ForexScannerAgent for manual scan with context: {}", context)
                val signals = forexScannerAgent.scan(context)
                logger.info("Manual scan complete. Generated {} signals.", signals.size)
                ResponseEntity.ok(signals)
            } catch (e: Exception) {
                logger.error("Error during manual scan execution: {}", e.message, e)
                // Ensure a specific error model is returned if desired, instead of just an empty list
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(emptyList<TradingSignal>())
            }
        }
    }
}
