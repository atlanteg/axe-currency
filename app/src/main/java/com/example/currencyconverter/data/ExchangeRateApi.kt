package com.example.currencyconverter.data

import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApi {
    // open.er-api.com — free, no key, 160+ currencies, ECB-aligned rates
    @GET("v6/latest/{base}")
    suspend fun getLatestRates(@Path("base") base: String): ExchangeRateResponse
}
