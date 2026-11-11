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

echo "[1/2] javac (module under test + stubs + tests)"
"$JAVAC" -encoding UTF-8 -nowarn -cp libs/libxposed-api.jar -d "$OUT" \
    tests/stub/android/util/Log.java \
    tests/stub/android/app/PendingIntent.java \
    tests/stub/android/content/Context.java \
    tests/stub/android/content/Intent.java \
    tests/stub/android/content/ContentResolver.java \
    tests/stub/android/content/SharedPreferences.java \
    tests/stub/android/content/pm/ApplicationInfo.java \
    tests/stub/android/content/pm/PackageManager.java \
    tests/stub/android/provider/Settings.java \
    tests/stub/android/os/CancellationSignal.java \
    tests/stub/android/app/usage/UsageStatsManager.java \
    tests/stub/dalvik/system/DexFile.java \
    src/io/github/zeroedx/fuckprivacyguard/Logs.java \
    src/io/github/zeroedx/fuckprivacyguard/InstallStatus.java \
    src/io/github/zeroedx/fuckprivacyguard/Hooks.java \
    src/io/github/zeroedx/fuckprivacyguard/ConfigWire.java \
    src/io/github/zeroedx/fuckprivacyguard/Config.java \
    src/io/github/zeroedx/fuckprivacyguard/NetGuard.java \
    tests/src/io/github/zeroedx/fuckprivacyguard/HooksNoteHitTest.java \
    tests/src/io/github/zeroedx/fuckprivacyguard/HooksNoteHitConcurrencyTest.java \
    tests/src/io/github/zeroedx/fuckprivacyguard/HooksSmsSignatureTest.java \
    tests/src/io/github/zeroedx/fuckprivacyguard/HooksP0ContractTest.java \
    tests/src/io/github/zeroedx/fuckprivacyguard/InstallStatusTest.java \
    tests/src/io/github/zeroedx/fuckprivacyguard/MemoryPrefs.java \
    tests/src/io/github/zeroedx/fuckprivacyguard/ConfigChannelTest.java

echo "[2/2] run"
rc=0
"$JAVA" -cp "$OUT:libs/libxposed-api.jar" io.github.zeroedx.fuckprivacyguard.HooksNoteHitTest || rc=1
"$JAVA" -cp "$OUT:libs/libxposed-api.jar" io.github.zeroedx.fuckprivacyguard.HooksNoteHitConcurrencyTest || rc=1
"$JAVA" -cp "$OUT:libs/libxposed-api.jar" io.github.zeroedx.fuckprivacyguard.HooksSmsSignatureTest || rc=1
"$JAVA" -cp "$OUT:libs/libxposed-api.jar" io.github.zeroedx.fuckprivacyguard.HooksP0ContractTest || rc=1
"$JAVA" -cp "$OUT:libs/libxposed-api.jar" io.github.zeroedx.fuckprivacyguard.InstallStatusTest || rc=1
"$JAVA" -cp "$OUT:libs/libxposed-api.jar" io.github.zeroedx.fuckprivacyguard.ConfigChannelTest || rc=1
[ "$rc" -eq 0 ] || exit 1

echo "OK -> tests passed"
