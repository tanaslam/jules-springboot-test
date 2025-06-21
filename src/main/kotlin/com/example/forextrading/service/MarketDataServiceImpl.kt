package com.example.forextrading.service

import com.example.forextrading.model.OhlcvData
// import com.fasterxml.jackson.annotation.JsonProperty // Not used directly in the final code
import com.fasterxml.jackson.databind.ObjectMapper
// import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule // Auto-configured by Spring Boot
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Helper data classes for Twelve Data API response parsing
data class TwelveDataTimeSeriesValue(
    val datetime: String,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String?
)

data class TwelveDataResponse(
    val meta: Map<String, Any>,
    val values: List<TwelveDataTimeSeriesValue>,
    val status: String,
    val message: String? = null // For error responses
)

@Service
class MarketDataServiceImpl(
    private val webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper // Spring Boot auto-configures this with JavaTimeModule
) : MarketDataService {

    private val logger = LoggerFactory.getLogger(MarketDataServiceImpl::class.java)

    @Value("\${twelve.api.key}")
    private lateinit var apiKey: String

    @Value("\${twelve.api.baseUrl}")
    private lateinit var baseUrl: String

    // Twelve Data specific date time formatter
    private val twelveDataDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override suspend fun fetchOhlcvData(symbol: String, interval: String, outputSize: Int): List<OhlcvData> {
        val client = webClientBuilder.baseUrl(baseUrl).build()

        logger.debug("Fetching OHLCV data for symbol: {}, interval: {}, outputSize: {}", symbol, interval, outputSize)

        try {
            val responseString = client.get()
                .uri { uriBuilder ->
                    uriBuilder.path("/time_series")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("outputsize", outputSize)
                        .queryParam("apikey", apiKey)
                        .queryParam("format", "JSON") // Explicitly request JSON
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono<String>() // Fetch as String first
                .timeout(Duration.ofSeconds(10)) // Increased timeout
                .awaitSingle()

            // logger.trace("Raw TwelveData response for {}: {}", symbol, responseString) // Uncomment for deep debugging

            val parsedResponse = objectMapper.readValue(responseString, TwelveDataResponse::class.java)

            if (parsedResponse.status != "ok") {
                logger.error("Error from Twelve Data API for symbol {}: {} - {}", symbol, parsedResponse.status, parsedResponse.message)
                // Consider a more specific exception for API errors
                throw RuntimeException("Twelve Data API error for $symbol: ${parsedResponse.message ?: parsedResponse.status}")
            }

            if (parsedResponse.values.isEmpty()) {
                logger.warn("No data returned from Twelve Data API for symbol {}. Raw response: {}", symbol, responseString)
                return emptyList()
            }

            return parsedResponse.values.mapNotNull { apiValue ->
                try {
                    // Assuming API provides time in UTC or a well-defined zone convertible to UTC/Instant
                    // If API provides local time without zone, ZoneId.systemDefault() might be risky if server zone != data zone
                    // For services like TwelveData, times are usually UTC.
                    val zonedDateTime = ZonedDateTime.parse(apiValue.datetime, twelveDataDateTimeFormatter.withZone(ZoneId.of("UTC")))
                    OhlcvData(
                        timestamp = zonedDateTime.toInstant(),
                        open = apiValue.open.toDouble(),
                        high = apiValue.high.toDouble(),
                        low = apiValue.low.toDouble(),
                        close = apiValue.close.toDouble(),
                        volume = apiValue.volume?.toLongOrNull()
                    )
                } catch (e: Exception) {
                    logger.error("Error parsing individual OHLCV value for symbol {}: {}. Data: {}", symbol, e.message, apiValue, e)
                    null // Skip problematic entries
                }
            }.reversed() // API returns newest first, TA4J expects oldest first for correct chronological order
        } catch (e: Exception) {
            // Log details including type of exception
            logger.error("Exception while fetching OHLCV data for symbol {}: {} - {}.", symbol, e::class.java.simpleName, e.message, e)
            return emptyList() // Or rethrow as a custom domain exception e.g., MarketDataFetchException
        }
    }

    override suspend fun fetchAndPrepareBarSeries(symbol: String, interval: String, outputSize: Int): BarSeries {
        val ohlcvDataList = fetchOhlcvData(symbol, interval, outputSize)
        val series = BaseBarSeriesBuilder().withName(symbol).build()

        if (ohlcvDataList.isEmpty()) {
            logger.warn("OHLCV data list is empty for symbol {}. Returning an empty BarSeries.", symbol)
            return series // Return empty series if no data
        }

        // Determine the bar duration from the interval string
        val timePeriod = parseIntervalToDuration(interval)

        ohlcvDataList.forEach { data ->
            try {
                // Ensure timestamp is correctly interpreted for ZonedDateTime conversion
                val zdt = ZonedDateTime.ofInstant(data.timestamp, ZoneId.systemDefault()) // Or ZoneId.of("UTC") if timestamps are UTC
                series.addBar(
                    timePeriod, // Duration of the bar (calculated from interval)
                    zdt,        // End time of the bar
                    data.open,
                    data.high,
                    data.low,
                    data.close,
                    data.volume?.toDouble() ?: 0.0 // Use 0.0 for volume if null
                )
            } catch (e: Exception) {
                // Log details including type of exception
                logger.error("Error adding bar to series for symbol {}: {} - {}. Data: {}", symbol, e::class.java.simpleName, e.message, data, e)
            }
        }
        return series
    }

    // Helper function to parse interval string to Duration for TA4j Bar
    private fun parseIntervalToDuration(interval: String): Duration {
        val value = interval.filter { it.isDigit() }.toLongOrNull() ?: 1L
        return when {
            interval.endsWith("min") -> Duration.ofMinutes(value)
            interval.endsWith("h") -> Duration.ofHours(value)
            interval.endsWith("day") -> Duration.ofDays(value)
            interval.endsWith("week") -> Duration.ofDays(value * 7) // Approx
            interval.endsWith("month") -> Duration.ofDays(value * 30) // Approx
            else -> {
                logger.warn("Unsupported interval string: {}. Defaulting to 1 Day.", interval)
                Duration.ofDays(1) // Default or throw exception
            }
        }
    }
}
