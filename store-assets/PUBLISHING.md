# FIXXE — материалы и ответы для публикации

Пакет: `com.karpinity.fixxe` · Имя: **FIXXE** · versionCode 44 / versionName 1.44 · targetSdk 36
Разработчик: KARPINITY / Davidovski GmbH

## Файлы
| Что | Файл | Требование стора |
|---|---|---|
| AAB для загрузки | `../FIXXE-v44.aab` | подписан, targetSdk 36 |
| Иконка | `icon-512.png` | 512×512 PNG |
| Feature graphic | `feature-graphic-1024x500.png` | 1024×500 |
| Скриншоты Play (4 шт.) | `screenshots/*.png` | 1080×1920 (9:16), мин. 2 |
| Скриншоты App Store (4 шт.) | `screenshots-ios/*.png` | 1320×2868 (6.9"), мин. 1 |

Политика конфиденциальности: **https://telebimmer.com/fixxe-privacy.html**

---

## Data safety (Google Play) — готовые ответы

**Главный вопрос:** *Does your app collect or share any of the required user data types?*
→ **No**

Обоснование (соответствует фактическому коду):
- Нет аккаунтов, авторизации, аналитики, рекламы, трекинга, крашлитики.
- Настройки (список валют, порядок, точность, источник, язык) хранятся
  **только на устройстве** (`SharedPreferences`) и никуда не передаются.
- Сетевые запросы — только публичные курсы валют; в запросах нет данных пользователя.
- Единственное разрешение: `INTERNET`.

Если анкета задаёт уточняющие вопросы:
| Вопрос | Ответ |
|---|---|
| Data collected | None |
| Data shared | None |
| Is data encrypted in transit? | N/A (данные не собираются); весь трафик — HTTPS |
| Can users request data deletion? | N/A (данные не собираются) |
| Есть ли данные от детей? | Нет, данные не собираются вообще |

**Content rating:** анкета → нет насилия/контента 18+/азартных игр/пользовательского
контента → ожидаемо **Everyone / 3+ (PEGI 3)**.
**Категория:** Finance. **Реклама:** нет. **Покупки в приложении:** нет.

---

## Apple App Privacy (App Store) — готовые ответы
- *Do you or your third-party partners collect data from this app?* → **No, we do not collect data**
- Privacy Manifest уже в проекте: `ios/FIXXE/Resources/PrivacyInfo.xcprivacy`
  (NSPrivacyTracking = false, collected data types = пусто, UserDefaults с причиной CA92.1).

### Проверено на собранном бандле (iOS)
| Пункт | Состояние |
|---|---|
| Сборка под iPhone | ✅ BUILD SUCCEEDED, Debug и Release |
| Минимальная iOS | ✅ `MinimumOSVersion 15.0` в Info.plist (iPhone 6s и новее) |
| Иконка без альфа-канала | ✅ AppIcon 1024×1024, Opaque = true |
| Privacy Manifest в бандле | ✅ `PrivacyInfo.xcprivacy` лежит в `.app` |
| Вёрстка на «челке»/Dynamic Island | ✅ проверено на iPhone 17 Pro Max |
| Вёрстка на маленьком экране | ✅ проверено на iPhone SE 3 (375×667 — как 6s/7/8/SE) |
| Версия синхронна с Android | ✅ MARKETING_VERSION 1.44 / CURRENT_PROJECT_VERSION 44 |
| Отладочный код в релизе | ✅ отсутствует (проверено `strings` по релизному бинарнику) |
| Язык по умолчанию | ✅ английский на всех платформах; язык устройства — опцией «System default» |

Скриншоты снимаются одной командой: `ios/tools/shoot-screenshots.sh`.

---

## Store listing

**Short description (≤80):**
Live currency converter: 150+ currencies, 40 languages, mid-market rates.

**Full description:**
FIXXE is a fast, clean currency converter with live mid-market exchange rates — the neutral midpoint between global buy and sell prices, not bank spreads.

• 300+ currencies including crypto and precious metals, with country flags
• Real-time conversion — type in any field, all others update instantly
• Dynamic base — rates are shown relative to the currency you are editing
• Multi-source rates with automatic fallback (ExchangeRate-API, F.A., Frankfurter / ECB); pick a source manually if you prefer
• Smart rounding — small values never collapse to "1"; precision adapts to keep the amount financially accurate
• Source-aware search — filter currencies by provider, see where each one is available, and switch source in one tap
• 40 languages, English by default, with an option to follow the device language (incl. right-to-left)
• Drag to reorder, add/remove currencies, one-tap CLEAR
• Works offline for the interface; your list, order and settings are saved on your device

No account. No ads. No tracking. Your preferences never leave your phone.

Displayed rates are mid-market reference values, not real-time trading quotes.
Rates By Exchange Rate API.

---

## Публикация через app-ship (настроено)

Бандл подписи развёрнут: `~/Developer/app-ship` (вне репозитория), пассфраза — в Keychain
(`security find-generic-password -s "FIXXE app-ship signing bundle" -a "app-ship" -w`).
Все четыре инструмента проходят `doctor`:

| Инструмент | Состояние |
|---|---|
| testflight-ops | ✅ подпись `Apple Distribution: Davidovski GmbH`, ASC API доступен, dry-run проходит |
| appstore-ops | ✅ `appstore/manifest.yaml` валиден, скриншоты в `appstore/screenshots/en-US/APP_IPHONE_67/` |
| notarize-ops | ✅ (для macOS-сборок; у FIXXE их нет) |
| play-ops | ✅ Play API доступен, проект определяется как native `com.karpinity.fixxe` |

Команды после создания записей приложения:
```bash
cd ios && testflight-ops ship --yes          # сборка → TestFlight
appstore-ops submit --yes                    # метаданные + отправка на ревью
play-ops ship --track internal --yes         # AAB → Google Play
```

⚠️ **Про ключ подписи Android.** `play-ops` подписывает своим общим upload-ключом
(`~/.local/state/davidovski-os/play-ops/signing/upload.jks`), а не локальным `keystore.properties`.
Поэтому при создании приложения в Play Console как upload key надо зарегистрировать
`~/Developer/app-ship/credentials/upload-certificate.pem`, и заливать через `play-ops`,
а не вручную собранный `FIXXE-v44.aab`. Смешивать два ключа нельзя — Play примет только один.

---

## Что должен сделать ВЛАДЕЛЕЦ аккаунта (я не могу — нет доступа и это юр. согласия)

1. **Play App Signing ToS** — Play Console → при создании релиза принять условия.
   Требует аккаунт-уровневых прав; выдать Admin исполнителю либо принять самому.
2. **Создать запись приложения в App Store Connect.** Вход — Apple ID `atlant-spb@yandex.ru`
   (роль App Manager в команде Davidovski GmbH, team K6J2KS5XF7); если Apple предложит выбор
   команды — выбрать Davidovski GmbH, иначе нужный bundle id не будет виден.
   Bundle id `com.karpinity.fixxe`
   в Developer-портале уже зарегистрирован, но саму запись Apple через API создать не даёт:
   appstoreconnect.apple.com → Apps → + → New App → платформа iOS → выбрать этот bundle id →
   имя FIXXE, основной язык English, SKU. После этого `testflight-ops ship --yes` работает.
3. **Создать приложение в Google Play Console** (`com.karpinity.fixxe`) и при включении
   Play App Signing зарегистрировать `credentials/upload-certificate.pem` как upload key.
4. **Trader status (DSA)** — и в Google Play, и в App Store.
   Статус: **Trader** (компания). Данные Davidovski GmbH, Wehntalerstrasse 283A,
   8046 Zürich, Switzerland. Email/телефон проходят верификацию кодом.
   ⚠️ Эти контакты станут ПУБЛИЧНЫМИ в карточке приложения для пользователей ЕС —
   используйте корпоративные (innovation@karpinity.com), не личные.
