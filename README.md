# FIXXE — Currency Converter

Real-time multi-currency converter for **Android** and as an installable **PWA** (web app).
Mid-market rates, 166+ currencies incl. crypto, 40 languages. Inspired by XE Currency.
Built entirely with CLI tools — no Android Studio required.

**Live web app:** https://fixe.l23.xyz (install to home screen on iOS/Android)
**Releases (APK):** https://github.com/atlanteg/axe-currency/releases/latest

## Features

- **Live mid-market rates** with a 3-source fallback chain (see [Rate sources](#rate-sources))
- **166+ currencies** (fiat) — and **300+ incl. crypto & metals** when using the F.A. source
- **Real-time conversion** — type in any field, all others update instantly
- **Dynamic base** — the rate line is computed relative to the currency you're editing, not a fixed one
- **Smart rounding** — chosen precision (0/1/2/4) is a minimum; if it would distort a value by >2%,
  that row automatically shows more decimals (e.g. small/strong currencies never collapse to `1`)
- **Source-aware currency picker** — search across all sources or filter by one; colored dots show
  which sources have each currency; find a coin even on a source that lacks it and get a one-tap
  prompt to switch source and add it
- **Forced source** selection in Settings (Auto / ExchangeRate-API / F.A. / Frankfurter), others stay as fallback
- **40 languages** with automatic device-language detection (RTL for Arabic/Hebrew/Persian)
- **Drag to reorder**, delete-with-confirmation, CLEAR, searchable add dialog
- **Persistent** currencies, order, precision, source and language across restarts
- **Android:** in-app auto-update via GitHub Releases; settings backed up via Google Backup
- **PWA:** installable, works offline (app shell cached), local-only settings

## Rate sources

Multi-source fallback chain (all normalized to EUR base), tried in order; the current source is
shown in the header:

1. **ExchangeRate-API** (`open.er-api.com`) — primary. Open Access tier **requires attribution**:
   the "Rates By Exchange Rate API" link back to [exchangerate-api.com](https://www.exchangerate-api.com)
   shown in the footer.
2. **F.A.** — Fawaz Ahmed currency-api (jsDelivr + `currency-api.pages.dev` mirror). CC0 public
   domain, no attribution. 300+ currencies incl. crypto/metals.
3. **Frankfurter** (`api.frankfurter.dev`) — MIT-licensed API, data from the European Central Bank (ECB).

All are free, no API key, CORS-enabled. Rates update roughly once a day; the difference from XE is
usually under 0.5% — these are mid-market reference rates, not real-time quotes.

## Repository layout

```
app/                         # Android app (Kotlin)
  src/main/java/com/example/currencyconverter/
    MainActivity.kt          # UI: RecyclerView, ItemTouchHelper, dialogs, language/source pickers
    UpdateChecker.kt         # In-app auto-update via GitHub Releases API
    data/CurrencyRepository.kt  # OkHttp multi-source fetch + per-source currency lists
    ui/CurrencyViewModel.kt  # AndroidViewModel: state, rates, persistence, source availability
    ui/CurrencyAdapter.kt    # Row binding, smart rounding, drag, double-tap select-all
  src/main/res/values*/      # strings.xml in 40 languages (default = English)
web/                         # PWA (vanilla JS/CSS, no build step)
  index.html  app.js  styles.css  sw.js  manifest.webmanifest
  i18n.js                    # auto-generated from Android strings.xml (40 languages)
  currency-data.js           # auto-generated from CurrencyViewModel.kt (symbols/names/flags)
  icons/
release.sh                   # single release command: Android + PWA in sync, auto-deploy
```

**Android stack:** Kotlin, ViewBinding, AndroidViewModel + StateFlow, OkHttp 4 + Gson,
RecyclerView + DiffUtil + ItemTouchHelper, Material Components, SharedPreferences,
per-app locales via `AppCompatDelegate.setApplicationLocales`.

## Exchange rate logic

Rates are fetched relative to EUR (the pivot). Any pair is a cross-rate `rate[target] / rate[source]`
computed from a single snapshot — no extra requests when switching the active currency:

```
amountInEUR = userInput / rate[activeCurrency]
result[X]   = amountInEUR × rate[X]
```

The pivot is an implementation detail; the displayed rate line uses the active currency as base.

## Building the Android app

Requirements: macOS, JDK 17 (`brew install openjdk@17`), Android SDK command-line tools in
`~/android-sdk/cmdline-tools/latest/`, `platforms;android-34` + `build-tools;34.0.0`.

```bash
export JAVA_HOME=$(brew --prefix openjdk@17)
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

## Releasing (always use the script)

`release.sh` is the **single source of truth** — it derives everything from one number `N` so the
Android `versionCode`, `versionName`, the git tag, the release body, the PWA `APP_VERSION` and the
service-worker cache all stay in sync (a mismatch would cause an endless in-app "update" loop):

```bash
./release.sh            # auto-increment
./release.sh 28         # release version 28 explicitly
./release.sh 28 "note"  # with a custom release note
```

It builds the APK, bumps the PWA version + SW cache, commits & pushes, creates GitHub release
`vN` with asset `FIXXE-vN.apk`, and **auto-deploys the PWA** to the VM (best-effort).

## PWA hosting

The web app is served by nginx on the VM `edge2il` (`127.0.0.1:8080`) behind a Cloudflare Tunnel
(`cloudflared`, systemd) mapped to `fixe.l23.xyz`. `release.sh` redeploys it automatically; a manual
deploy is `tar → scp → extract → systemctl reload nginx`. Assets carry `Cache-Control: no-cache`
so updates propagate immediately; installed PWAs refresh on the next open via the SW cache bump.

## License

MIT © Atlanteg. Displayed rates are mid-market reference rates. "Rates By Exchange Rate API"
attribution is required by ExchangeRate-API's Open Access tier and shown in-app.
