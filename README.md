# Spring AI Forex Trading System

## 1. Project Overview

This project is a Spring Boot application built with Kotlin (2.0) that implements an automated Forex trading system. It leverages Spring AI's conceptual MCP (Multi-Content Prompt) agents to manage different aspects of trading, from market scanning and signal generation to periodic backtesting and strategy recalibration.

The system is designed to scan Forex data at regular intervals, apply technical analysis indicators (RSI, SMAs, Fibonacci retracement) to generate trading signals, and manage trades with a defined risk/reward ratio.

## 2. Features

*   **Automated Market Scanning**: Scans Forex data every 15 minutes (configurable).
*   **Technical Analysis Based Signals**:
    *   Uses RSI (Relative Strength Index), SMA (Simple Moving Averages - 20/50 periods), and Fibonacci retracement levels (0.382, 0.5, 0.618) for filtering trading signals.
    *   **BUY Condition**: RSI < 40, SMA20 crosses above SMA50, price touches/bounces near 61.8% Fibonacci retracement level of a prior up-swing.
    *   **SELL Condition**: RSI > 60, SMA20 crosses below SMA50, price touches/bounces near 61.8% Fibonacci retracement level of a prior down-swing.
*   **Risk Management**: Applies a 1:2 risk/reward ratio for calculated Stop Loss (SL) and Take Profit (TP) levels.
*   **Automated Backtesting & Recalibration**:
    *   Periodically (daily/weekly, configurable) backtests the trading strategy on past month's performance.
    *   Tests variations of strategy parameters (e.g., RSI thresholds, SMA periods).
    *   Automatically updates the live trading strategy with the optimal parameters found.
*   **REST API**: Exposes an endpoint (`/mcp/scan`) to manually trigger market scans and retrieve generated signals.
*   **CSV Logging**: Logs all generated trading signals to a CSV file for record-keeping and analysis.

## 3. System Architecture

The system is composed of several key components:

*   **Services**:
    *   `MarketDataService`: Fetches OHLCV (Open, High, Low, Close, Volume) data from the Twelve Data API and prepares it for analysis (e.g., as TA4J `BarSeries`).
    *   `SignalAnalysisService`: Contains the core trading logic, including calculation of technical indicators and generation of BUY/SELL signals based on the active strategy.
    *   `StrategyConfigService`: Manages the current trading strategy parameters. It loads initial parameters from configuration and allows them to be updated by the `BacktestAgent`.
    *   `CsvExporterService`: Handles logging of trading signals to a CSV file.
*   **Agents (Conceptual Spring AI MCP Agents)**:
    *   `ForexScannerAgent`: Orchestrates the process of fetching market data, applying signal analysis using the current strategy, and logging signals.
    *   `MarketSchedulerAgent`: Uses Spring's `@Scheduled` annotation to trigger the `ForexScannerAgent` at regular 15-minute intervals.
    *   `BacktestAgent`: Periodically performs backtesting of different strategy parameter sets on historical data, identifies optimal parameters, and updates the `StrategyConfigService`.
*   **Controller**:
    *   `McpController`: Provides a RESTful API endpoint (`POST /mcp/scan`) for on-demand triggering of the `ForexScannerAgent`.
*   **Configuration**:
    *   `application.yml`: Centralized configuration for API keys, default trading parameters, scheduling CRON expressions, backtesting settings, etc.
*   **Data Models**: Kotlin data classes representing OHLCV data, trading signals, strategy parameters, etc.

## 4. Configuration

Before running the application, you need to configure API keys and other settings in `src/main/resources/application.yml`:

