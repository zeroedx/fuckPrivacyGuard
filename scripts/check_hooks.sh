#!/bin/sh
# Hook 签名与默认开关自查；设置 ANDROID_JAR 时额外校验平台签名。
cd "$(dirname "$0")/.." || exit 1
exec python3 scripts/check_hooks.py "$@"
