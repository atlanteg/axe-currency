#!/usr/bin/env bash
#
# Единая точка выпуска релиза FIXXE.
# Всё выводится из ОДНОГО числа N → рассинхрон versionCode/тега невозможен.
#
# ВАЖНО: всё, что видит пользователь магазина, должно быть НА АНГЛИЙСКОМ — в том числе
# «что нового». play-ops по умолчанию берёт текст последнего коммита, а коммиты у нас
# русские, поэтому заметки для магазинов лежат в store-release-notes.txt и передаются
# в ship явно через --notes-file (см. подсказку в конце скрипта).
#
# APK наружу НЕ публикуется: приложение распространяется только через Google Play
# (Android developer verification требует регистрации ключей для раздачи вне Play,
# а отладочный ключ для этого не годится). Вне магазина остаётся PWA на fixe.l23.xyz.
#
# Usage:
#   ./release.sh            # авто-инкремент: берёт текущий versionCode и +1
#   ./release.sh 8          # явно выпустить версию 8
#   ./release.sh 8 "текст"  # с произвольным описанием релиза
#
set -euo pipefail

cd "$(dirname "$0")"

# 0. Тексты, которые увидит пользователь магазина, обязаны быть английскими.
#    Проверяем до сборки: дешевле поймать здесь, чем вычищать из Play и App Store.
python3 - <<'PYCHK' || exit 1
import re, sys, pathlib
CYR = re.compile('[\u0400-\u04FF]')

def user_visible(path, line):
    """Комментарии — для разработчика, пользователь магазина их не видит."""
    if path.suffix == ".yaml":
        line = re.sub(r"(^|\s)#.*$", "", line)
    return line

bad = []
for f in ["store-release-notes.txt", "appstore/manifest.yaml"]:
    p = pathlib.Path(f)
    if not p.exists():
        continue
    for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        if CYR.search(user_visible(p, line)):
            bad.append(f"  {f}:{i}: {line.strip()[:70]}")
if bad:
    print("\u2717 Кириллица в текстах для магазинов (должен быть английский):")
    print("\n".join(bad))
    sys.exit(1)
PYCHK

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

# 3. Коммит + пуш
git add -A
git commit -m "v$N: $NOTE"
git push

# 4. GitHub Release — только отметка версии в истории, без APK-файла.
gh release create "v$N" \
  --title "FIXXE v$N" \
  --notes "versionName=1.$N

$NOTE

Установка: Google Play (Android) · App Store (iOS) · https://fixe.l23.xyz (PWA)"

echo "✓ v$N помечен. versionCode=$N == тег v$N."
echo
echo "  Заметки для магазинов (АНГЛИЙСКИЙ) — обнови store-release-notes.txt, затем:"
echo "    play-ops ship --track internal --yes --notes-file store-release-notes.txt"
echo "    (cd ios && testflight-ops ship --yes --notes-file ../store-release-notes.txt)"

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
