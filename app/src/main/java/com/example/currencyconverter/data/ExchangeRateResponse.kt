package com.example.currencyconverter.data

import com.google.gson.annotations.SerializedName

data class ExchangeRateResponse(
    @SerializedName("result") val result: String,
    @SerializedName("base_code") val baseCode: String,
    @SerializedName("time_last_update_utc") val lastUpdateUtc: String,
    @SerializedName("rates") val rates: Map<String, Double>
)
