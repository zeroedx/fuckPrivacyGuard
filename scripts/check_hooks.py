#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Hook 签名与默认开关的可重复自查。

纯静态检查（不需要 Android SDK）：核对源码里的 Hook 注册是否与预期一致。
设置 ANDROID_JAR 后，额外用 javap 校验注册签名在目标平台 jar 里真实存在。

    sh scripts/check_hooks.sh
    ANDROID_JAR=/path/android-35/android.jar sh scripts/check_hooks.sh

发现不一致时返回非零。无法判定的项（如拿不到真实 API 级别、找不到 javap）记为 SKIP，
既不计入通过，也不误报成功。
"""
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "io", "github", "vivibibi30", "hyperpguard")

results = []


def check(name, ok, detail=""):
    results.append((name, "ok" if ok else "FAIL", detail))


def skip(name, detail=""):
    """无法判定时记 SKIP：既不算通过，也不误报成功。"""
    results.append((name, "SKIP", detail))


def read(fn):
    with open(os.path.join(SRC, fn), encoding="utf-8") as f:
        return f.read()


def main():
    hooks = read("Hooks.java")
    priv = read("PrivacyApis.java")
    capture = read("CaptureHooks.java")
    entry = read("HookEntry.java")
    cfg = read("Config.java")
    all_src = hooks + priv + capture + entry + cfg

    # ---- A. noteHit 去重键含类名/方法名/参数签名 ----
    check("noteHit 基于 Chain（含类名/方法名/参数签名）",
          "noteHit(XposedInterface.Chain chain)" in hooks
          and "identity(" in hooks
          and "getParameterTypes()" in hooks)
    check("noteHit 去重键包含语义标签",
          "label + ' ' + id" in hooks or "label + \" \" + id" in hooks)
    check("已无旧式 noteHit(String) 字符串调用",
          re.search(r'noteHit\("', all_src) is None)

    # ---- B. 应用清单 Hook 四个入口 ----
    check("applist: getInstalledPackages(int) 已注册",
          'getInstalledPackages", new Class<?>[]{int.class}' in priv)
    check("applist: getInstalledApplications(int) 已注册",
          'getInstalledApplications", new Class<?>[]{int.class}' in priv)
    check("applist: flags 重载通过反射类注册（packages）",
          'new Class<?>[]{pif}' in priv
          and 'android.content.pm.PackageManager$PackageInfoFlags' in priv)
    check("applist: flags 重载通过反射类注册（applications）",
          'new Class<?>[]{aif}' in priv
          and 'android.content.pm.PackageManager$ApplicationInfoFlags' in priv)
    check("applist: 反射取类失败不致命（findClass 返回 null）",
          "Class.forName(name, false, cl)" in priv)
    check("applist: 源码不硬引用 flags 类（不写 .class 字面量）",
          "PackageInfoFlags.class" not in priv
          and "ApplicationInfoFlags.class" not in priv)

    # ---- C. 默认开关未变 ----
    check("敏感数据细粒度开关默认关闭（Config.on(k, false)）",
          "Config.on(k, false)" in priv)
    check("SystemHelper 分组默认开",
          "Config.K_G_SYSHELPER, true" in entry)
    check("guardprovider 分组默认开",
          "Config.K_G_GUARD, true" in entry)
    check("dmregservice 分组默认开",
          "Config.K_G_DMREG, true" in entry)
    check("SystemHelper 整进程断网默认关",
          "Config.K_SYSHELPER_ALLNET, false" in entry)
    check("guardprovider 云查默认关",
          "Config.K_GP_CLOUDSCAN, false" in entry)

    # ---- D. 敏感能力映射未变 ----
    for pkg in ("com.mobiletools.systemhelper", "com.miui.guardprovider",
                "com.miui.dmregservice"):
        check("CAPS 仍覆盖 " + pkg, pkg in priv)

    # ---- E. 可选：签名在平台 jar 里真实存在 ----
    # 注意：这里校验的是公开基类 android.content.pm.PackageManager 的声明。
    # 运行时被 hook 的是框架内部类 android.app.ApplicationPackageManager，
    # 它不在 SDK 平台包里（javap 会报 class not found），因此本项只能确认
    # 「基类声明了这些重载」，不能证明框架类实际覆写；后者需真机日志确认。
    android_jar = os.environ.get("ANDROID_JAR", "")
    javap = shutil.which("javap")
    if not android_jar:
        skip("平台签名核对：未设置 ANDROID_JAR（跳过）")
    elif not os.path.isfile(android_jar):
        skip("平台签名核对：ANDROID_JAR 不是文件（跳过）：%s" % android_jar)
    elif not javap:
        skip("平台签名核对：找不到 javap（跳过）")
    else:
        api = platform_api(android_jar)
        if api <= 0:
            # 例如 Maven 的 com.google.android:android stub：没有 source.properties，
            # 拿不到真实 API 级别，无法判定 flags 重载应否存在 —— 记 SKIP，不得平凡通过。
            skip("平台签名核对：无法确认平台 API 级别（如 Maven stub，跳过）")
        else:
            out = javap_out(javap, android_jar, "android.content.pm.PackageManager")
            has_int_p = "getInstalledPackages(int)" in out
            has_int_a = "getInstalledApplications(int)" in out
            has_flags_p = ("getInstalledPackages("
                           "android.content.pm.PackageManager$PackageInfoFlags)") in out
            has_flags_a = ("getInstalledApplications("
                           "android.content.pm.PackageManager$ApplicationInfoFlags)") in out
            check("平台 jar(%s): 基类声明 getInstalledPackages(int)" % api, has_int_p)
            check("平台 jar(%s): 基类声明 getInstalledApplications(int)" % api, has_int_a)
            if api >= 33:
                check("平台 jar(%s): 基类声明 flags 重载" % api, has_flags_p and has_flags_a)
            else:
                check("平台 jar(%s): flags 重载应不存在（旧系统）" % api,
                      not has_flags_p and not has_flags_a)

    # ---- 输出 ----
    bad = 0
    skipped = 0
    for name, status, detail in results:
        if status == "ok":
            mark = "  ok  "
        elif status == "SKIP":
            mark = "  SKIP"
            skipped += 1
        else:
            mark = "  FAIL"
            bad += 1
        print(mark + "  " + name + (("  " + detail) if detail else ""))
    print()
    print("共 %d 项，失败 %d 项，跳过 %d 项" % (len(results), bad, skipped))
    return 1 if bad else 0


def platform_api(jar):
    """从平台包的 source.properties 读 AndroidVersion.ApiLevel；读不到返回 0。

    注意不能从路径猜：平台目录名是代号（android-13 其实是 API 33）。
    """
    d = os.path.dirname(os.path.abspath(jar))
    for _ in range(3):
        sp = os.path.join(d, "source.properties")
        if os.path.isfile(sp):
            try:
                with open(sp, encoding="utf-8", errors="ignore") as f:
                    for line in f:
                        if "AndroidVersion.ApiLevel" in line and "=" in line:
                            return int(float(line.split("=", 1)[1].strip()))
            except Exception:  # noqa
                pass
        nd = os.path.dirname(d)
        if nd == d:
            break
        d = nd
    return 0


def javap_out(javap, jar, cls):
    try:
        r = subprocess.run([javap, "-classpath", jar, cls],
                           capture_output=True, text=True, timeout=120)
        return r.stdout
    except Exception as e:  # noqa
        print("javap 失败: %s" % e)
        return ""


if __name__ == "__main__":
    sys.exit(main())
