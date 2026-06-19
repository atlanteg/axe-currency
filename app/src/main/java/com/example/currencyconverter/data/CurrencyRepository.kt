package com.example.currencyconverter.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class CurrencyRepository {

    private val api: ExchangeRateApi

    init {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl("https://open.er-api.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApi::class.java)
    }

    suspend fun getRates(base: String = "EUR"): Result<ExchangeRateResponse> = runCatching {
        val response = api.getLatestRates(base)
        check(response.result == "success") { "API error: ${response.result}" }
        response
    }
}
