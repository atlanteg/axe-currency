#!/usr/bin/env bash
# Снимает скриншоты FIXXE для App Store в симуляторе.
#
# Кадры: 1320×2868 (6.9" — iPhone 17 Pro Max) и 2064×2752 (13" — iPad Pro).
# Оба размера обязательны в App Store Connect, раз приложение заявлено
# и для iPhone, и для iPad; прочие размеры Apple масштабирует сама.
#
# На время съёмки в DEBUG-бандл подкладываются PNG флагов (см. FlagAssets и
# tools/render-flags.swift): в эмодзи-шрифте симулятора нет глифов флагов,
# на реальном iPhone они есть. В релизную сборку эти файлы не попадают.
#
# Запуск: ios/tools/shoot-screenshots.sh [имя-симулятора]
set -euo pipefail

SIM="${1:-iPhone 17 Pro Max}"
IPAD="${2:-iPad Pro 13-inch (M4) (16GB)}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="${TMPDIR:-/tmp}/fixxe-shots"
DD="$WORK/dd"
OUT="$ROOT/store-assets/screenshots-ios"
OUT_IPAD="$ROOT/store-assets/screenshots-ios-ipad"
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

echo "▶ iPad: $IPAD"
xcodebuild -project "$ROOT/ios/FIXXE.xcodeproj" -scheme FIXXE \
  -destination "platform=iOS Simulator,name=$IPAD" \
  -derivedDataPath "$DD-ipad" CODE_SIGNING_ALLOWED=NO build >/dev/null
IPAD_APP="$DD-ipad/Build/Products/Debug-iphonesimulator/FIXXE.app"
cp "$WORK"/flags/flag_*.png "$IPAD_APP/"
xcrun simctl boot "$IPAD" 2>/dev/null || true
xcrun simctl bootstatus "$IPAD" -b >/dev/null 2>&1 || true
xcrun simctl install "$IPAD" "$IPAD_APP"
xcrun simctl status_bar "$IPAD" override --time "9:41" \
  --wifiBars 3 --batteryState charged --batteryLevel 100 2>/dev/null || true

mkdir -p "$OUT_IPAD"
ipad_shot() {
  local name="$1"; shift
  xcrun simctl terminate "$IPAD" "$BUNDLE_ID" 2>/dev/null || true
  xcrun simctl launch "$IPAD" "$BUNDLE_ID" "$@" >/dev/null
  sleep 5
  xcrun simctl io "$IPAD" screenshot --type=png "$OUT_IPAD/$name.png" >/dev/null 2>&1
  echo "  ✓ ipad/$name.png"
}
ipad_shot 1-main
ipad_shot 2-add-currency -screen-add
ipad_shot 3-settings -screen-settings
ipad_shot 4-reorder -screen-reorder
xcrun simctl terminate "$IPAD" "$BUNDLE_ID" 2>/dev/null || true

echo "готово: $OUT и $OUT_IPAD"
