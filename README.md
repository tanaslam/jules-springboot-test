# Forex Trading MCP Application

This is a Spring Boot application using Kotlin that implements a Model Context Protocol (MCP)
based multi-agent system for Forex trading signal generation and strategy backtesting.

## Features
- Fetches Forex market data (e.g., 15-minute intervals) from Twelve Data API.
- Generates trading signals based on RSI and SMA (e.g., SMA20, SMA50) indicators.
- Enforces a configurable minimum risk/reward ratio (default 1:2).
- Provides a REST API endpoint (`POST /api/mcp/scan`) to trigger on-demand scans with custom parameters.
- Includes a scheduler (`MarketSchedulerAgent`) to run scans automatically (e.g., every 15 minutes with default parameters).
- Supports backtesting of trading strategies against historical data (`BacktestAgent`).
- Exports generated signals and backtest results to CSV files.
- Configurable API key (via `application.yaml` or `TWELVE_DATA_API_KEY` environment variable).

## Prerequisites
- Docker installed and running.
- A Twelve Data API key. You can get one from [twelvedata.com](https://twelvedata.com/).

## Using Docker

### Building the Docker Image
To build the Docker image for this application, navigate to the project root directory
(where the `Dockerfile` is located) and run the following command:

```sh
docker build -t forex-trading-mcp .
```
This will create an image tagged `forex-trading-mcp`.

### Running the Docker Container
Once the image is built, you can run the application as a Docker container.
You **must** provide your Twelve Data API key using the `TWELVE_DATA_API_KEY`
environment variable.

```sh
docker run -d -p 8080:8080 -e TWELVE_DATA_API_KEY="YOUR_API_KEY_HERE" --name forex-mcp-app forex-trading-mcp
```

Breakdown of the command:
- `-d`: Runs the container in detached mode (in the background).
- `-p 8080:8080`: Maps port 8080 of the host to port 8080 of the container (where the Spring Boot app runs).
- `-e TWELVE_DATA_API_KEY="YOUR_API_KEY_HERE"`: Sets the environment variable for the Twelve Data API key. **Replace `YOUR_API_KEY_HERE` with your actual key.**
- `--name forex-mcp-app`: Assigns a name to the running container for easier management.
- `forex-trading-mcp`: Specifies the Docker image to use.

### Accessing the Application
Once the container is running, the application's API will be accessible:
- **REST API for on-demand scans:** `POST http://localhost:8080/api/mcp/scan`
  - Body (example):
    ```json
    {
      "currencyPairs": ["EUR/USD", "GBP/USD"],
      "interval": "15min",
      "outputSize": 100,
      "strategyParameters": {
        "rsiPeriod": 14,
        "rsiLow": 30,
        "rsiHigh": 70,
        "smaShortPeriod": 20,
        "smaLongPeriod": 50,
        "stopLossLookBackPeriod": 10,
        "minRiskRewardRatio": 2.0
      }
    }
    ```

### Viewing Logs
To view the logs from the running container:
```sh
docker logs forex-mcp-app
```
To follow the logs in real-time:
```sh
docker logs -f forex-mcp-app
```

### Stopping and Removing the Container
To stop the container:
```sh
docker stop forex-mcp-app
```
To remove the stopped container:
```sh
docker rm forex-mcp-app
```

## Configuration
- The Twelve Data API key **must** be set.
  - When using Docker: via the `TWELVE_DATA_API_KEY` environment variable.
  - If running locally (e.g., via IDE or `./gradlew bootRun`): by editing the `src/main/resources/application.yaml` file and replacing `YOUR_API_KEY_HERE` with your actual key.
- Default strategy parameters are defined within the respective agent classes (e.g., `MarketSchedulerAgent` uses defaults from `StrategyParameters`). These can be overridden for on-demand scans via the `/api/mcp/scan` endpoint's request body.
- Backtest results and signals are saved to CSV files in the `backtest_results` directory (relative to where the application is run, or inside the container if run via Docker without volume mounts for this directory).

## Development

### Building from Source
```sh
./gradlew build
```

### Running Locally
Ensure you have JDK 17 installed.
1. Set your API key in `src/main/resources/application.yaml`.
2. Run the application using:
   ```sh
   ./gradlew bootRun
   ```
The application will be available at `http://localhost:8080`.
---

This README provides a good overview and necessary instructions for users.
```
