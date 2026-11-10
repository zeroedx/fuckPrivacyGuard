#!/bin/sh
# 离线单元测试：不需要设备，也不需要 Android SDK。
#
# 只编译 Hooks.java 及其依赖（Logs、libxposed-api），用 tests/stub 下的
# android.util.Log / dalvik.system.DexFile 替身跑纯 JVM 断言。
# 不编译 MainActivity 等需要完整 Android 运行时的类。
#
#   sh tests/run.sh
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

JAVAC=${JAVAC:-javac}
JAVA=${JAVA:-java}
OUT=tests/out
rm -rf "$OUT"
mkdir -p "$OUT"

echo "[1/2] javac (module under test + stubs + test)"
"$JAVAC" -encoding UTF-8 -nowarn -cp libs/libxposed-api.jar -d "$OUT" \
    tests/stub/android/util/Log.java \
    tests/stub/dalvik/system/DexFile.java \
    src/io/github/vivibibi30/hyperpguard/Logs.java \
    src/io/github/vivibibi30/hyperpguard/Hooks.java \
    tests/src/io/github/vivibibi30/hyperpguard/HooksNoteHitTest.java

echo "[2/2] run"
"$JAVA" -cp "$OUT:libs/libxposed-api.jar" io.github.vivibibi30.hyperpguard.HooksNoteHitTest

echo "OK -> tests passed"
