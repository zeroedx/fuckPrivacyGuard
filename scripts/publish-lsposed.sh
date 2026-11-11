#!/bin/sh
# 把构建产物发布到 LSPosed 分发仓库
# 用法：sh scripts/publish-lsposed.sh [APK]
set -e

DIST_REPO=${DIST_REPO:-Xposed-Modules-Repo/io.github.zeroedx.fuckprivacyguard}
APK=${1:-fuckPrivacyGuard.apk}
[ -f "$APK" ] || { echo "找不到 $APK" >&2; exit 1; }

# 授权判定（只读，不做写探针）：不采信 permissions.push。
# GitHub App 安装令牌以「安装覆盖的仓库列表」为准；细粒度 PAT 才回退到 permissions.push。
AUTH=""
INSTALLS=$(gh api /user/installations --jq '.installations[].id' 2>/dev/null || true)
if [ -n "$INSTALLS" ]; then
  for iid in $INSTALLS; do
    if gh api "/user/installations/$iid/repositories" --paginate \
         --jq '.repositories[].full_name' 2>/dev/null | grep -qxF "$DIST_REPO"; then
      AUTH="installation $iid"
      break
    fi
  done
  if [ -z "$AUTH" ]; then
    echo "令牌为 GitHub App 安装令牌，但其安装范围不含 $DIST_REPO" >&2
    echo "请把该仓库加入 App 安装范围，或改用具备该仓 contents:write + releases:write 的令牌" >&2
    exit 1
  fi
else
  PUSH=$(gh api "repos/$DIST_REPO" --jq '.permissions.push // false' 2>/dev/null || echo false)
  if [ "$PUSH" != "true" ]; then
    echo "令牌对 $DIST_REPO 无 push 权限" >&2
    exit 1
  fi
  AUTH="pat permissions.push"
fi
echo "授权确认（$AUTH）"

BADGING=$(aapt2 dump badging "$APK")
CODE=$(printf '%s\n' "$BADGING" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" | head -1)
NAME=$(printf '%s\n' "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)
[ -n "$CODE" ] && [ -n "$NAME" ] || { echo "解析版本号失败" >&2; exit 1; }

TAG="$CODE-$NAME"
NOTES="docs/RELEASE-$NAME.md"
[ -f "$NOTES" ] || NOTES="docs/RELEASE.md"
[ -f "$NOTES" ] || { echo "缺少发布说明 $NOTES" >&2; exit 1; }

LOCAL_SHA=$(sha256sum "$APK" | awk '{print $1}')
TMP="/tmp/fuckPrivacyGuard-$NAME.apk"
cp "$APK" "$TMP"

echo "发布 $DIST_REPO  版本 $NAME ($CODE)  tag=$TAG"

# 幂等：已存在同 tag Release 时
#   同名 APK 的 SHA-256 相同 → 安全跳过上传
#   已存在但 SHA-256 不同 → 报错停止，绝不覆盖、绝不删除
if gh release view "$TAG" --repo "$DIST_REPO" >/dev/null 2>&1; then
  echo "已存在 Release $TAG，检查附件"
  if gh release view "$TAG" --repo "$DIST_REPO" --json assets \
       --jq '.assets[].name' | grep -qxF "fuckPrivacyGuard-$NAME.apk"; then
    rm -rf /tmp/fpg-existing && mkdir -p /tmp/fpg-existing
    gh release download "$TAG" --repo "$DIST_REPO" \
      --pattern "fuckPrivacyGuard-$NAME.apk" --dir /tmp/fpg-existing --clobber
    EXIST_SHA=$(sha256sum "/tmp/fpg-existing/fuckPrivacyGuard-$NAME.apk" | awk '{print $1}')
    if [ "$EXIST_SHA" = "$LOCAL_SHA" ]; then
      echo "分发仓 APK SHA-256 相同，安全跳过上传"
    else
      echo "分发仓 APK SHA-256 与构建件不同：$EXIST_SHA != $LOCAL_SHA；停止，不覆盖、不删除" >&2
      exit 1
    fi
  else
    echo "Release $TAG 已存在但缺少附件；仅补传该附件（不删除其它资产）"
    gh release upload "$TAG" "$TMP" --repo "$DIST_REPO"
  fi
else
  gh release create "$TAG" "$TMP" \
    --repo "$DIST_REPO" \
    --title "$NAME" \
    --notes-file "$NOTES"
fi

# 回读分发仓资产并比对 SHA-256，逐字节一致才算同步成功
rm -rf /tmp/fpg-verify && mkdir -p /tmp/fpg-verify
gh release download "$TAG" --repo "$DIST_REPO" \
  --pattern "fuckPrivacyGuard-$NAME.apk" --dir /tmp/fpg-verify --clobber
REMOTE_SHA=$(sha256sum "/tmp/fpg-verify/fuckPrivacyGuard-$NAME.apk" | awk '{print $1}')
if [ "$LOCAL_SHA" != "$REMOTE_SHA" ]; then
  echo "分发仓 APK SHA-256 与构建件不一致：$REMOTE_SHA != $LOCAL_SHA" >&2
  exit 1
fi
echo "完成：https://github.com/$DIST_REPO/releases/tag/$TAG  sha256=$LOCAL_SHA"
