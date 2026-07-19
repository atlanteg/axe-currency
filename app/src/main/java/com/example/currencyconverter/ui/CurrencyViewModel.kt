package com.example.currencyconverter.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.currencyconverter.data.CurrencyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    val currencyItems: List<CurrencyItem> = emptyList(),
    val activeCurrency: String = "EUR",
    val isLoading: Boolean = true,
    val lastUpdated: String = "",
    val source: String = "",
    val totalCurrencies: Int = 0,
    val error: String? = null,
    val decimalPlaces: Int = 0
)

class CurrencyViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("axe_prefs", Context.MODE_PRIVATE)
    private val repository = CurrencyRepository()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var allRates: Map<String, Double> = emptyMap()
    private val displayCurrencies = loadCurrencies()
    private var activeCurrency = displayCurrencies.firstOrNull() ?: "EUR"
    private var activeAmount = 1.0
    private var refreshJob: Job? = null

    private fun loadCurrencies(): MutableList<String> {
        val saved = prefs.getString("currencies", null)
        return if (saved != null) saved.split(",").toMutableList()
        else mutableListOf("EUR", "USD", "RSD", "GEL", "ILS", "TJS", "CHF")
    }

    private fun saveCurrencies() {
        prefs.edit().putString("currencies", displayCurrencies.joinToString(",")).apply()
    }

    fun getDecimalPlaces(): Int = prefs.getInt("decimal_places", 0)

    fun setDecimalPlaces(n: Int) {
        prefs.edit().putInt("decimal_places", n).apply()
        _state.value = _state.value.copy(decimalPlaces = n)
    }

    // 0 = Авто (цепочка с резервом), 1..3 = принудительно конкретный источник
    fun getSourceMode(): Int = prefs.getInt("rate_source", 0)

    fun setSourceMode(mode: Int) {
        prefs.edit().putInt("rate_source", mode).apply()
        refresh()  // сразу перезапрашиваем с новым источником
    }

    init {
        _state.value = _state.value.copy(decimalPlaces = getDecimalPlaces())
        refresh()
        startAutoRefresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repository.getRates(getSourceMode()).fold(
                onSuccess = { snap ->
                    allRates = snap.rates
                    // Показываем ВРЕМЯ НАШЕГО запроса — чтобы refresh был виден
                    val fetchTime = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                        .format(Date())
                    _state.value = _state.value.copy(
                        isLoading = false,
                        lastUpdated = fetchTime,
                        source = snap.source,
                        totalCurrencies = snap.rates.size,
                        currencyItems = buildItems()
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Network error"
                    )
                }
            )
        }
    }

    fun setActiveAmount(currency: String, amount: Double) {
        activeCurrency = currency
        activeAmount = amount
        if (allRates.isEmpty()) return
        _state.value = _state.value.copy(
            activeCurrency = currency,
            currencyItems = buildItems()
        )
    }

    fun clearAll() {
        activeAmount = 0.0
        // Пустой activeCurrency → адаптер вызовет notifyDataSetChanged() → все EditText обнулятся
        _state.value = _state.value.copy(
            activeCurrency = "",
            currencyItems = buildItems()
        )
        // Восстанавливаем activeCurrency без emit — пользователь выберет поле сам
        activeCurrency = displayCurrencies.firstOrNull() ?: "EUR"
    }

    fun addCurrency(code: String) {
        if (!displayCurrencies.contains(code)) {
            displayCurrencies.add(code)
            saveCurrencies()
            _state.value = _state.value.copy(currencyItems = buildItems())
        }
    }

    fun removeCurrency(code: String) {
        if (displayCurrencies.size <= 2) return
        displayCurrencies.remove(code)
        if (activeCurrency == code) activeCurrency = displayCurrencies.first()
        saveCurrencies()
        _state.value = _state.value.copy(currencyItems = buildItems())
    }

    fun reorderCurrencies(newOrder: List<String>) {
        displayCurrencies.clear()
        displayCurrencies.addAll(newOrder)
        saveCurrencies()
    }

    fun getAvailableCurrencies(): List<CurrencyInfo> =
        allRates.keys
            .filter { it !in displayCurrencies }
            .sorted()
            .map { CurrencyInfo(it, currencyName(it)) }

    private fun buildItems(): List<CurrencyItem> {
        if (allRates.isEmpty()) return emptyList()
        val activeRateInEur = allRates[activeCurrency] ?: 1.0
        val amountInEur = if (activeRateInEur != 0.0) activeAmount / activeRateInEur else 0.0
        return displayCurrencies.mapNotNull { code ->
            val rateInEur = allRates[code] ?: return@mapNotNull null
            val converted = amountInEur * rateInEur
            val rateText = if (code == "EUR") "Base currency"
                          else "1 EUR = ${fmtRate(rateInEur)} $code"
            CurrencyItem(
                code = code,
                name = currencyName(code),
                flag = currencyFlag(code),
                symbol = currencySymbol(code),
                amount = converted,
                rateText = rateText
            )
        }
    }

    private fun fmtRate(rate: Double): String = when {
        rate >= 10000 -> "%.0f".format(rate)
        rate >= 100   -> "%.2f".format(rate)
        rate >= 1     -> "%.4f".format(rate)
        else          -> "%.6f".format(rate)
    }

    private fun formatDate(utc: String): String = try {
        val inFmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
        val outFmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        outFmt.format(inFmt.parse(utc)!!)
    } catch (_: Exception) { utc }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(30 * 60 * 1000L)
                refresh()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }

    companion object {
        fun currencyName(code: String) = NAMES[code] ?: code
        fun currencyFlag(code: String) = FLAGS[code] ?: "🌐"
        fun currencySymbol(code: String) = SYMBOLS[code] ?: code

        val SYMBOLS = mapOf(
            "EUR" to "€",  "USD" to "$",  "GBP" to "£",  "JPY" to "¥",
            "CHF" to "Fr", "CAD" to "C$", "AUD" to "A$", "NZD" to "NZ$",
            "CNY" to "¥",  "HKD" to "HK$","SGD" to "S$", "RSD" to "din.",
            "GEL" to "₾",  "ILS" to "₪",  "TJS" to "SM", "RUB" to "₽",
            "UAH" to "₴",  "TRY" to "₺",  "PLN" to "zł", "CZK" to "Kč",
            "HUF" to "Ft", "RON" to "lei","SEK" to "kr", "NOK" to "kr",
            "DKK" to "kr", "ISK" to "kr", "MXN" to "MX$","BRL" to "R$",
            "ARS" to "AR$","KRW" to "₩",  "THB" to "฿",  "IDR" to "Rp",
            "MYR" to "RM", "PHP" to "₱",  "VND" to "₫",  "INR" to "₹",
            "PKR" to "₨",  "EGP" to "E£", "ZAR" to "R",  "NGN" to "₦",
            "SAR" to "SR", "AED" to "AED","QAR" to "QR", "KWD" to "KD",
            "BHD" to "BD", "OMR" to "RO", "JOD" to "JD", "AMD" to "֏",
            "AZN" to "₼",  "KZT" to "₸",  "UZS" to "soʻm","MDL" to "L",
            "BGN" to "лв", "MKD" to "ден","ALL" to "L",  "BYN" to "Br",
            "CLP" to "CL$","COP" to "CO$","PEN" to "S/", "TWD" to "NT$",
            "GHS" to "GH₵","MAD" to "MAD","DZD" to "DA", "TND" to "DT",
            "ETB" to "Br", "TZS" to "TSh","KGS" to "с",  "GEL" to "₾"
        )

        val NAMES = mapOf(
            "EUR" to "Euro", "USD" to "US Dollar", "GBP" to "British Pound",
            "JPY" to "Japanese Yen", "CHF" to "Swiss Franc", "CAD" to "Canadian Dollar",
            "AUD" to "Australian Dollar", "NZD" to "New Zealand Dollar",
            "CNY" to "Chinese Yuan", "HKD" to "Hong Kong Dollar",
            "SGD" to "Singapore Dollar", "RSD" to "Serbian Dinar",
            "GEL" to "Georgian Lari", "ILS" to "Israeli Shekel",
            "TJS" to "Tajikistani Somoni", "RUB" to "Russian Ruble",
            "UAH" to "Ukrainian Hryvnia", "TRY" to "Turkish Lira",
            "PLN" to "Polish Zloty", "CZK" to "Czech Koruna",
            "HUF" to "Hungarian Forint", "RON" to "Romanian Leu",
            "SEK" to "Swedish Krona", "NOK" to "Norwegian Krone",
            "DKK" to "Danish Krone", "ISK" to "Icelandic Krona",
            "MXN" to "Mexican Peso", "BRL" to "Brazilian Real",
            "ARS" to "Argentine Peso", "KRW" to "South Korean Won",
            "THB" to "Thai Baht", "IDR" to "Indonesian Rupiah",
            "MYR" to "Malaysian Ringgit", "PHP" to "Philippine Peso",
            "VND" to "Vietnamese Dong", "INR" to "Indian Rupee",
            "PKR" to "Pakistani Rupee", "EGP" to "Egyptian Pound",
            "ZAR" to "South African Rand", "NGN" to "Nigerian Naira",
            "SAR" to "Saudi Riyal", "AED" to "UAE Dirham",
            "QAR" to "Qatari Riyal", "KWD" to "Kuwaiti Dinar",
            "BHD" to "Bahraini Dinar", "OMR" to "Omani Rial",
            "JOD" to "Jordanian Dinar", "AMD" to "Armenian Dram",
            "AZN" to "Azerbaijani Manat", "KZT" to "Kazakhstani Tenge",
            "UZS" to "Uzbekistani Som", "MDL" to "Moldovan Leu",
            "BGN" to "Bulgarian Lev", "MKD" to "North Macedonian Denar",
            "ALL" to "Albanian Lek", "BYN" to "Belarusian Ruble",
            "CLP" to "Chilean Peso", "COP" to "Colombian Peso",
            "PEN" to "Peruvian Sol", "TWD" to "Taiwan Dollar",
            "GHS" to "Ghanaian Cedi", "MAD" to "Moroccan Dirham",
            "DZD" to "Algerian Dinar", "TND" to "Tunisian Dinar",
            "ETB" to "Ethiopian Birr", "TZS" to "Tanzanian Shilling",
            "KGS" to "Kyrgyz Som", "AFN" to "Afghan Afghani"
        )

        val FLAGS = mapOf(
            "EUR" to "🇪🇺", "USD" to "🇺🇸", "GBP" to "🇬🇧", "JPY" to "🇯🇵",
            "CHF" to "🇨🇭", "CAD" to "🇨🇦", "AUD" to "🇦🇺", "NZD" to "🇳🇿",
            "CNY" to "🇨🇳", "HKD" to "🇭🇰", "SGD" to "🇸🇬", "RSD" to "🇷🇸",
            "GEL" to "🇬🇪", "ILS" to "🇮🇱", "TJS" to "🇹🇯", "RUB" to "🇷🇺",
            "UAH" to "🇺🇦", "TRY" to "🇹🇷", "PLN" to "🇵🇱", "CZK" to "🇨🇿",
            "HUF" to "🇭🇺", "RON" to "🇷🇴", "SEK" to "🇸🇪", "NOK" to "🇳🇴",
            "DKK" to "🇩🇰", "ISK" to "🇮🇸", "MXN" to "🇲🇽", "BRL" to "🇧🇷",
            "ARS" to "🇦🇷", "KRW" to "🇰🇷", "THB" to "🇹🇭", "IDR" to "🇮🇩",
            "MYR" to "🇲🇾", "PHP" to "🇵🇭", "VND" to "🇻🇳", "INR" to "🇮🇳",
            "PKR" to "🇵🇰", "EGP" to "🇪🇬", "ZAR" to "🇿🇦", "NGN" to "🇳🇬",
            "SAR" to "🇸🇦", "AED" to "🇦🇪", "QAR" to "🇶🇦", "KWD" to "🇰🇼",
            "BHD" to "🇧🇭", "OMR" to "🇴🇲", "JOD" to "🇯🇴", "AMD" to "🇦🇲",
            "AZN" to "🇦🇿", "KZT" to "🇰🇿", "UZS" to "🇺🇿", "MDL" to "🇲🇩",
            "BGN" to "🇧🇬", "MKD" to "🇲🇰", "ALL" to "🇦🇱", "BYN" to "🇧🇾",
            "CLP" to "🇨🇱", "COP" to "🇨🇴", "PEN" to "🇵🇪", "TWD" to "🇹🇼",
            "GHS" to "🇬🇭", "MAD" to "🇲🇦", "DZD" to "🇩🇿", "TND" to "🇹🇳",
            "ETB" to "🇪🇹", "TZS" to "🇹🇿", "KGS" to "🇰🇬", "AFN" to "🇦🇫"
        )
    }
}
