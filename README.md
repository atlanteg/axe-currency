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

## Rate source

[ExchangeRate-API](https://open.er-api.com/) — free tier, updates daily, no authentication required.  
Displayed rates are mid-market reference rates, not buy/sell spreads.

## License

MIT © Atlanteg
