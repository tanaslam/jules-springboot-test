package com.example.forextrading.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfig {

    @Bean("applicationScope") // Named "applicationScope" as anticipated by MarketSchedulerAgent
    fun applicationScope(): CoroutineScope {
        // SupervisorJob ensures that if one child coroutine fails, others are not cancelled.
        // Dispatchers.Default is suitable for CPU-bound or general tasks that might involve some IO.
        // For highly IO-bound tasks, Dispatchers.IO could be considered, but Default is a good starting point.
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
