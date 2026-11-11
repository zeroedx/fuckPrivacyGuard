#!/bin/sh
# fuckPrivacyGuard build script (libxposed API 102)
#
# 依赖：JDK 11+、平台 android.jar、build-tools 中的 d8 / aapt2 / zipalign / apksigner
#
#   export ANDROID_JAR=/path/to/platforms/android-35/android.jar
#   export D8=/path/to/build-tools/35.0.0/d8
#   export AAPT2=/path/to/build-tools/35.0.0/aapt2
#   export ZIPALIGN=/path/to/build-tools/35.0.0/zipalign
#   export APKSIGNER=/path/to/build-tools/35.0.0/apksigner
#   sh build.sh
#
# 打包顺序：javac -> d8（含 libxposed service）-> aapt2 link -> 追加 classes.dex
#           -> 追加 META-INF 到 APK 根 -> zipalign -> apksigner
set -e

ROOT=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT"

ANDROID_JAR=${ANDROID_JAR:-$ROOT/../lsp/android-35/android.jar}
D8=${D8:-$ROOT/../lsp/bt37/android-37.0/d8}
AAPT2=${AAPT2:-aapt2}
ZIPALIGN=${ZIPALIGN:-zipalign}
APKSIGNER=${APKSIGNER:-apksigner}

# aapt2 link 需要带资源表的 jar；CI 用完整平台包，本地可用 android-stub.jar
AINC=${AAPT2_INCLUDE:-}
if [ -z "$AINC" ]; then
  if [ -f "$ROOT/android-stub.jar" ]; then AINC=$ROOT/android-stub.jar; else AINC=$ANDROID_JAR; fi
fi

# compileOnly：由框架在运行时提供，不打进 dex
APIX=$ROOT/libs/libxposed-api.jar
# 必须打进 dex：libxposed service + interface（IXposedService$Stub 在 interface 包里）
LXP=$ROOT/libs/libxposed-service.jar

# ---- 签名配置 ----
# 本项目使用独立的正式签名证书，不复用上游 HyperPGuard 的 debug.keystore。
# 签名材料（keystore / 别名 / 密码 / 期望指纹）一律从仓库外的配置读取，
# 绝不入库、绝不写日志。优先环境变量，其次外部配置文件（默认 ~/.config/
# fuckprivacyguard/signing.env，可用 SIGNING_ENV 覆盖）。
#
#   KEYSTORE    keystore 文件路径（必填）
#   KEY_ALIAS   别名（必填）
#   STORE_PASS  keystore 密码（必填）
#   KEY_PASS    私钥密码（缺省与 STORE_PASS 相同）
#   EXPECT_SHA256  期望的证书 SHA-256（64 位小写 hex，必填；防止用错密钥）
#
# 缺少任何必需项时直接失败，不静默回退到任何内置/上游证书。

SIGNING_ENV=${SIGNING_ENV:-$HOME/.config/fuckprivacyguard/signing.env}
if [ -f "$SIGNING_ENV" ]; then
  # shellcheck disable=SC1090
  . "$SIGNING_ENV"
fi

KS=${KEYSTORE:-}
ALIAS=${KEY_ALIAS:-}
STORE_PASS=${STORE_PASS:-}
KEY_PASS=${KEY_PASS:-$STORE_PASS}
EXPECT_SHA256=${EXPECT_SHA256:-}

missing=""
[ -n "$KS" ]         || missing="$missing KEYSTORE"
[ -n "$ALIAS" ]      || missing="$missing KEY_ALIAS"
[ -n "$STORE_PASS" ] || missing="$missing STORE_PASS"
[ -n "$EXPECT_SHA256" ] || missing="$missing EXPECT_SHA256"
if [ -n "$missing" ]; then
  echo "错误：缺少签名配置项：$missing" >&2
  echo "请在环境变量或 $SIGNING_ENV 中提供，例如：" >&2
  echo "  KEYSTORE=/path/release.jks KEY_ALIAS=fuckprivacyguard \\" >&2
  echo "  STORE_PASS=... EXPECT_SHA256=<64-hex> sh build.sh" >&2
  exit 1
fi
if [ ! -f "$KS" ]; then
  echo "错误：找不到 keystore：$KS" >&2
  exit 1
fi

KS_SHA256=$(keytool -list -v -keystore "$KS" -storepass "$STORE_PASS" -alias "$ALIAS" 2>/dev/null \
  | sed -n 's/.*SHA256: //p' | head -1 | tr -d '\r' | tr 'A-F' 'a-f' | tr -d ':')
if [ -z "$KS_SHA256" ]; then
  echo "错误：无法读取 keystore 证书指纹（keystore/别名/密码是否正确？）" >&2
  exit 1
fi
if [ "$KS_SHA256" != "$EXPECT_SHA256" ]; then
  echo "错误：keystore 证书指纹与 EXPECT_SHA256 不一致" >&2
  echo "  实际 $KS_SHA256" >&2
  echo "  期望 $EXPECT_SHA256" >&2
  exit 1
fi
echo "签名证书 SHA-256: $KS_SHA256"

rm -rf build fuckPrivacyGuard.apk
mkdir -p build/classes build/dex build/gen

echo "[1/6] javac"
javac --release 11 -encoding UTF-8 -nowarn \
  -cp "$ANDROID_JAR:$LXP:$APIX" -d build/classes \
  $(find src -name '*.java')

echo "[2/6] d8"
"$D8" --release --min-api 29 --lib "$ANDROID_JAR" --output build/dex \
  $(find build/classes -name '*.class') "$LXP"

echo "[3/6] aapt2 compile"
"$AAPT2" compile --dir res -o build/gen/res.zip

echo "[4/6] aapt2 link"
"$AAPT2" link -o build/app-unsigned.apk \
  -I "$AINC" \
  --manifest AndroidManifest.xml \
  -R build/gen/res.zip \
  -A assets \
  --min-sdk-version 29 --target-sdk-version 34 \
  --auto-add-overlay

echo "[5/6] add dex + META-INF"
cp build/dex/classes.dex build/classes.dex
# META-INF/xposed/* 必须在 APK 根目录：放进 assets/ 会被 aapt2 打成 assets/META-INF/，
# LSPosed 在根目录找不到就判定「不是模块」，日志里一条记录都不会有。
rm -rf build/META-INF && cp -r META-INF build/
(cd build && zip -q app-unsigned.apk classes.dex && zip -qr app-unsigned.apk META-INF)
"$ZIPALIGN" -f 4 build/app-unsigned.apk build/app-aligned.apk

echo "[6/6] sign"
"$APKSIGNER" sign --ks "$KS" --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$STORE_PASS" --key-pass "pass:$KEY_PASS" \
  --out fuckPrivacyGuard.apk build/app-aligned.apk

"$APKSIGNER" verify --print-certs fuckPrivacyGuard.apk 2>/dev/null | grep "certificate SHA-256" || true

echo "OK -> $ROOT/fuckPrivacyGuard.apk"
ls -la fuckPrivacyGuard.apk
echo
echo "更新方式（务必覆盖安装，不要卸载）："
echo "  adb install -r fuckPrivacyGuard.apk"
echo "  # 手机上：su -c 'pm install -r /sdcard/Download/fuckPrivacyGuard-2.1.1.apk'"
