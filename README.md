# AXE — Currency Converter

Android app for real-time currency conversion using mid-market exchange rates.  
Inspired by XE Currency. Built entirely with CLI tools, no Android Studio required.

## Features

- **Live mid-market rates** via [open.er-api.com](https://open.er-api.com/) (free, no API key)
- **60+ currencies** with flags, symbols, and full names
- **Real-time conversion** — type in any field, all others update instantly
- **Auto-refresh** every 30 minutes + on every app resume
- **Persistent state** — selected currencies, order, and layout survive app restarts (SharedPreferences)
- **Drag to reorder** rows via the ⠿ handle on the right
- **Searchable currency picker** — filter by code or name
- **CLEAR button** — zero out all amounts at once
- **Delete per row** — ✕ button on the left (away from the amount field)
- Force light theme — no dark mode issues
- Integer-only amounts (no unnecessary decimals)

## Screenshots

| Main screen | Add currency |
|---|---|
| _(install APK and screenshot)_ | _(search by code or name)_ |

## Architecture

```
app/src/main/java/com/example/currencyconverter/
├── MainActivity.kt              # Activity: RecyclerView, ItemTouchHelper, dialogs
├── data/
│   ├── ExchangeRateApi.kt       # Retrofit interface (GET /v6/latest/{base})
│   ├── ExchangeRateResponse.kt  # Data model for API response
│   └── CurrencyRepository.kt   # Network layer, returns Result<T>
└── ui/
    ├── CurrencyItem.kt          # RecyclerView item model + CurrencyInfo
    ├── CurrencyAdapter.kt       # RecyclerView adapter with drag support
    └── CurrencyViewModel.kt     # AndroidViewModel: state, rates, persistence
```

**Stack:**
- Kotlin + ViewBinding
- ViewModel + StateFlow (unidirectional data flow)
- Retrofit 2 + OkHttp 4 + Gson
- RecyclerView + DiffUtil + ItemTouchHelper
- Material Components (MaterialCardView)
- SharedPreferences for persistence

## Exchange rate logic

Rates are fetched with EUR as base. All conversions go through EUR as the pivot:

```
amountInEUR = userInput / rate[activeCurrency]
result[X]   = amountInEUR × rate[X]
```

This gives true mid-market cross rates for any currency pair.

## Building

### Requirements

- macOS (tested on Sonoma / Apple Silicon)
- JDK 17: `brew install openjdk@17`
- Android SDK: download [command-line tools](https://developer.android.com/studio#command-tools), unzip to `~/android-sdk/cmdline-tools/latest/`
- Gradle: `brew install gradle && gradle wrapper` (or use the included `gradlew`)

### Build debug APK

```bash
export JAVA_HOME=$(brew --prefix openjdk@17)
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH

# First time only — accept SDK licenses and install build tools
sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0"

# Build
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Install to device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project config

| Parameter | Value |
|---|---|
| `minSdk` | 24 (Android 7.0) |
| `targetSdk` | 34 (Android 14) |
| `compileSdk` | 34 |
| `versionName` | 1.0 |
| Package | `com.example.currencyconverter` |

## Key dependencies

| Library | Version | Purpose |
|---|---|---|
| `androidx.recyclerview` | 1.3.2 | Currency list |
| `lifecycle-viewmodel-ktx` | 2.7.0 | ViewModel + coroutine scope |
| `kotlinx-coroutines-android` | 1.7.3 | Async network + auto-refresh |
| `retrofit2` | 2.9.0 | HTTP client |
| `converter-gson` | 2.9.0 | JSON parsing |
| `okhttp3` | 4.12.0 | HTTP engine |
| `material` | 1.11.0 | MaterialCardView |

## Default currencies

On first install: `EUR · USD · RSD · GEL · ILS · TJS · CHF`  
Add or remove any of the 60+ supported currencies via the **+ Add currency** button.

## Releasing (IMPORTANT — always use the script)

The app auto-updates by comparing its own `versionCode` against the latest GitHub
release tag (`versionCodeFromTag("v8") → 8`). If the APK's `versionCode` does **not**
equal the tag number, the app prompts to "update" forever in a loop.

**Therefore: never run `gh release create` by hand.** Always release with the script,
which derives `versionCode`, `versionName`, the git tag, and the release body from a
single number `N` — making drift impossible:

```bash
./release.sh        # auto-increment (current versionCode + 1)
./release.sh 8      # release version 8 explicitly
./release.sh 8 "Fixed X"   # with custom release note
```

The script guarantees `versionCode == versionName minor == tag` (e.g. `8 / 1.8 / v8`).
After release, verify with:

```bash
$ANDROID_HOME/build-tools/*/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | grep versionCode
```

The first line of every release body is `versionName=1.N`, which the in-app updater
reads to show a human-friendly "Доступна версия 1.N" instead of the raw tag.

## Rate source

Multi-source fallback chain (all normalized to EUR base), tried in order:

1. **ExchangeRate-API** (`open.er-api.com`) — primary. Open Access tier **requires
   attribution**: a visible "Rates By Exchange Rate API" link back to
   [exchangerate-api.com](https://www.exchangerate-api.com) (shown in the app footer).
2. **F.A. currency-api** (jsDelivr + `currency-api.pages.dev` mirror) — CC0
   public domain, no attribution required. 300+ currencies incl. crypto.
3. **Frankfurter** (`api.frankfurter.dev`) — MIT-licensed API, data from the European
   Central Bank (ECB). No hard attribution requirement.

Users can force a specific source in Settings (⚙); the others stay as fallback.
Displayed rates are mid-market reference rates, not buy/sell spreads.

## License

MIT © Atlanteg
