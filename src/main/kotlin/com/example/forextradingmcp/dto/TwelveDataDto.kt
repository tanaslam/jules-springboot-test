package com.example.forextradingmcp.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class Meta(
    val symbol: String,
    val interval: String,
    @JsonProperty("currency_base") val currencyBase: String,
    @JsonProperty("currency_quote") val currencyQuote: String,
    val type: String
)

data class ValueEntry(
    val datetime: String,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String
)

data class TwelveDataResponse(
    val meta: Meta,
    val values: List<ValueEntry>,
    val status: String,
    val message: String? = null
)
