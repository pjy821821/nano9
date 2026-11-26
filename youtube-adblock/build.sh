#!/bin/sh
# 스토어 업로드·Firefox 설치용 zip 생성: build/youtube-adblock-<버전>.zip
set -e
cd "$(dirname "$0")"
VER=$(node -e "console.log(require('./manifest.json').version)")
mkdir -p build
OUT="build/youtube-adblock-$VER.zip"
rm -f "$OUT"
zip -qr "$OUT" manifest.json rules.json content.js styles.css popup.html popup.js icons
echo "$OUT"
