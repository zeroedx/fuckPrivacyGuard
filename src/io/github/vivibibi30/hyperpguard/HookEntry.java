package io.github.vivibibi30.hyperpguard;

import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 模块入口（libxposed 新 API）。
 *
 * 拦截分四层，各自独立成文件：
 *   NetGuard      网络出口（URL / InetAddress / Socket）
 *   PrivacyApis   数据来源（应用列表 / 使用记录 / 设备标识 / 短信 / XMSF 广播）
 *   AnalyticsHooks 统计方法层（固定目标 + 签名扫描）
 *   XsofHooks     XSOF Provider / AIDL / 写库 / 评分 + MRM
 */
public class HookEntry extends XposedModule {

    private final NetGuard net = new NetGuard(this);
    private final PrivacyApis privacy = new PrivacyApis(this);
    private final AnalyticsHooks analytics = new AnalyticsHooks(this);
    private final XsofHooks xsof = new XsofHooks(this);

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        Logs.i("module loaded: " + param.getProcessName());
    }

    private final java.util.concurrent.atomic.AtomicBoolean dispatched =
            new java.util.concurrent.atomic.AtomicBoolean();

    /** 兜底线程，热重载前需要让它退出 */
    private volatile Thread fallbackThread;

    /**
     * 热重载请求。libxposed 默认返回 false，不覆写的话框架会判定本模块拒绝热重载，
     * 模块更新后所有已注入进程都会被记成「热重载失败」。
     */
    @Override
    public boolean onHotReloading(XposedModuleInterface.HotReloadingParam param) {
        Thread t = fallbackThread;
        if (t != null) t.interrupt();
        Logs.i("hot reloading");
        return true;
    }

    /**
     * 热重载完成，此时已经跑在新的模块类加载器里。
     * 框架不会重放 onModuleLoaded / onPackageReady，旧 hook 也要自己卸载，
     * 所以这里手动清掉旧 hook 再按当前包重新安装一遍。
     */
    @Override
    public void onHotReloaded(XposedModuleInterface.HotReloadedParam param) {
        for (Object handle : param.getOldHookHandles()) {
            if (!(handle instanceof io.github.libxposed.api.XposedInterface.HookHandle)) continue;
            try {
                ((io.github.libxposed.api.XposedInterface.HookHandle) handle).unhook();
            } catch (Throwable t) {
                Logs.i("hot reload unhook fail: " + t);
            }
        }
        android.app.Application app = currentApplication();
        if (app == null) {
            Logs.i("hot reload: no Application in " + param.getProcessName());
            return;
        }
        String pkg = app.getPackageName();
        Config.load(app.getApplicationContext());
        dispatched.set(false);
        dispatchOnce(pkg, app.getClassLoader());
        Logs.i("hot reloaded: " + pkg);
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        final String pkg = param.getPackageName();
        final ClassLoader cl = param.getClassLoader();
        if (pkg == null || cl == null) return;

        android.app.Application app = currentApplication();
        if (app != null) {
            // Application 已存在：拿到本进程 Context，配置和 hook 都按真实开关走
            Config.load(app.getApplicationContext());
            dispatchOnce(pkg, cl);
            return;
        }
        // 子进程常见情况：onPackageReady 时 Application 还没创建
        if (hookApplicationOnCreate(pkg, cl)) {
            return;
        }
        Config.load(activityThreadContext(), pkg);
        dispatchOnce(pkg, cl);
    }

    /** 等 Application.onCreate 再读配置并装 hook，返回是否挂上 */
    private boolean hookApplicationOnCreate(final String pkg, final ClassLoader cl) {
        try {
            java.lang.reflect.Method m = android.app.Application.class.getDeclaredMethod("onCreate");
            hook(m).intercept(chain -> {
                Object self = chain.getThisObject();
                if (self instanceof android.app.Application) {
                    Config.load(((android.app.Application) self).getApplicationContext());
                }
                dispatchOnce(pkg, cl);
                return chain.proceed();
            });
            // 兜底：Application 迟迟不创建时，用默认值先把拦截装上
            Thread fallback = new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                    return;
                }
                if (!dispatched.get()) {
                    Logs.i("onCreate timeout, use defaults: " + pkg);
                    Config.load(activityThreadContext(), pkg);
                    dispatchOnce(pkg, cl);
                }
            }, "hyperpguard-fallback");
            fallbackThread = fallback;
            fallback.start();
            Logs.i("waiting Application.onCreate: " + pkg);
            return true;
        } catch (Throwable t) {
            Logs.i("onCreate hook fail: " + t);
            return false;
        }
    }

    private void dispatchOnce(String pkg, ClassLoader cl) {
        if (!dispatched.compareAndSet(false, true)) return;
        if (!Config.masterOn()) {
            Logs.i("master off, skip: " + pkg);
            return;
        }
        try {
            dispatch(pkg, cl);
            final java.util.List<String> methodRules = Config.customMethodRules();
            if (!methodRules.isEmpty()) {
                stage("customMethods", () -> Hooks.customMethods(this, cl, methodRules));
            }
        } catch (Throwable t) {
            Logs.i("dispatch fail " + pkg + ": " + t);
            for (String line : android.util.Log.getStackTraceString(t).split("\n")) {
                if (!line.isEmpty()) Logs.i("dispatch stack " + pkg + ": " + line.trim());
            }
        }
    }

    /**
     * SystemHelper 4.x 起混淆类名变化：按签名在 c.d.a.* 里找数据出口。
     * 找不到时把候选类名（或整体类名样本）打进日志，便于补硬编码。
     */
    private boolean systemHelperUpload(ClassLoader cl) {
        final java.util.List<String> cands = Hooks.classNames(cl, s -> s.startsWith("c.d.a."), 600);
        int n = Hooks.hookBySignature(this, cands, cl,
                m -> {
                    Class<?>[] p = m.getParameterTypes();
                    return p.length == 3 && p[0] == String.class
                            && p[1] == byte[].class && p[2] == byte[].class
                            && m.getReturnType() == boolean.class;
                },
                chain -> {
                    Hooks.noteHit(chain);
                    return Boolean.FALSE;
                });
        Logs.i("systemhelper upload fallback=" + n + " scan=" + cands.size()
                + (n > 0 ? "" : " probe=" + probe(cl, cands)));
        return n > 0;
    }

    /** 写库入口：(String) 返回 void，方法名沿用 a */
    private boolean systemHelperWrite(ClassLoader cl) {
        final java.util.List<String> cands = Hooks.classNames(cl, s -> s.startsWith("c.d.a."), 600);
        int n = Hooks.hookBySignature(this, cands, cl,
                m -> m.getName().equals("a") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class
                        && m.getReturnType() == void.class,
                chain -> {
                    Hooks.noteHit(chain);
                    return null;
                });
        Logs.i("systemhelper write fallback=" + n + " scan=" + cands.size()
                + (n > 0 ? "" : " probe=" + probe(cl, cands)));
        return n > 0;
    }

    /** 候选为空说明包名前缀也变了，这时给一段整体类名样本 */
    private static String probe(ClassLoader cl, java.util.List<String> cands) {
        java.util.List<String> list = cands;
        if (list.isEmpty()) {
            list = Hooks.classNames(cl, s -> !s.startsWith("android.")
                    && !s.startsWith("java.") && !s.startsWith("kotlin."), 24);
        }
        StringBuilder sb = new StringBuilder("[");
        int max = Math.min(12, list.size());
        for (int i = 0; i < max; i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.append(']').toString();
    }

    /** 单步安装失败不影响同进程其余规则 */
    private void stage(String name, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Logs.i("stage " + name + " fail: " + t);
        }
    }

    private void netSafe(ClassLoader cl, String tag, List<NetGuard.Rule> rules, boolean strictAll) {
        try {
            List<NetGuard.Rule> all = new ArrayList<>(rules);
            all.addAll(Config.customRules());
            net.install(cl, tag, all, strictAll);
        } catch (Throwable t) {
            Logs.i("net install fail " + tag + ": " + t);
        }
    }

    private void privacySafe(ClassLoader cl, String pkg, boolean appList, boolean usage,
                             boolean ids, boolean sms, boolean xmsf) {
        try {
            privacy.install(cl, pkg, appList, usage, ids, sms, xmsf);
        } catch (Throwable t) {
            Logs.i("privacy install fail " + pkg + ": " + t);
        }
    }

    /** 进程已创建 Application 时直接取；早期返回 null */
    private android.app.Application currentApplication() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            if (thread == null) return null;
            java.lang.reflect.Field f = at.getDeclaredField("mInitialApplication");
            f.setAccessible(true);
            Object app = f.get(thread);
            return app instanceof android.app.Application ? (android.app.Application) app : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 系统 Context 的包名是 android，普通应用拿它查 provider 会被 AMS 拒。
     * LoadedApk#makeApplication 返回时 Application 已 attach，可用它自己的 Context 再读一次。
     */
    private void hookMakeApplication(final String pkg, final ClassLoader cl) {
        try {
            Class<?> loadedApk = Class.forName("android.app.LoadedApk", false, cl);
            for (java.lang.reflect.Method m : loadedApk.getDeclaredMethods()) {
                if (!m.getName().equals("makeApplication")) continue;
                if (m.getReturnType() != android.app.Application.class) continue;
                hook(m).intercept(chain -> {
                    Object r = chain.proceed();
                    try {
                        android.app.Application app = (android.app.Application) r;
                        Config.load(app.getApplicationContext());
                        Logs.i("app ctx config: " + Config.size());
                    } catch (Throwable t) {
                        Logs.i("app ctx config fail: " + t);
                    }
                    return r;
                });
                Logs.i("makeApplication hooked: " + pkg);
                return;
            }
            Logs.i("makeApplication not found: " + pkg);
        } catch (Throwable t) {
            Logs.i("makeApplication hook fail: " + t);
        }
    }

    /** 目标进程早期的系统 Context，只用来查询开关；拿不到就按默认值走 */
    private android.content.Context activityThreadContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            if (thread == null) return null;
            Object ctx = at.getMethod("getSystemContext").invoke(thread);
            return ctx instanceof android.content.Context ? (android.content.Context) ctx : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void dispatch(String pkg, ClassLoader cl) {
        if ("com.mobiletools.systemhelper".equals(pkg)) {
            if (!Config.on(Config.K_G_SYSHELPER, true)) return;
            Logs.i("attach systemhelper");
            privacySafe(cl, pkg, false, false, true, false, false);
            boolean upload = Hooks.ret(this, cl, "c.d.a.b.f", "a",
                    new Class<?>[]{String.class, byte[].class, byte[].class}, Boolean.FALSE,
                    "systemhelper upload");
            boolean write = Hooks.ret(this, cl, "c.d.a.b.e", "a", new Class<?>[]{String.class},
                    null, "systemhelper write");
            if (!upload) upload = systemHelperUpload(cl);
            if (!write) write = systemHelperWrite(cl);
            Logs.i("systemhelper direct upload=" + upload + " write=" + write);
            List<NetGuard.Rule> r = new ArrayList<>();
            if (Config.on(Config.K_SYSHELPER, true)) r.add(new NetGuard.Rule("dm.wo.com.cn"));
            netSafe(cl, "systemhelper", r, Config.on(Config.K_SYSHELPER_ALLNET, false));
            return;
        }

        if ("com.miui.guardprovider".equals(pkg)) {
            if (!Config.on(Config.K_G_GUARD, true)) return;
            List<NetGuard.Rule> r = new ArrayList<>();
            if (Config.on(Config.K_GUARD_APPLIST, true)) r.add(new NetGuard.Rule("flash.sec.miui.com"));
            if (Config.on(Config.K_GUARD_AIGC, true)) r.add(new NetGuard.Rule("detection-aigc.engine.miui.com"));
            if (Config.on(Config.K_GUARD_FRAUD, true)) r.add(new NetGuard.Rule("fraud-detection.engine.miui.com"));
            if (Config.on(Config.K_GP_CLOUDSCAN, false)) r.add(new NetGuard.Rule("virus-engine.sec.miui.com"));
            if (Config.on(Config.K_GP_ENGINELOG, true)) {
                r.add(new NetGuard.Rule("logupdate.avlyun.sec.miui.com"));
            }
            Logs.i("attach guardprovider, rules=" + r.size());
            privacySafe(cl, pkg, true, false, true, false, false);
            if (Config.on(Config.K_GP_SCREENGUARD, true)) {
                stage("screenCapture", () -> CaptureHooks.screenCapture(this, cl));
            }
            netSafe(cl, "guardprovider", r, false);
            return;
        }

        if ("com.miui.dmregservice".equals(pkg)) {
            // 移动 DM 组件：App 清单、SIM 标识、基站、Wi-Fi/蓝牙扫描、BSSID
            if (!Config.on(Config.K_G_DMREG, true)) return;
            Logs.i("attach dmregservice");
            privacySafe(cl, pkg, true, false, true, false, false);
            if (Config.on(Config.K_DMREG, true)) {
                List<NetGuard.Rule> r = new ArrayList<>();
                r.add(new NetGuard.Rule("fxltsbl.com"));
                netSafe(cl, "dmregservice", r, false);
            }
            return;
        }

        if ("com.miui.misightservice".equals(pkg)) {
            // 系统诊断：日志上传只在用户主动反馈或售后远程诊断时触发
            if (!Config.on(Config.K_G_MISIGHT, true)) return;
            Logs.i("attach misightservice");
            List<NetGuard.Rule> r = new ArrayList<>();
            if (Config.on(Config.K_MISIGHT_UPLOAD, true)) {
                r.add(new NetGuard.Rule("feedback.miui.com", "/misight/upload/"));
            }
            if (Config.on(Config.K_MISIGHT_MQSAS, true)) {
                r.add(new NetGuard.Rule("port.sec.miui.com", "/mqsas/kdxFileUpload"));
            }
            if (Config.on(Config.K_MISIGHT_KEFU, true)) {
                r.add(new NetGuard.Rule("chat.kefu.mi.com", "/openapi/misight/diagnostic/"));
            }
            if (Config.on(Config.K_MISIGHT_EVENTLOG, true)) {
                r.add(new NetGuard.Rule("event-log.cnbj1.mi-fds.com"));
            }
            netSafe(cl, "misightservice", r, false);
            return;
        }

        if ("com.xiaomi.trustservice".equals(pkg)) {
            if (!Config.on(Config.K_G_TRUST, true)) return;
            Logs.i("attach trustservice");
            if (Config.on(Config.K_TRUST, false)) stage("mrm", () -> xsof.mrm(cl));
            List<NetGuard.Rule> r = new ArrayList<>();
            r.add(new NetGuard.Rule("deviceapi.micloud.xiaomi.net"));
            if (Config.on(Config.K_TRUST_FIND, false)) {
                r.add(new NetGuard.Rule("find.api.micloud.xiaomi.net"));
            }
            netSafe(cl, "trustservice", r, false);
            return;
        }

        if ("com.miui.analytics".equals(pkg)) {
            if (!Config.on(Config.K_G_ANALYTICS, true)) return;
            if (!Config.on(Config.K_ANALYTICS, true)) return;
            Logs.i("attach analytics");
            stage("analytics", () -> analytics.install(cl));
            privacySafe(cl, pkg, true, true, true, false, true);
            List<NetGuard.Rule> r = new ArrayList<>();
            r.add(new NetGuard.Rule("miui.com"));
            r.add(new NetGuard.Rule("xiaomi.com"));
            r.add(new NetGuard.Rule("xiaomi.net"));
            r.add(new NetGuard.Rule("onetrack.mi.com"));
            r.add(new NetGuard.Rule("pubsub.googleapis.com"));
            if (Config.on(Config.K_ANALYTICS_DOH, true)) {
                r.add(new NetGuard.Rule("dns.google"));
                r.add(new NetGuard.Rule("8.8.8.8"));
                r.add(new NetGuard.Rule("8.8.4.4"));
            }
            netSafe(cl, "analytics", r, Config.on(Config.K_ANALYTICS_ALLNET, true));
            return;
        }

        if ("com.xiaomi.aiasst.vision".equals(pkg)) {
            if (!Config.on(Config.K_G_AIASST, true)) return;
            if (!Config.on(Config.K_AIASST, false)) return;
            Logs.i("attach aiasst.vision");
            List<NetGuard.Rule> r = new ArrayList<>();
            r.add(new NetGuard.Rule("xiaomixiaoai.com", "/speech/"));
            netSafe(cl, "aiasst", r, false);
            return;
        }

        if ("com.miui.securitycenter".equals(pkg)) {
            if (!Config.on(Config.K_G_SECCENTER, true)) return;
            Logs.i("attach securitycenter");
            // 设备标识默认放行：安全中心要靠 IMSI / ICCID / 手机号查套餐与流量
            privacySafe(cl, pkg, false, false,
                    Config.on(Config.K_SC_IDS, false), false, false);
            if (Config.on(Config.K_XSOF, true)) stage("xsof", () -> xsof.install(cl));
            if (Config.on(Config.K_SC_DBGUARD, true)) {
                stage("dbWrites", () -> CaptureHooks.dbWrites(this, cl));
            }
            List<NetGuard.Rule> r = new ArrayList<>();
            if (Config.on(Config.K_SECCENTER, true)) {
                r.add(new NetGuard.Rule("fraud-detection.engine.miui.com"));
            }
            r.add(new NetGuard.Rule("flash.sec.miui.com", "/detect/"));
            if (Config.on(Config.K_SC_VIRUS, false)) r.add(new NetGuard.Rule("virus-engine.sec.miui.com"));
            if (Config.on(Config.K_SC_FRAMEWORK, false)) r.add(new NetGuard.Rule("framework.sec.miui.com"));
            if (Config.on(Config.K_SC_DATA, false)) r.add(new NetGuard.Rule("data.sec.miui.com"));
            if (Config.on(Config.K_SC_APPCLASS, false)) r.add(new NetGuard.Rule("flash.sec.miui.com", "/user/cat"));
            if (Config.on(Config.K_SC_VOLUNTEER, true)) {
                r.add(new NetGuard.Rule("api.sec.miui.com", "/earthquake/volunteer/sensor/upload"));
            }
            if (Config.on(Config.K_SC_LOCATION, false)) {
                r.add(new NetGuard.Rule("api.sec.miui.com", "/LBS/"));
                r.add(new NetGuard.Rule("api.sec.miui.com", "/earthquake/volunteer/"));
                r.add(new NetGuard.Rule("srv.sec.miui.com", "/earthquake/"));
                r.add(new NetGuard.Rule("gateway-papa.cloud.weizhi.com"));
                r.add(new NetGuard.Rule("eps.caict.ac.cn"));
            }
            if (Config.on(Config.K_SC_ADB, false)) r.add(new NetGuard.Rule("srv.sec.miui.com", "/data/adb"));
            if (Config.on(Config.K_SC_MISC, true)) {
                r.add(new NetGuard.Rule("adv.sec.miui.com", "/info/layout"));
                r.add(new NetGuard.Rule("net-assistant.10046.xiaomimobile.com"));
                r.add(new NetGuard.Rule("flash.sec.miui.com", "/satisfaction/"));
            }
            if (Config.on(Config.K_SC_TRACKING, true)) {
                // OneTrack 统计埋点；host 是裸域名，按区域各自一条
                r.add(new NetGuard.Rule("tracking.miui.com"));
                r.add(new NetGuard.Rule("tracking.rus.miui.com"));
                r.add(new NetGuard.Rule("tracking.intl.miui.com"));
                r.add(new NetGuard.Rule("tracking.india.miui.com"));
                r.add(new NetGuard.Rule("tracking.eu.miui.com"));
                r.add(new NetGuard.Rule("sdkconfig.xiaomi.com"));
                r.add(new NetGuard.Rule("sdkconfig.rus.xiaomi.com"));
                r.add(new NetGuard.Rule("sdkconfig.intl.xiaomi.com"));
                r.add(new NetGuard.Rule("sdkconfig.india.xiaomi.com"));
                r.add(new NetGuard.Rule("sdkconfig.eu.xiaomi.com"));
            }
            netSafe(cl, "securitycenter", r, false);
            return;
        }


        if ("com.miui.systemAdSolution".equals(pkg)) {
            if (!Config.on(Config.K_G_MSA, true)) return;
            Logs.i("attach msa");
            if (Config.on(Config.K_MSA_USAGE, true)) {
                stage("msaUsage", () -> new MsaHooks(this).installUsage(cl));
            }
            if (Config.on(Config.K_MSA_TRACK, true)) {
                stage("msaTrack", () -> new MsaHooks(this).installTrack(cl));
            }
            // 广告 SDK 失败会重试，域名层默认不拦，保留自定义规则入口
            netSafe(cl, "msa", new ArrayList<>(), false);
            return;
        }

        if ("com.xiaomi.registration".equals(pkg)) {
            if (!Config.on(Config.K_G_REGISTRATION, true)) return;
            Logs.i("attach registration");
            privacySafe(cl, pkg, false, false, true, true, false);
            List<NetGuard.Rule> r = new ArrayList<>();
            if (Config.on(Config.K_REG_ZZHC, false)) r.add(new NetGuard.Rule("zzhc.vnet.cn"));
            if (Config.on(Config.K_REG_TYPDM, true)) r.add(new NetGuard.Rule("typdm.tydevice.com"));
            netSafe(cl, "registration", r, false);
            return;
        }

        if ("com.miui.packageinstaller".equals(pkg)) {
            if (!Config.on(Config.K_G_PKG, true)) return;
            boolean pathOn = Config.on(Config.K_PATH, true);
            List<NetGuard.Rule> r = new ArrayList<>();
            if (Config.on(Config.K_PKG_AVL, true)) {
                r.add(new NetGuard.Rule("api.installer.xiaomi.com", "/avl/upload"));
            }
            if (pathOn && Config.on(Config.K_PKG_STAT, true)) {
                r.add(new NetGuard.Rule("api.installer.xiaomi.com", "/statistics/appactive"));
            }
            if (Config.on(Config.K_AD_PKG, true)) {
                // 这三个是纯域名规则，不该受「路径特征拦截」开关影响
                r.add(new NetGuard.Rule("api.ad.xiaomi.com"));
                r.add(new NetGuard.Rule("monitor-check.ad.xiaomi.com"));
                r.add(new NetGuard.Rule("sdkconfig.ad.xiaomi.com"));
                if (pathOn) {
                    r.add(new NetGuard.Rule("api.installer.xiaomi.com", "/info/layout"));
                    // 推荐位的应用图标走商店图片 CDN，只拦缩略图路径
                    r.add(new NetGuard.Rule("t1.a.market.xiaomi.com", "/thumbnail/"));
                    r.add(new NetGuard.Rule("t2.a.market.xiaomi.com", "/thumbnail/"));
                }
            }
            if (Config.on(Config.K_PKG_CLOUD, true)) {
                // 云端参数：新未注册应用的备案提示、按钮文案、安全模式默认值都从这里下发
                // 5.4.x 安装器改走 /v3/cloud_config/query，v2 只覆盖老版本
                r.add(new NetGuard.Rule("api.installer.xiaomi.com", "/v2/cloud_config/query"));
                r.add(new NetGuard.Rule("api.installer.xiaomi.com", "/v3/cloud_config/query"));
                r.add(new NetGuard.Rule("preview-api.installer.xiaomi.com", "/v2/cloud_config/query"));
                r.add(new NetGuard.Rule("preview-api.installer.xiaomi.com", "/v3/cloud_config/query"));
            }
            if (Config.on(Config.K_PKG_RISK, false)) {
                // 云端风险校验：会带上待装应用的包名与哈希
                r.add(new NetGuard.Rule("verify.sec.xiaomi.com"));
                r.add(new NetGuard.Rule("tz.sec.xiaomi.com"));
            }
            if (Config.on(Config.K_PKG_GAME, false)) {
                // 游戏安装时的防篡改检查，含 normal / safemode / enhancemode
                r.add(new NetGuard.Rule("api.installer.xiaomi.com", "/v2/game/interceptcheck"));
                r.add(new NetGuard.Rule("api.installer.xiaomi.com", "/v3/game/interceptcheck"));
                r.add(new NetGuard.Rule("api.installer.xiaomi.com", "/v4/game/interceptcheck"));
            }
            // 方法层：MSA OAID，返回空串
            if (Config.on(Config.K_PKG_OAID, true)) {
                final java.util.List<String> oaCands = Hooks.classNames(
                        cl, s -> s.contains("OAID") || s.contains("MdidSdkHelper"), 80);
                int n = Hooks.hookBySignature(this, oaCands, cl,
                        m -> m.getReturnType() == String.class
                                && m.getParameterCount() <= 2
                                && m.getName().toLowerCase().contains("oaid"),
                        chain -> "");
                if (n == 0) {
                    // 方法名被混淆时，退到「类名含 OAID、返回 String、参数不超过 2」
                    n = Hooks.hookBySignature(this, oaCands, cl,
                            m -> m.getReturnType() == String.class && m.getParameterCount() <= 2,
                            chain -> "");
                }
                Logs.i("packageinstaller oaid hooked=" + n);
            }
            // 方法层：广告缓存读取，返回 null 让推荐位不渲染
            if (Config.on(Config.K_AD_PKG, true)) {
                int n = Hooks.hookBySignature(this,
                        Hooks.classNames(cl, s -> !s.startsWith("android.")
                                && !s.startsWith("java.") && !s.startsWith("kotlin."), 2500),
                        cl,
                        m -> m.getParameterCount() == 1
                                && m.getParameterTypes()[0] == String.class
                                && m.getReturnType().getName().contains("AdModel"),
                        chain -> null);
                Logs.i("packageinstaller ad cache hooked=" + n);
            }
            // 方法层：安装页广告位请求入口，static void 7 参、第 3 参 Integer、末参回调接口
            if (Config.on(Config.K_AD_PKG, true)) {
                Hooks.MethodFilter ad = m -> java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        && m.getReturnType() == void.class
                        && m.getParameterCount() == 7
                        && m.getParameterTypes()[0] == String.class
                        && m.getParameterTypes()[1] == String.class
                        && m.getParameterTypes()[2] == Integer.class
                        && m.getParameterTypes()[3] == String.class
                        && m.getParameterTypes()[6].isInterface();
                int n = Hooks.hookBySignature(this,
                        Hooks.classNames(cl, s -> !s.startsWith("android.")
                                && !s.startsWith("java.") && !s.startsWith("kotlin."), 2500),
                        cl, ad, chain -> null);
                Logs.i("packageinstaller ad method hooked=" + n);
            }
            // 方法层：安装活跃统计入口，AppActiveStatService 内的 void(List) 方法
            if (Config.on(Config.K_PKG_STAT, true)) {
                int n = Hooks.hookBySignature(this,
                        Hooks.classNames(cl, s -> s.contains("AppActiveStatService"), 40), cl,
                        m -> m.getReturnType() == void.class
                                && m.getParameterCount() == 1
                                && java.util.List.class.isAssignableFrom(m.getParameterTypes()[0]),
                        chain -> null);
                Logs.i("packageinstaller stat method hooked=" + n);
            }
            // 方法层：AVL 上报入口，静态 4 参、第二参为 ApkInfo、返回 void
            if (Config.on(Config.K_PKG_AVL, true)) {
                Hooks.MethodFilter avl = m -> java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        && m.getParameterCount() == 4
                        && m.getParameterTypes()[1].getName().contains("ApkInfo")
                        && m.getReturnType() == void.class;
                int n = Hooks.hookBySignature(this,
                        Hooks.classNames(cl, s -> s.startsWith("C2."), 900), cl, avl, chain -> null);
                if (n == 0) {
                    // 包名前缀变了就退到全量扫描
                    n = Hooks.hookBySignature(this,
                            Hooks.classNames(cl, s -> !s.startsWith("android.")
                                    && !s.startsWith("java.") && !s.startsWith("kotlin."), 2500),
                            cl, avl, chain -> null);
                }
                Logs.i("packageinstaller avl method hooked=" + n);
            }
            Logs.i("attach packageinstaller, rules=" + r.size());
            netSafe(cl, "packageinstaller", r, false);
            return;
        }

        if ("com.xiaomi.market".equals(pkg)) {
            if (!Config.on(Config.K_G_AD_MARKET, true)) return;
            Logs.i("attach market");
            List<NetGuard.Rule> r = new ArrayList<>();
            if (Config.on(Config.K_AD_MARKET, true)) r.add(new NetGuard.Rule("sdkconfig.ad.xiaomi.com"));
            if (Config.on(Config.K_MARKET_TRACK, true)) {
                r.add(new NetGuard.Rule("tracking.miui.com", "/track/"));
            }
            netSafe(cl, "market", r, false);
            return;
        }

        if ("com.xiaomi.xmsf".equals(pkg)) {
            if (!Config.on(Config.K_G_AD_XMSF, true)) return;
            Logs.i("attach xmsf");
            List<NetGuard.Rule> r = new ArrayList<>();
            if (Config.on(Config.K_AD_XMSF, true)) r.add(new NetGuard.Rule("adv.sec.miui.com"));
            if (Config.on(Config.K_XMSF_TRACK, true)) {
                r.add(new NetGuard.Rule("tracking.miui.com", "/track/"));
            }
            netSafe(cl, "xmsf", r, false);
            return;
        }
    }
}
