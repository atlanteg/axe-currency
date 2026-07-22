import Foundation

/// Умное округление вверх (как в Android/PWA): выбранная точность — минимум,
/// но если она искажает значение больше чем на 2%, добавляем знаки,
/// чтобы мелкие суммы не превращались в «1».
enum SmartFormat {
    static func amount(_ v: Double, decimals n: Int) -> String {
        if v <= 0 { return n == 0 ? "0" : String(format: "%.\(n)f", 0.0) }
        let tol = 0.02
        var d = n
        while d < 8 {
            let f = pow(10.0, Double(d))
            let ceiled = (v * f - 1e-9).rounded(.up) / f
            if ceiled - v <= tol * v { break }
            d += 1
        }
        let f = pow(10.0, Double(d))
        let ceiled = (v * f - 1e-9).rounded(.up) / f
        return d == 0 ? String(Int(ceiled.rounded())) : String(format: "%.\(d)f", ceiled)
    }

    static func rate(_ r: Double) -> String {
        if r >= 10000 { return String(format: "%.0f", r) }
        if r >= 100 { return String(format: "%.2f", r) }
        if r >= 1 { return String(format: "%.4f", r) }
        return String(format: "%.6f", r)
    }

    static func parse(_ s: String) -> Double {
        Double(s.replacingOccurrences(of: ",", with: ".")) ?? 0
    }
}
