package com.example.currencyconverter.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Нормализованный снимок курсов: всё приведено к базе EUR. */
data class RatesSnapshot(
    val rates: Map<String, Double>,
    val source: String
)

class CurrencyRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private data class Source(
        val name: String,
        val url: String,
        val parse: (String) -> Map<String, Double>
    )

    // Порядок = приоритет. Основной первый, дальше фоллбэки.
    private val sources = listOf(
        Source(
            "ExchangeRate-API",
            "https://open.er-api.com/v6/latest/EUR"
        ) { body ->
            val o = gson.fromJson(body, JsonObject::class.java)
            check(o.get("result")?.asString == "success") { "er-api: result != success" }
            parseRates(o.getAsJsonObject("rates"))
        },
        Source(
            "Fawaz Ahmed",
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/eur.json"
        ) { body ->
            val o = gson.fromJson(body, JsonObject::class.java)
            // Коды в нижнем регистре → приводим к верхнему как у остальных источников
            parseRates(o.getAsJsonObject("eur")).mapKeys { it.key.uppercase() }
        },
        Source(
            "Frankfurter (ECB)",
            "https://api.frankfurter.dev/v1/latest?base=EUR"
        ) { body ->
            val o = gson.fromJson(body, JsonObject::class.java)
            // Frankfurter не включает саму базу EUR — добавляем EUR=1
            parseRates(o.getAsJsonObject("rates")).toMutableMap().apply { put("EUR", 1.0) }
        }
    )

    /** Пробует источники по очереди, возвращает первый успешный. */
    suspend fun getRates(): Result<RatesSnapshot> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (src in sources) {
            try {
                val req = Request.Builder().url(src.url).build()
                client.newCall(req).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code}" }
                    val body = resp.body?.string() ?: error("пустой ответ")
                    val rates = src.parse(body)
                    check(rates.size >= 2) { "слишком мало курсов" }
                    return@withContext Result.success(RatesSnapshot(rates, src.name))
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: Exception("Нет доступных источников"))
    }

    private fun parseRates(obj: JsonObject?): Map<String, Double> {
        if (obj == null) return emptyMap()
        val map = HashMap<String, Double>(obj.size())
        for ((k, v) in obj.entrySet()) {
            try {
                val d = v.asDouble
                if (d > 0) map[k] = d
            } catch (_: Exception) { /* пропускаем нечисловые поля */ }
        }
        return map
    }
}
