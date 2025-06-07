package com.example.forextradingmcp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling // Import this

@SpringBootApplication
@EnableScheduling // Add this annotation
class ForexTradingMcpApplication

fun main(args: Array<String>) {
    runApplication<ForexTradingMcpApplication>(*args)
}
