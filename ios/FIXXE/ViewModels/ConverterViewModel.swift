import Foundation
import SwiftUI

@MainActor
final class ConverterViewModel: ObservableObject {
    // Настройки (персист в UserDefaults)
    @Published var currencies: [String] { didSet { UserDefaults.standard.set(currencies, forKey: "currencies") } }
    @Published var decimals: Int { didSet { UserDefaults.standard.set(decimals, forKey: "decimals") } }
    @Published var sourceMode: Int { didSet { UserDefaults.standard.set(sourceMode, forKey: "source") } }
    @Published var langVersion = 0   // инкремент → перерисовка при смене языка

    // Состояние курсов
    @Published var rates: [String: Double] = [:]
    @Published var currentSource = ""
    @Published var lastUpdated = ""
    @Published var isLoading = false
    @Published var errorText: String?

    // Активная валюта и ввод
    @Published var activeCode: String
    @Published var activeAmount: Double = 1
    @Published var inputText: String = "1"

    // Доступность валют по источникам
    @Published var sourceCodes: [String: Set<String>] = [:]

    init() {
        let saved = UserDefaults.standard.stringArray(forKey: "currencies")
        var list = saved ?? CurrencyData.defaults
        var seen = Set<String>(); list = list.filter { seen.insert($0).inserted }   // дедуп
        currencies = list
        decimals = UserDefaults.standard.integer(forKey: "decimals")
        sourceMode = UserDefaults.standard.integer(forKey: "source")
        activeCode = list.first ?? "EUR"
    }

    // MARK: курсы

    func refresh() async {
        isLoading = true; errorText = nil
        defer { isLoading = false }
        do {
            let r = try await RatesService.fetchRates(preferred: sourceMode)
            rates = r.rates; currentSource = r.source
            let df = DateFormatter()
            df.dateFormat = "dd MMM yyyy HH:mm"
            lastUpdated = df.string(from: Date())
        } catch {
            errorText = L10n.t("network_error")
        }
    }

    func loadSourceCodes() async {
        if sourceCodes.isEmpty { sourceCodes = await RatesService.fetchSourceCodes() }
    }

    // MARK: расчёты (кросс-курс через EUR, динамическая база)

    var amountInEur: Double {
        let r = rates[activeCode] ?? 1
        return r != 0 ? activeAmount / r : 0
    }

    func converted(_ code: String) -> Double { amountInEur * (rates[code] ?? 0) }

    var pivot: String { rates[activeCode] != nil ? activeCode : "EUR" }

    func rateLine(_ code: String) -> String {
        if code == pivot { return L10n.t("base_currency") }
        guard let r = rates[code], let p = rates[pivot], p != 0 else { return "" }
        return "1 \(pivot) = \(SmartFormat.rate(r / p)) \(code)"
    }

    // MARK: действия

    func setActive(_ code: String) {
        guard code != activeCode else { return }
        activeAmount = converted(code)
        activeCode = code
        inputText = SmartFormat.amount(activeAmount, decimals: decimals)
    }

    func inputChanged(_ text: String) {
        inputText = text
        activeAmount = SmartFormat.parse(text)
    }

    func clearAll() {
        activeAmount = 0
        inputText = "0"
    }

    func add(_ code: String) { if !currencies.contains(code) { currencies.append(code) } }

    func remove(_ code: String) {
        guard currencies.count > 2 else { return }
        currencies.removeAll { $0 == code }
        if activeCode == code, let f = currencies.first { activeCode = f; activeAmount = converted(f) }
    }

    func move(from: IndexSet, to: Int) { currencies.move(fromOffsets: from, toOffset: to) }

    // Какие источники (индексы 0..2) содержат код
    func sourcesWith(_ code: String) -> [Int] {
        RatesService.sources.enumerated().compactMap { i, s in
            (sourceCodes[s.name]?.contains(code) ?? false) ? i : nil
        }
    }

    var allCodesUnion: [String] {
        var u = Set(rates.keys)
        for s in sourceCodes.values { u.formUnion(s) }
        return u.sorted()
    }

    func switchSourceAndAdd(_ code: String, sourceIdx: Int) async {
        sourceMode = sourceIdx + 1
        add(code)
        await refresh()
    }
}
