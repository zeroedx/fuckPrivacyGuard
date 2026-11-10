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
SRC = os.path.join(ROOT, "src", "io", "github", "zeroedx", "fuckprivacyguard")

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
    xsof = read("XsofHooks.java")
    msa = read("MsaHooks.java")
    analytics = read("AnalyticsHooks.java")
    sh = read("SystemHelperHooks.java")
    guard = read("GuardHooks.java")
    all_src = hooks + priv + capture + entry + cfg + xsof + msa + analytics + sh + guard

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

    # ---- E. 短信发送 Hook 按签名谓词判定（审计 P0-1） ----
    check("sms: 用签名谓词 isSmsSend 而不是写死重载",
          "isSmsSend(String name, Class<?>[] p, Class<?> ret)" in hooks)
    check("sms: 覆盖 sendTextMessage",
          '"sendTextMessage"' in hooks and '"sendTextMessageWithoutPersisting"' in hooks)
    check("sms: 覆盖 sendMultipartTextMessage 与 sendDataMessage",
          '"sendMultipartTextMessage"' in hooks and '"sendDataMessage"' in hooks)
    check("sms: 谓词要求 void 返回",
          "if (ret != void.class) return false;" in hooks)
    check("sms: PrivacyApis 用谓词注册 SmsManager（不再写死错误签名）",
          "Hooks.isSmsSend(method.getName()" in priv
          and 'android.telephony.SmsManager' in priv)
    # 旧的错误签名：4 个 String + 1 个 PendingIntent（sendTextMessage 写成 5 参 String 版）
    check("sms: 已移除写死 String,String,String,String,PendingIntent 的错误签名",
          "String.class, String.class, String.class, String.class" not in priv)

    # ---- F. UsageStatsManager#queryEvents(UsageEventsQuery) 版本兼容（审计 P1-1） ----
    check("usage: 保留 queryEvents(long,long)",
          '"queryEvents", new Class<?>[]{long.class, long.class}' in priv)
    check("usage: 反射补 queryEvents(UsageEventsQuery)",
          "android.app.usage.UsageEventsQuery" in priv
          and '"queryEvents", new Class<?>[]{ueq}' in priv)
    check("usage: 源码不硬引用 UsageEventsQuery 类（不写 .class 字面量）",
          "UsageEventsQuery.class" not in priv)

    # ---- G. SystemHelper 标准应用清单入口（审计 P0-2） ----
    check("systemhelper: privacySafe 开启 appList",
          re.search(r'privacySafe\(cl, pkg, true, false, true, false, false\)', entry) is not None)
    check("systemhelper: 仍保留厂商上报出口 c.d.a.b.f",
          '"c.d.a.b.f", "a"' in entry)

    # ---- H. 作用域未知状态显示（审计 P1-6） ----
    cats = read("CategoriesPage.java")
    diag = read("DiagnosticsPage.java")
    check("scope: CategoriesPage 用 scopeKnown 区分未知与未加入",
          "scopeKnown && !ModuleState.I.inScope(g.pkg)" in cats)
    check("scope: CategoriesPage 未知时不变暗",
          "!scopeKnown || ModuleState.I.inScope(g.pkg) ? 1f : 0.4f" in cats)
    check("scope: DiagnosticsPage 未加入仅在 scopeKnown 时显示",
          "ModuleState.I.scopeKnown() ? Ui.T(\"未加入\") : Ui.T(\"未知\")" in diag)

    # ---- I. 真实命中记录（审计 P1-3）：安装成功不得等同于拦截成功 ----
    check("hit: XSOF Provider 记录真实命中",
          'Hooks.noteHit(chain, "xsof query")' in xsof
          and 'Hooks.noteHit(chain, "xsof call")' in xsof
          and 'Hooks.noteHit(chain, "xsof insert")' in xsof)
    check("hit: XSOF AIDL / 写库 / 评分记录真实命中",
          'Hooks.noteHit(chain, "xsof aidl")' in xsof
          and 'Hooks.noteHit(chain, "xsof db write")' in xsof
          and 'Hooks.noteHit(chain, "xsof score")' in xsof)
    check("hit: MSA 画像 / 埋点记录真实命中",
          'Hooks.noteHit(chain, "msa opened")' in msa
          and 'Hooks.noteHit(chain, "msa track")' in msa)
    check("hit: analytics 固定目标带 hitTag",
          'Hooks.ret(m, cl, "com.miui.analytics.onetrack.p.u", "w",' in analytics
          and '"analytics p.u#w"' in analytics)
    check("hit: analytics 扫描方法记录真实命中",
          'Hooks.noteHit(chain, "analytics usage")' in analytics)
    # 拦截 lambda 里必须同时有 noteHit，避免「装上即算拦截」
    check("hit: XSOF 写库拦截同时记录命中（非仅安装计数）",
          re.search(r'intercept\(chain -> \{\s*Hooks\.noteHit\(chain, "xsof db write"\);'
                    r'\s*return null;\s*\}\)', xsof) is not None)

    # ---- K. 2.1.0 P0：定位契约 / Android ID / SystemHelper / guardprovider ----
    check("loc: getCurrentLocation 走回调而不是直接返回 null",
          "deliverEmptyCurrentLocation" in priv
          and "deliverEmptyCurrentLocation" in hooks)
    check("loc: 回调投递要求同时具备 Executor 与 Consumer",
          "java.util.concurrent.Executor" in hooks
          and "java.util.function.Consumer" in hooks)
    check("loc: requestSingleUpdate 一并覆盖",
          '"requestSingleUpdate"' in priv)
    check("loc: 回调接收器为可测的 LocationSink",
          "interface LocationSink" in hooks)
    check("loc: 已取消的 CancellationSignal 不再回调",
          "android.os.CancellationSignal" in hooks and "isCanceled(" in hooks)
    # 投递失败必须放行原逻辑，否则调用方既拿不到回调、原逻辑也没执行，会一直等
    check("loc: getCurrentLocation 投递失败时放行 chain.proceed()",
          re.search(r'boolean delivered = Hooks\.deliverEmptyCurrentLocation\(.*?'
                    r'if \(delivered\) \{.*?return null;\s*\}.*?return chain\.proceed\(\);',
                    priv, re.S) is not None)

    check("androidid: 返回稳定假值而不是 null",
          "Hooks.androidIdStub()" in priv
          and "androidIdStub()" in hooks)
    # android_id 分支里不得再直接 return null（限定在同一分支的短窗口内匹配）
    m = re.search(r'if \("android_id"\.equals\(key\)\) \{(.{0,300}?)\}',
                  priv, re.S)
    check("androidid: android_id 分支内不出现 return null",
          m is not None and "return null" not in m.group(1)
          and "androidIdStub()" in m.group(1))

    check("sh: 许可判断按 public static (Context)->boolean 判定",
          "isPolicyGate" in hooks and "Hooks.isPolicyGate" in sh)
    check("sh: 许可判断全范围候选唯一，否则跳过",
          "sh gate skip cands=" in sh and "cands.size() != 1" in sh)
    check("sh: 统计线程按 Thread 子类 run() 判定",
          "isUsageRun" in hooks and "Hooks.isUsageRun" in sh)
    check("sh: 统计线程额外要求引用 android.app.usage.*（避免误伤其它线程）",
          "referencesUsageTypes" in hooks
          and 'startsWith("android.app.usage.")' in hooks)
    check("sh: 统计线程全范围候选唯一，否则跳过",
          "sh usage skip cands=" in sh and "cands.size() != 1" in sh)
    check("sh: 接收器按 onReceive(Context,Intent) 判定",
          "isReceiverOnReceive" in hooks and "Hooks.isReceiverOnReceive" in sh)
    check("sh: 7 个接收器类名齐全",
          all(('"' + n + '"') in sh for n in [
              "AppInfoRegisterReceiver", "ScheduleRegisterReceiver", "GetSignalInfoReceiver",
              "SignalInfoRegisterReceiver", "OnBootReceiver", "SIMStateReceiver",
              "ConnectStateReceiver"]))
    check("sh: 接收器 FQN 前缀为 control 包",
          '"com.mobiletools.systemhelper.control." + simple' in sh)
    check("sh: 每个接收器入口候选唯一，否则跳过",
          "sh receiver skip " in sh and "cands.size() != 1" in sh)
    check("sh: 扫描范围限定在组件自身与混淆包",
          'startsWith("com.mobiletools.systemhelper")' in sh)
    check("sh: 三个开关默认开启",
          "Config.K_SH_POLICY, true" in sh and "Config.K_SH_USAGE, true" in sh
          and "Config.K_SH_RECEIVER, true" in sh)
    check("sh: 接入 dispatch",
          "new SystemHelperHooks(this).install(cl)" in entry)

    check("guard: 只 hook AiGuardService",
          'com.miui.guardprovider.aiguard.AiGuardService' in guard)
    check("guard: onStartCommand 返回 START_NOT_STICKY 并 stopSelf",
          "START_NOT_STICKY" in guard and "stopSelf" in guard)
    check("guard: 服务入口按签名判定",
          "isServiceOnStartCommand" in hooks and "Hooks.isServiceOnStartCommand" in guard)
    check("guard: 服务入口候选唯一，否则跳过",
          "guard aiguard skip cands=" in guard and "found != 1" in guard)
    check("guard: onStartCommand 判定含修饰符检查",
          "Modifier.isPublic" in hooks and "Modifier.isStatic" in hooks)
    check("guard: 开关默认开启并接入 dispatch",
          "Config.K_GP_AIGUARD, true" in entry
          and "new GuardHooks(this).install(cl)" in entry)

    # ---- J. 可选：签名在平台 jar 里真实存在 ----
    # 注意：这里校验的是公开基类 android.content.pm.PackageManager 的声明。
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

            # 短信发送签名（审计 P0-1）：校验谓词认定的每个重载在平台里真实存在。
            sms = javap_out(javap, android_jar, "android.telephony.SmsManager")
            pi = "android.app.PendingIntent"
            sms_expected = [
                "public void sendTextMessage(java.lang.String, java.lang.String, "
                "java.lang.String, %s, %s);" % (pi, pi),
                "public void sendMultipartTextMessage(java.lang.String, java.lang.String, "
                "java.util.ArrayList<java.lang.String>, java.util.ArrayList<%s>, "
                "java.util.ArrayList<%s>);" % (pi, pi),
                "public void sendDataMessage(java.lang.String, java.lang.String, short, "
                "byte[], %s, %s);" % (pi, pi),
            ]
            for sig in sms_expected:
                check("平台 jar(%s): SmsManager %s" % (api, sig.split("(")[0].split()[-1]),
                      sig in sms)
            # 旧代码写错的签名必须不存在（否则说明谓词/文档有误）
            check("平台 jar(%s): 不存在 sendTextMessage(String,String,String,String,PI)" % api,
                  "sendTextMessage(java.lang.String, java.lang.String, java.lang.String, "
                  "java.lang.String, %s)" % pi not in sms)
            check("平台 jar(%s): 不存在 5 参 sendDataMessage" % api,
                  "sendDataMessage(java.lang.String, java.lang.String, short, byte[], %s);"
                  % pi not in sms)

            # UsageStatsManager#queryEvents 重载（审计 P1-1）
            us = javap_out(javap, android_jar, "android.app.usage.UsageStatsManager")
            has_q_long = "queryEvents(long, long)" in us
            has_q_query = "queryEvents(android.app.usage.UsageEventsQuery)" in us
            check("平台 jar(%s): UsageStatsManager 声明 queryEvents(long,long)" % api, has_q_long)
            if api >= 35:
                check("平台 jar(%s): 声明 queryEvents(UsageEventsQuery)" % api, has_q_query)
            else:
                check("平台 jar(%s): queryEvents(UsageEventsQuery) 应不存在（旧系统）" % api,
                      not has_q_query)

            # LocationManager#getCurrentLocation 回调形态（2.1.0 P0-3）
            lm = javap_out(javap, android_jar, "android.location.LocationManager")
            if api >= 30:
                check("平台 jar(%s): getCurrentLocation 带 Executor+Consumer 重载" % api,
                      "getCurrentLocation(java.lang.String, android.location.LocationRequest, "
                      "java.util.concurrent.Executor, java.util.function.Consumer" in lm
                      or "getCurrentLocation(java.lang.String, "
                      "android.os.CancellationSignal, java.util.concurrent.Executor, "
                      "java.util.function.Consumer" in lm)
                check("平台 jar(%s): getCurrentLocation 返回 void（不是 Location）" % api,
                      "java.lang.Object getCurrentLocation" not in lm
                      and "android.location.Location getCurrentLocation" not in lm)
            else:
                check("平台 jar(%s): getCurrentLocation 应不存在（<API 30）" % api,
                      "getCurrentLocation" not in lm)
            check("平台 jar(%s): getLastKnownLocation(String) 返回 Location" % api,
                  "android.location.Location getLastKnownLocation(java.lang.String)" in lm)

            # Service#onStartCommand 契约（2.1.0 P0-2）
            svc = javap_out(javap, android_jar, "android.app.Service")
            check("平台 jar(%s): Service.onStartCommand(Intent,int,int)->int" % api,
                  "public int onStartCommand(android.content.Intent, int, int)" in svc)
            check("平台 jar(%s): Service.START_NOT_STICKY 常量存在" % api,
                  "START_NOT_STICKY" in svc)

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
