package com.example.forextradingmcp.util

import com.example.forextradingmcp.model.Signal
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

object CsvExporter {
    private val logger = LoggerFactory.getLogger(CsvExporter::class.java)

    private const val CSV_HEADER = "Pair,Direction,Entry,StopLoss,TakeProfit,RiskRewardRatio,Timestamp"

    fun exportSignals(signals: List<Signal>, directory: String, fileName: String) {
        val dirPath = Paths.get(directory)
        val fullPath = dirPath.resolve(fileName).toString()

        logger.info("📄 Attempting to export {} signals to CSV file: {}", signals.size, fullPath)

        try {
            // Create directory if it doesn't exist
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath)
                logger.info("📁 Created directory: {}", directory)
            }

            FileWriter(fullPath).use { writer -> // Using use for automatic resource management
                writer.append(CSV_HEADER)
                writer.append('\n')

                for (signal in signals) {
                    writer.append(signal.pair)
                    writer.append(',')
                    writer.append(signal.direction.name)
                    writer.append(',')
                    writer.append(signal.entryPrice.toPlainString()) // Use toPlainString for BigDecimals
                    writer.append(',')
                    writer.append(signal.stopLoss.toPlainString())
                    writer.append(',')
                    writer.append(signal.takeProfit.toPlainString())
                    writer.append(',')
                    writer.append(signal.riskRewardRatio.toPlainString())
                    writer.append(',')
                    writer.append(signal.timestamp) // Timestamp is already a String
                    writer.append('\n')
                }
                writer.flush()
                logger.info("✅ Successfully exported signals to {}", fullPath)
            }
        } catch (e: IOException) {
            logger.error("❌ Error writing signals to CSV file {}: {}", fullPath, e.message, e)
            // Depending on requirements, might re-throw or handle differently
        } catch (e: SecurityException) {
            logger.error("🛡️ Security error while creating directory or file {}: {}", fullPath, e.message, e) // Changed emoji for SecurityException
        } catch (e: Exception) {
            logger.error("💥 An unexpected error occurred during CSV export to {}: {}", fullPath, e.message, e)
        }
    }
}
