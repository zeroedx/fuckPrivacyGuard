#!/bin/sh
# 把构建产物发布到 LSPosed 分发仓库
# 用法：sh scripts/publish-lsposed.sh [APK]
set -e

DIST_REPO=${DIST_REPO:-Xposed-Modules-Repo/io.github.vivibibi30.hyperpguard}
APK=${1:-fuckPrivacyGuard.apk}
[ -f "$APK" ] || { echo "找不到 $APK" >&2; exit 1; }

BADGING=$(aapt2 dump badging "$APK")
CODE=$(printf '%s\n' "$BADGING" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" | head -1)
NAME=$(printf '%s\n' "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)
[ -n "$CODE" ] && [ -n "$NAME" ] || { echo "解析版本号失败" >&2; exit 1; }

TAG="$CODE-$NAME"
NOTES="docs/RELEASE-$NAME.md"
[ -f "$NOTES" ] || NOTES="docs/RELEASE.md"
[ -f "$NOTES" ] || { echo "缺少发布说明 $NOTES" >&2; exit 1; }

TMP="/tmp/fuckPrivacyGuard-$NAME.apk"
cp "$APK" "$TMP"

echo "发布 $DIST_REPO  版本 $NAME ($CODE)  tag=$TAG"
gh release create "$TAG" "$TMP" \
  --repo "$DIST_REPO" \
  --title "$NAME" \
  --notes-file "$NOTES"
echo "完成：https://github.com/$DIST_REPO/releases/tag/$TAG"
