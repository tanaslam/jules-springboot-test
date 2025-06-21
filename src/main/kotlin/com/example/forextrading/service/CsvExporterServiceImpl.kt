package com.example.forextrading.service

import com.example.forextrading.model.TradingSignal
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule // For proper Instant serialization
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
// import java.time.format.DateTimeFormatter // Not directly used if Jackson handles Instant well

@Service
class CsvExporterServiceImpl : CsvExporterService {

    private val logger = LoggerFactory.getLogger(CsvExporterServiceImpl::class.java)

    @Value("\${csv.filePath:trading_signals_log.csv}")
    private lateinit var filePath: String

    private val csvMapper = CsvMapper().apply {
        registerModule(JavaTimeModule()) // Ensure Instant is handled correctly
    }

    // Define the schema from the TradingSignal data class
    // Explicit column definition for header and order control.
    private val baseSchema: CsvSchema = CsvSchema.builder()
        .addColumn("timestamp")
        .addColumn("symbol")
        .addColumn("direction")
        .addColumn("entry")
        .addColumn("stopLoss")
        .addColumn("takeProfit")
        .addColumn("riskRewardRatio")
        .addColumn("rsi")
        .addColumn("sma20") // Corresponds to strategy's short SMA
        .addColumn("sma50") // Corresponds to strategy's long SMA
        .addColumn("fibLevel")
        .addColumn("outcome")
        .build()

    init {
        // Ensure parent directory exists
        try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
        } catch (e: Exception) {
            logger.error("Error creating parent directories for CSV file {}: {}", filePath, e.message, e)
        }
    }

    override fun logSignal(signal: TradingSignal) {
        logSignals(listOf(signal))
    }

    override fun logSignals(signals: List<TradingSignal>) {
        if (signals.isEmpty()) return

        val file = File(filePath)
        val writeHeader = !file.exists() || file.length() == 0L

        try {
            FileOutputStream(file, true).bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                // Obtain an ObjectWriter with the correct schema (with or without header)
                val schemaWithHeader = baseSchema.withHeader()
                val schemaWithoutHeader = baseSchema.withoutHeader()

                val objectWriter: ObjectWriter = csvMapper.writerFor(TradingSignal::class.java)
                    .with(if (writeHeader) schemaWithHeader else schemaWithoutHeader)

                signals.forEach { signal ->
                    try {
                        // The objectWriter.writeValue(writer, object) method handles writing the object
                        // and normally includes a newline character after each record.
                        objectWriter.writeValue(writer, signal)
                    } catch (e: Exception) {
                        logger.error("Error writing signal to CSV for symbol {}: {}", signal.symbol, e.message, e)
                    }
                }
            }
            if (signals.isNotEmpty()) { // Avoid logging if the input list was empty and we returned early.
                logger.info("{} signal(s) logged to {}", signals.size, filePath)
            }
        } catch (e: Exception) {
            logger.error("Error opening or writing to CSV file {}: {}", filePath, e.message, e)
        }
    }
}
