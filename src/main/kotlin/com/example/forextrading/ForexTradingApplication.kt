package com.example.forextrading

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling // Import this

@SpringBootApplication
@EnableScheduling // Add this annotation
class ForexTradingApplication

fun main(args: Array<String>) {
    runApplication<ForexTradingApplication>(*args)
}