*   **Twelve Data API Key**:
    ```yaml
    twelve:
      api:
        key: YOUR_TWELVE_DATA_API_KEY_HERE
    ```
    Replace `YOUR_TWELVE_DATA_API_KEY_HERE` with your actual API key from [twelvedata.com](https://twelvedata.com).

*   **OpenAI API Key (Spring AI - if used directly)**:
    ```yaml
    spring.ai:
      openai:
        api-key: YOUR_OPENAI_API_KEY_HERE
    ```
    This is needed if Spring AI features requiring it are actively used by the MCP agents.

*   **Other Parameters**:
    *   Default symbols for scheduled scans (`trading.defaults.symbols`).
    *   Default interval for scans (`trading.defaults.interval`).
    *   Initial strategy parameters (`trading.strategy.*`). These serve as a starting point before the `BacktestAgent` performs recalibration.
    *   CRON expressions for scheduled market scans and backtesting (`scheduling.marketScanCron`, `scheduling.backtestCron`).
    *   CSV file path (`csv.filePath`).
    *   Backtesting parameters (`backtesting.*`).

## 5. API Endpoints

### 5.1. Trigger Manual Scan

*   **Endpoint**: `POST /mcp/scan`
*   **Description**: Manually triggers a market scan for the specified symbols and interval.
*   **Request Body (JSON)**:
    ```json
    {
      "symbols": ["EUR/USD", "GBP/USD"],
      "interval": "15min"
    }
    ```
    *   `symbols`: A list of currency pairs (e.g., "EUR/USD", "USD/JPY").
    *   `interval`: The time interval for data analysis (e.g., "1min", "5min", "15min", "1h", "1day").
*   **Success Response (200 OK)**:
    *   Returns a list of generated `TradingSignal` objects (which might be empty if no signals are found).
    ```json
    [
      {
        "timestamp": "2023-10-27T10:30:00Z",
        "symbol": "EUR/USD",
        "direction": "BUY",
        "entry": 1.0550,
        "stopLoss": 1.0445,
        "takeProfit": 1.0760,
        "riskRewardRatio": "1:2",
        "rsi": 35.5,
        "sma20": 1.0540,
        "sma50": 1.0535,
        "fibLevel": 0.618,
        "outcome": null
      }
      // ... more signals
    ]
    ```
*   **Error Responses**:
    *   `400 Bad Request`: If the request payload is invalid (e.g., empty symbols list, blank interval).
    *   `500 Internal Server Error`: If an unexpected error occurs during the scan.

## 6. How to Build and Run

### Prerequisites
*   Java Development Kit (JDK) 17 or later.
*   Gradle (the project includes a Gradle wrapper `./gradlew`).
*   Configured API keys in `application.yml`.

### Build
To build the application and run tests:
```bash
./gradlew clean build
```

### Run
To run the application:
```bash
./gradlew bootRun
```
Alternatively, you can run the executable JAR from the `build/libs/` directory:
```bash
java -jar build/libs/forex-trading-mcp-0.0.1-SNAPSHOT.jar
```
The application will start, and scheduled tasks will begin executing based on their CRON expressions. The API will be available at `http://localhost:8080`.

## 7. Backtesting & Strategy Recalibration

The `BacktestAgent` is configured to run periodically (e.g., weekly). It fetches historical data for the last 30 days (configurable) and tests a predefined set of strategy parameter variants (RSI thresholds, SMA periods).

The agent simulates trades for each variant (using a simplified model) and calculates a win rate. The parameter set with the highest win rate is then chosen as the new optimal strategy, and the `StrategyConfigService` is updated. This means the `ForexScannerAgent` will use these new, recalibrated parameters for subsequent live scans.

Recalibration details and chosen parameters are logged by the `BacktestAgent`.

## 8. Future Enhancements (Potential)

*   More sophisticated Spring AI MCP agent integration.
*   Advanced backtesting engine (event-driven, proper equity tracking, drawdown calculation, etc.).
*   More dynamic Stop Loss / Take Profit calculation (e.g., ATR-based).
*   Persistent storage for strategy parameters and backtest results.
*   Expanded set of technical indicators and strategy variations.
*   Real-time trade execution capabilities (integration with a broker API).
*   More comprehensive error handling and resilience.
```
