import Foundation

/// Локализация с мгновенным переключением (та же модель, что в Android/PWA):
/// словарь 40 языков из i18n.json, выбранный язык ?? системный ?? en.
enum L10n {
    private static let table: [String: [String: String]] = {
        guard let url = Bundle.main.url(forResource: "i18n", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: [String: String]]
        else { return [:] }
        return obj
    }()

    static var chosenLang: String? {
        get { UserDefaults.standard.string(forKey: "lang") }
        set {
            if let v = newValue { UserDefaults.standard.set(v, forKey: "lang") }
            else { UserDefaults.standard.removeObject(forKey: "lang") }
        }
    }

    /// Язык: выбранный → первый поддерживаемый системный → en (неизвестный язык → English)
    static func resolved() -> String {
        if let c = chosenLang, table[c] != nil { return c }
        for pref in Locale.preferredLanguages {
            let base = String(pref.prefix(while: { $0 != "-" && $0 != "_" })).lowercased()
            if table[base] != nil { return base }
        }
        return "en"
    }

    static var isRTL: Bool { ["ar", "he", "fa"].contains(resolved()) }

    /// t("key", args...) — подстановка %1$s/%2$s/%1$d как в Android/вебе
    static func t(_ key: String, _ args: CustomStringConvertible...) -> String {
        let lang = resolved()
        var s = table[lang]?[key] ?? table["en"]?[key] ?? key
        for (i, a) in args.enumerated() {
            s = s.replacingOccurrences(of: "%\(i + 1)$s", with: a.description)
            s = s.replacingOccurrences(of: "%\(i + 1)$d", with: a.description)
        }
        return s
    }
}
