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

KS=${KEYSTORE:-$ROOT/debug.keystore}
# compileOnly：由框架在运行时提供，不打进 dex
APIX=$ROOT/libs/libxposed-api.jar
# 必须打进 dex：libxposed service + interface（IXposedService$Stub 在 interface 包里）
LXP=$ROOT/libs/libxposed-service.jar

# 固定签名：仓库内 debug.keystore 必须存在且证书指纹不变。
# 换证书 = 无法覆盖安装 = 必须卸载重装 = uid 变化 = KernelSU 按 uid 记录的
# root 授权失效（表现为「每次更新都要重新授权」）。
EXPECT_SHA256=${EXPECT_SHA256:-cacdf86d34ff7f7de0ed99467364ff5fc9d898f6c61d2202a02a34050d8cdcd3}

if [ ! -f "$KS" ]; then
  echo "错误：缺少签名文件 $KS" >&2
  echo "本仓库自带 debug.keystore（证书 $EXPECT_SHA256），缺失时不要自动生成新密钥。" >&2
  exit 1
fi

KS_SHA256=$(keytool -list -v -keystore "$KS" -storepass android 2>/dev/null \
  | sed -n 's/.*SHA256: //p' | head -1 | tr -d '\r' | tr 'A-F' 'a-f' | tr -d ':')
if [ "$KS_SHA256" != "$EXPECT_SHA256" ]; then
  echo "错误：keystore 证书指纹与固定值不一致" >&2
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
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out fuckPrivacyGuard.apk build/app-aligned.apk

"$APKSIGNER" verify --print-certs fuckPrivacyGuard.apk 2>/dev/null | grep "certificate SHA-256" || true

echo "OK -> $ROOT/fuckPrivacyGuard.apk"
ls -la fuckPrivacyGuard.apk
echo
echo "更新方式（务必覆盖安装，不要卸载）："
echo "  adb install -r fuckPrivacyGuard.apk"
echo "  # 手机上：su -c 'pm install -r /sdcard/Download/fuckPrivacyGuard-2.0.0.apk'"
