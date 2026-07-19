#!/usr/bin/env bash
#
# Единая точка выпуска релиза AXE.
# Всё выводится из ОДНОГО числа N → рассинхрон versionCode/тега невозможен.
#
# Usage:
#   ./release.sh            # авто-инкремент: берёт текущий versionCode и +1
#   ./release.sh 8          # явно выпустить версию 8
#   ./release.sh 8 "текст"  # с произвольным описанием релиза
#
set -euo pipefail

cd "$(dirname "$0")"

GRADLE="app/build.gradle.kts"

# Текущий versionCode из gradle
CURRENT=$(grep -E 'versionCode = [0-9]+' "$GRADLE" | grep -oE '[0-9]+')

# Новый номер: аргумент или +1
N="${1:-$((CURRENT + 1))}"
NOTE="${2:-Релиз v$N}"

echo "▶ Выпускаю версию $N (versionCode=$N, versionName=1.$N, тег=v$N)"

# 1. Синхронно меняем ОБА поля в gradle — единственный источник истины
sed -i '' -E "s/versionCode = [0-9]+/versionCode = $N/" "$GRADLE"
sed -i '' -E "s/versionName = \"[^\"]*\"/versionName = \"1.$N\"/" "$GRADLE"

# 1b. Синхронизируем версию PWA-веб-версии с Android (та же 1.N) + бампим кэш SW
sed -i '' -E "s/const APP_VERSION = '[^']*'/const APP_VERSION = '1.$N'/" web/app.js
sed -i '' -E "s/const CACHE = 'fixe-[^']*'/const CACHE = 'fixe-v$N'/" web/sw.js

# 2. Сборка
export JAVA_HOME="$(brew --prefix openjdk@17)"
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "✗ APK не собрался"; exit 1; }

# Копируем под осмысленным именем, чтобы ассет в релизе был FIXXE-vN.apk
NAMED_APK="app/build/outputs/apk/debug/FIXXE-v$N.apk"
cp "$APK" "$NAMED_APK"

# 3. Коммит + пуш
git add -A
git commit -m "v$N: $NOTE"
git push

# 4. GitHub Release. Тег v$N СОВПАДАЕТ с versionCode=$N — гарантированно.
#    Первая строка body = versionName=1.$N (приложение читает её для красивого показа).
gh release create "v$N" "$NAMED_APK" \
  --title "FIXXE v$N" \
  --notes "versionName=1.$N

$NOTE"

echo "✓ Android v$N выпущен. versionCode=$N == тег v$N — петли обновления не будет."

# 5. Деплой веб-версии на VM edge2il (best-effort — если хост доступен)
VM="ubuntu@130.110.238.118"
if tar czf /tmp/fixe-web.tgz -C web . 2>/dev/null && \
   scp -q -o BatchMode=yes -o ConnectTimeout=8 /tmp/fixe-web.tgz "$VM:/tmp/" 2>/dev/null; then
  ssh -o BatchMode=yes -o ConnectTimeout=8 "$VM" \
    'sudo tar xzf /tmp/fixe-web.tgz -C /var/www/fixe 2>/dev/null && sudo systemctl reload nginx' \
    && echo "✓ PWA задеплоена на https://fixe.l23.xyz (версия 1.$N)"
else
  echo "⚠ PWA не задеплоена (VM недоступна) — задеплой вручную позже"
fi
