# FIXXE — материалы и ответы для публикации

Пакет: `com.karpinity.fixxe` · Имя: **FIXXE** · versionCode 40 / versionName 1.40 · targetSdk 36
Разработчик: KARPINITY / Davidovski GmbH

## Файлы
| Что | Файл | Требование стора |
|---|---|---|
| AAB для загрузки | `../FIXXE-v40.aab` | подписан, targetSdk 36 |
| Иконка | `icon-512.png` | 512×512 PNG |
| Feature graphic | `feature-graphic-1024x500.png` | 1024×500 |
| Скриншоты (4 шт.) | `screenshots/*.png` | 1080×1920 (9:16), мин. 2 |

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

---

## Store listing

**Short description (≤80):**
Live currency converter: 150+ currencies, 40 languages, mid-market rates.

**Full description:**
FIXXE is a fast, clean currency converter with live mid-market exchange rates — the neutral midpoint between global buy and sell prices, not bank spreads.

• 150+ currencies (and 300+ including crypto and precious metals via an alternative source)
• Real-time conversion — type in any field, all others update instantly
• Dynamic base — rates are shown relative to the currency you are editing
• Multi-source rates with automatic fallback (ExchangeRate-API, Fawaz Ahmed, Frankfurter / ECB); pick a source manually if you prefer
• Smart rounding — small values never collapse to "1"; precision adapts to keep the amount financially accurate
• Source-aware search — filter currencies by provider, see where each one is available, and switch source in one tap
• 40 languages with automatic device-language detection (incl. right-to-left)
• Drag to reorder, add/remove currencies, one-tap CLEAR
• Works offline for the interface; your list, order and settings are saved on your device

No account. No ads. No tracking. Your preferences never leave your phone.

Displayed rates are mid-market reference values, not real-time trading quotes.
Rates By Exchange Rate API.

---

## Что должен сделать ВЛАДЕЛЕЦ аккаунта (я не могу — нет доступа и это юр. согласия)

1. **Play App Signing ToS** — Play Console → при создании релиза принять условия.
   Требует аккаунт-уровневых прав; выдать Admin исполнителю либо принять самому.
2. **Trader status (DSA)** — и в Google Play, и в App Store.
   Статус: **Trader** (компания). Данные Davidovski GmbH, Wehntalerstrasse 283A,
   8046 Zürich, Switzerland. Email/телефон проходят верификацию кодом.
   ⚠️ Эти контакты станут ПУБЛИЧНЫМИ в карточке приложения для пользователей ЕС —
   используйте корпоративные (innovation@karpinity.com), не личные.
