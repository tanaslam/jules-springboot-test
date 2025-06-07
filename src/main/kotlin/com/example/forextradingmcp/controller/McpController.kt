package com.example.forextradingmcp.controller

import com.example.forextradingmcp.agent.ForexScannerAgent
import com.example.forextradingmcp.model.McpContext
import com.example.forextradingmcp.model.Signal
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mcp")
class McpController(
    private val forexScannerAgent: ForexScannerAgent
) {
    private val logger = LoggerFactory.getLogger(McpController::class.java)

    @PostMapping("/scan")
    fun processMcpRequest(@RequestBody context: McpContext): ResponseEntity<List<Signal>> {
        logger.info("Received MCP request to scan with context: {}", context)
        return try {
            val signals = forexScannerAgent.scan(context)
            logger.info("Scan completed. Found {} signals for context: {}", signals.size, context)
            ResponseEntity.ok(signals)
        } catch (e: IllegalStateException) { // Specific exception for known issues like API key
            logger.error(
                "Error processing MCP request for context {} due to invalid state (e.g., API key not set): {}",
                context,
                e.message
            )
            // Returning a more specific error or custom error object might be better.
            // For now, sending a generic message within the response.
            // Consider creating a standardized error response object for the API.
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(null) // Or provide a simple error message list: listOf(SignalError("Invalid state: ${e.message}"))
        } catch (e: RuntimeException) { // Catch broader runtime exceptions that might originate from services
            logger.error("Runtime error processing MCP request for context {}: {}", context, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null) // Or: listOf(SignalError("Internal server error: ${e.message}"))
        } catch (e: Exception) { // Fallback for any other unexpected errors
            logger.error("Unexpected error processing MCP request for context {}: {}", context, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null) // Or: listOf(SignalError("Unexpected internal server error"))
        }
    }
}
