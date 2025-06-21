package com.example.forextrading.service

import com.example.forextrading.model.StrategyParameters

interface StrategyConfigService {
    fun getCurrentParameters(): StrategyParameters
    fun updateParameters(newParams: StrategyParameters)
}
