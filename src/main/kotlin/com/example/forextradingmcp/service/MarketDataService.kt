package com.example.forextradingmcp.service

import com.example.forextradingmcp.dto.TwelveDataResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.num.DecimalNum
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
class MarketDataService(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) {

    @Value("\${forex.data.api_key}")
    private lateinit var apiKey: String

    private val logger = LoggerFactory.getLogger(MarketDataService::class.java)

    fun fetchMarketData(symbol: String, interval: String, outputSize: Int = 70): BarSeries {
        logger.info("Fetching market data for symbol: {}, interval: {}, outputSize: {}", symbol, interval, outputSize)
        if (!::apiKey.isInitialized || apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            logger.error("API key is not configured. Please set forex.data.api_key in application.yaml or FOREX_DATA_API_KEY environment variable.")
            throw IllegalStateException("API key is not configured.")
        }

        val url = "https://api.twelvedata.com/time_series?symbol=$symbol&interval=$interval&apikey=$apiKey&outputsize=$outputSize&timezone=UTC&format=JSON"
        logger.debug("Requesting URL: {}", url)

        val responseBody: String?
        try {
            responseBody = restTemplate.getForObject(url, String::class.java)
        } catch (e: HttpClientErrorException) {
            logger.error("Client error fetching data from Twelve Data for $symbol: {} - {}", e.statusCode, e.responseBodyAsString, e)
            throw RuntimeException("Client error fetching data from Twelve Data for $symbol: ${e.statusCode}", e)
        } catch (e: HttpServerErrorException) {
            logger.error("Server error fetching data from Twelve Data for $symbol: {} - {}", e.statusCode, e.responseBodyAsString, e)
            throw RuntimeException("Server error fetching data from Twelve Data for $symbol: ${e.statusCode}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error fetching data from Twelve Data for $symbol", e)
            throw RuntimeException("Unexpected error fetching data from Twelve Data for $symbol", e)
        }

        if (responseBody == null) {
            logger.error("Received null response body from Twelve Data for $symbol")
            throw RuntimeException("Received null response body from Twelve Data for $symbol")
        }

        val twelveDataResponse = objectMapper.readValue<TwelveDataResponse>(responseBody)

        if (twelveDataResponse.status == "error") {
            val errorMessage = "Twelve Data API error for $symbol: ${twelveDataResponse.message ?: "Unknown error"}"
            logger.error(errorMessage)
            throw RuntimeException(errorMessage)
        }

        if (twelveDataResponse.values.isNullOrEmpty()) {
            logger.warn("No data values received from Twelve Data for $symbol. Returning empty series.")
            return BaseBarSeries("$symbol $interval")
        }

        val series = BaseBarSeries("$symbol $interval")
        // Ensure ZoneId.of("UTC") is used as specified in API call
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))

        // Twelve Data API returns data with the latest entry first. We need to reverse it for TA4J.
        twelveDataResponse.values.reversed().forEach { value ->
            try {
                val endTime = ZonedDateTime.parse(value.datetime, formatter)
                series.addBar(
                    endTime,
                    DecimalNum.valueOf(value.open),
                    DecimalNum.valueOf(value.high),
                    DecimalNum.valueOf(value.low),
                    DecimalNum.valueOf(value.close),
                    DecimalNum.valueOf(value.volume)
                )
            } catch (e: Exception) {
                logger.error("Error parsing value entry for $symbol: $value", e)
                // Decide if to skip this bar or throw error for the whole series
            }
        }
        logger.info("Successfully fetched and processed {} bars for symbol: {}", series.barCount, symbol)
        return series
    }
}
