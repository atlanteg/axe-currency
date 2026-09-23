#!/usr/bin/env bash
# Снимает скриншоты FIXXE для App Store в симуляторе.
#
# Размер кадра 1320×2868 (6.9" — iPhone 17 Pro Max) — обязательный набор
# для App Store Connect; остальные размеры Apple масштабирует сам.
#
# На время съёмки в DEBUG-бандл подкладываются PNG флагов (см. FlagAssets и
# tools/render-flags.swift): в эмодзи-шрифте симулятора нет глифов флагов,
# на реальном iPhone они есть. В релизную сборку эти файлы не попадают.
#
# Запуск: ios/tools/shoot-screenshots.sh [имя-симулятора]
set -euo pipefail

SIM="${1:-iPhone 17 Pro Max}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="${TMPDIR:-/tmp}/fixxe-shots"
DD="$WORK/dd"
OUT="$ROOT/store-assets/screenshots-ios"
BUNDLE_ID="com.karpinity.fixxe"

mkdir -p "$WORK" "$OUT"

echo "▶ сборка"
xcodebuild -project "$ROOT/ios/FIXXE.xcodeproj" -scheme FIXXE \
  -destination "platform=iOS Simulator,name=$SIM" \
  -derivedDataPath "$DD" CODE_SIGNING_ALLOWED=NO build >/dev/null

APP="$DD/Build/Products/Debug-iphonesimulator/FIXXE.app"

echo "▶ флаги"
swift "$ROOT/ios/tools/render-flags.swift" "$WORK/flags" 96 >/dev/null
cp "$WORK"/flags/flag_*.png "$APP/"

echo "▶ симулятор: $SIM"
xcrun simctl boot "$SIM" 2>/dev/null || true
xcrun simctl bootstatus "$SIM" -b >/dev/null 2>&1 || true
xcrun simctl install "$SIM" "$APP"
# фиксированный статус-бар: без него в кадре будет случайное время/заряд
xcrun simctl status_bar "$SIM" override --time "9:41" \
  --cellularMode active --cellularBars 4 --wifiBars 3 --batteryState charged --batteryLevel 100

shot() {
  local name="$1"; shift
  xcrun simctl terminate "$SIM" "$BUNDLE_ID" 2>/dev/null || true
  xcrun simctl launch "$SIM" "$BUNDLE_ID" "$@" >/dev/null
  sleep 5
  xcrun simctl io "$SIM" screenshot --type=png "$OUT/$name.png" >/dev/null 2>&1
  echo "  ✓ $name.png"
}

echo "▶ съёмка"
shot 1-main
shot 2-add-currency -screen-add
shot 3-settings -screen-settings
shot 4-reorder -screen-reorder

xcrun simctl terminate "$SIM" "$BUNDLE_ID" 2>/dev/null || true
echo "готово: $OUT"
