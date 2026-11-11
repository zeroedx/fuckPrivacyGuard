package io.github.zeroedx.fuckprivacyguard;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 配置。
 *
 * 界面侧写普通 SharedPreferences，再经 LSPosed 的 RemotePreferences 通道把整份配置
 * 推给各目标进程；hook 侧用同一通道读取。全程不需要 root：写入走框架的 Binder 服务，
 * 读取同理，不再调用 `su -c settings put`。
 *
 * 兼容旧数据：升级前写入的 Settings.Global(`fuckprivacyguard_conf`) 仍会被读取一次
 * 作为回落，读不到时按各规则的默认值运行。新配置不再写 Settings.Global。
 */
public final class Config {

    public static final String PKG = "io.github.zeroedx.fuckprivacyguard";
    public static final String PREFS = "pg";
    public static final String VERSION = "2.1.0";

    /** RemotePreferences 分组名，沿用旧配置键名，不引入新协议 */
    public static final String REMOTE_GROUP = "fuckprivacyguard_conf";
    /** RemotePreferences 里承载整份 `k=v|k=v` 的键 */
    public static final String REMOTE_KEY = "wire";
    /** 旧版写入 Settings.Global 的键，仅用于升级后的一次性回落读取 */
    public static final String GLOBAL_KEY = "fuckprivacyguard_conf";

    /* 全局 */
    public static final String K_MASTER = "master";
    public static final String K_PATH = "block_path_markers";
    public static final String K_SNIFF = "sniff_all";
    /** 自定义规则：域名 或 域名/路径，多条用 ;; 分隔 */
    public static final String K_CUSTOM = "custom_rules";

    /* 敏感数据：按应用独立，默认全部关闭 */
    public static final String K_LOC_SH = "loc_sh";       // SystemHelper 定位
    public static final String K_CELL_SH = "cell_sh";     // SystemHelper 基站
    public static final String K_WIFI_SH = "wifi_sh";     // SystemHelper Wi-Fi 扫描
    public static final String K_LOC_REG = "loc_reg";     // 运营商注册 定位
    public static final String K_CELL_REG = "cell_reg";   // 运营商注册 基站
    public static final String K_LOC_GP = "loc_gp";       // 反诈 定位
    public static final String K_WIFI_GP = "wifi_gp";     // 反诈 Wi-Fi 扫描
    public static final String K_SOCIAL_GP = "social_gp"; // 反诈 通讯录 / 通话
    public static final String K_LOC_SC = "loc_sc";       // 安全中心 定位
    public static final String K_SOCIAL_SC = "social_sc"; // 安全中心 通讯录 / 通话 / 短信
    public static final String K_CELL_DM = "cell_dm";     // 移动 DM 基站
    public static final String K_WIFI_DM = "wifi_dm";     // 移动 DM Wi-Fi 扫描

    /* 2.1.0 新增：SystemHelper 上报链路的许可判断 / 统计线程 / 广播接收器 */
    public static final String K_SH_POLICY = "sh_policy";   // 运营商服务改进许可判断
    public static final String K_SH_USAGE = "sh_usage";     // 应用使用统计线程
    public static final String K_SH_RECEIVER = "sh_receiver"; // 采集类广播接收器

    /* 分组总开关 */
    public static final String K_G_SYSHELPER = "g_syshelper";
    public static final String K_G_GUARD = "g_guardprovider";
    public static final String K_G_TRUST = "g_trustservice";
    public static final String K_G_ANALYTICS = "g_analytics";
    public static final String K_G_AIASST = "g_aiasst";
    public static final String K_G_SECCENTER = "g_seccenter";
    public static final String K_G_PKG = "g_packageinstaller";
    public static final String K_G_AD_MARKET = "g_ad_market";
    public static final String K_G_AD_XMSF = "g_ad_xmsf";
    public static final String K_G_MSA = "g_msa";

    /* SystemHelper */
    public static final String K_SYSHELPER = "block_syshelper";

    /* guardprovider */
    public static final String K_GUARD_APPLIST = "block_guard_applist";
    public static final String K_GUARD_AIGC = "block_guard_aigc";
    public static final String K_GUARD_FRAUD = "block_guard_fraud";

    /* trustservice */
    public static final String K_TRUST = "block_trustservice";

    /* analytics */
    public static final String K_ANALYTICS = "block_analytics";
    public static final String K_ANALYTICS_DOH = "block_analytics_doh";
    public static final String K_ANALYTICS_ALLNET = "analytics_all_net";
    public static final String K_SYSHELPER_ALLNET = "syshelper_all_net";

    /* MSA 广告组件 */
    /** 广告使用画像：查询应用今日是否打开、最后使用时间 */
    public static final String K_MSA_USAGE = "block_msa_usage";
    /** 广告埋点：OneTrack 的 track / adTrack 方法 */
    public static final String K_MSA_TRACK = "block_msa_track";

    /* 翻译组件 */
    public static final String K_AIASST = "block_aiasst";

    /* 安全中心 */
    public static final String K_SECCENTER = "block_securitycenter";
    public static final String K_SC_VIRUS = "block_sc_virus";
    public static final String K_SC_FRAMEWORK = "block_sc_framework";
    public static final String K_SC_DATA = "block_sc_data";
    public static final String K_SC_APPCLASS = "block_sc_appclass";
    public static final String K_SC_VOLUNTEER = "block_sc_volunteer";
    public static final String K_SC_LOCATION = "block_sc_location";
    public static final String K_SC_ADB = "block_sc_adb";
    public static final String K_SC_DBGUARD = "block_sc_dbguard";
    /** 安全中心的设备标识默认放行：拦了 IMSI / ICCID / 手机号，套餐与流量查询会失效 */
    public static final String K_SC_IDS = "block_sc_ids";
    /** 安全中心的广告位、网络助手推送与满意度问卷 */
    public static final String K_SC_MISC = "block_sc_misc";
    /** 安全中心的 OneTrack 统计埋点（tracking.*.miui.com / sdkconfig.*.xiaomi.com） */
    public static final String K_SC_TRACKING = "block_sc_tracking";
    /** guardprovider 的安犬引擎日志上传 */
    public static final String K_GP_ENGINELOG = "block_gp_enginelog";
    public static final String K_GP_CLOUDSCAN = "block_gp_cloudscan";
    public static final String K_GP_SCREENGUARD = "block_gp_screenguard";
    /** guardprovider 的 AI 通话预警检测服务（AiGuardService） */
    public static final String K_GP_AIGUARD = "block_gp_aiguard";
    public static final String K_TRUST_FIND = "block_trust_find";
    public static final String K_G_REGISTRATION = "g_registration";
    public static final String K_G_DMREG = "g_dmregservice";
    public static final String K_G_MISIGHT = "g_misightservice";
    public static final String K_MISIGHT_UPLOAD = "block_misight_upload";
    public static final String K_MISIGHT_MQSAS = "block_misight_mqsas";
    public static final String K_MISIGHT_KEFU = "block_misight_kefu";
    /** MiSight 事件日志上报域名 */
    public static final String K_MISIGHT_EVENTLOG = "block_misight_eventlog";
    public static final String K_DMREG = "block_dmreg";
    public static final String K_REG_ZZHC = "block_reg_zzhc";
    public static final String K_REG_TYPDM = "block_reg_typdm";
    public static final String K_XSOF = "block_xsof";

    /* 安装器 */
    public static final String K_PKG_AVL = "block_packageinstaller_avl";
    public static final String K_PKG_STAT = "block_packageinstaller_stat";
    public static final String K_AD_PKG = "block_ad_pkg";
    /** 安装器的 MSA OAID 获取 */
    public static final String K_PKG_OAID = "block_pkg_oaid";
    /** 安装器云端参数下发（含新未注册应用的备案提示） */
    public static final String K_PKG_CLOUD = "block_pkg_cloud";
    /** 安装时的云端风险校验与游戏防篡改检查（默认不拦，影响检测功能） */
    public static final String K_PKG_RISK = "block_pkg_risk";
    public static final String K_PKG_GAME = "block_pkg_game";

    /* 广告 */
    public static final String K_AD_MARKET = "block_ad_market";
    public static final String K_AD_XMSF = "block_ad_xmsf";
    /** 应用商店 / 服务框架自带的 OneTrack 埋点，路径 /track/ */
    public static final String K_MARKET_TRACK = "block_market_track";
    public static final String K_XMSF_TRACK = "block_xmsf_track";

    private Config() {
    }

    /* ---------------- UI 侧 ---------------- */

    public static SharedPreferences ui(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void uiSet(Context ctx, String key, boolean v) {
        uiSetLocal(ctx, key, v);
        pushAll(ctx);
    }

    /** 只写本地偏好、不推送；批量改开关时用，最后统一 pushAll 一次 */
    public static void uiSetLocal(Context ctx, String key, boolean v) {
        ui(ctx).edit().putBoolean(key, v).apply();
        invalidateCache();
    }

    /**
     * 把整份配置推到 LSPosed 的 RemotePreferences，目标进程经同一通道读取。
     *
     * 以通道里已有的值打底，再叠加本地偏好：本地偏好被清理过时，不会把其余开关
     * 一起冲成默认值。通道不可用（LSPosed 未连接 / 框架不支持）时不写入，只留日志，
     * 由界面在连接后重推一次。
     */
    public static void pushAll(Context ctx) {
        SharedPreferences remote = REMOTE;
        if (remote == null) {
            Logs.i("config push skipped: remote channel unavailable");
            return;
        }
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        try {
            map.putAll(ConfigWire.decode(remote.getString(REMOTE_KEY, null)));
        } catch (Throwable t) {
            Logs.i("config push base read fail: " + t);
        }
        for (java.util.Map.Entry<String, ?> e : ui(ctx).getAll().entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean) map.put(e.getKey(), String.valueOf(v));
            else if (v instanceof String) map.put(e.getKey(), (String) v);
        }
        String wire = ConfigWire.encode(map);
        if (wire == null) return;
        // 用 commit 而非 apply：改完开关后马上重启目标进程时，确保通道里已是新值
        final SharedPreferences target = remote;
        new Thread(() -> {
            try {
                target.edit().putString(REMOTE_KEY, wire).commit();
            } catch (Throwable t) {
                Logs.i("config push fail: " + t);
            }
        }, "fuckprivacyguard-config-push").start();
    }

    /** 界面读取开关：本地没有时回落到通道，清过数据也不会显示成默认值 */
    public static boolean get(Context ctx, String key, boolean def) {
        SharedPreferences sp = ui(ctx);
        if (sp.contains(key)) return sp.getBoolean(key, def);
        Boolean remote = fallback().get(key);
        return remote != null ? remote : def;
    }

    /** 通道里的整表，只在本地缺键时载入一次 */
    private static java.util.Map<String, Boolean> fallback() {
        if (FALLBACK != null) return FALLBACK;
        java.util.LinkedHashMap<String, Boolean> map = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, String> e : readWire().entrySet()) {
            map.put(e.getKey(), "true".equals(e.getValue()));
        }
        FALLBACK = map;
        return FALLBACK;
    }

    private static java.util.Map<String, Boolean> FALLBACK;

    /** 自定义规则原文，进程内只读一次 */
    private static volatile String CUSTOM = "";

    /** 界面改过开关后丢掉回落缓存，下次重新读 */
    public static void invalidateCache() {
        FALLBACK = null;
    }

    /* ---------------- 非 root 配置通道 ---------------- */

    /** 通道句柄：UI 侧来自 XposedService，hook 侧来自 XposedInterface；未绑定时为 null */
    private static volatile SharedPreferences REMOTE;

    /**
     * 绑定通道句柄；两端都实现 {@link SharedPreferences}（RemotePreferences）。
     * 传 null 表示通道断开，之后按旧版 Settings.Global 回落或默认值运行。
     */
    public static void bindRemote(SharedPreferences prefs) {
        REMOTE = prefs;
        invalidateCache();
    }

    public static SharedPreferences remote() {
        return REMOTE;
    }

    /** 读通道里的整份配置；未绑定或为空时返回空表 */
    private static java.util.Map<String, String> readWire() {
        SharedPreferences remote = REMOTE;
        if (remote == null) return java.util.Collections.emptyMap();
        try {
            String wire = remote.getString(REMOTE_KEY, null);
            if (wire != null && !wire.isEmpty()) return ConfigWire.decode(wire);
        } catch (Throwable t) {
            Logs.i("config remote read fail: " + t);
        }
        return java.util.Collections.emptyMap();
    }

    /**
     * 旧版 Settings.Global 回落（只读）。升级到本版本后新配置不再写这里，
     * 读到的是升级前最后一次写入的值；读不到按默认值。
     */
    private static java.util.Map<String, String> readLegacyGlobal(android.content.Context ctx) {
        if (ctx == null) return java.util.Collections.emptyMap();
        try {
            String v = android.provider.Settings.Global.getString(
                    ctx.getContentResolver(), GLOBAL_KEY);
            if (v == null || v.isEmpty()) return java.util.Collections.emptyMap();
            return ConfigWire.decode(v);
        } catch (Throwable t) {
            Logs.i("config legacy read fail: " + t);
            return java.util.Collections.emptyMap();
        }
    }

    /* ---------------- Hook 侧 ---------------- */

    public static boolean masterOn() {
        return on(K_MASTER, true);
    }

    /** hook 侧读到的开关，进程内缓存一份，读不到就用各规则的默认值 */
    private static final java.util.Map<String, Boolean> REMOTE_VALUES =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 目标进程加载界面开关：先读 RemotePreferences 通道，为空时读一次旧版
     * Settings.Global 作为升级回落。都读不到就按默认值运行。全程不写文件、不调 root。
     */
    public static void load(android.content.Context ctx) {
        if (!REMOTE_VALUES.isEmpty() || !CUSTOM.isEmpty()) return;

        java.util.Map<String, String> wire = readWire();
        if (wire.isEmpty()) wire = readLegacyGlobal(ctx);

        for (java.util.Map.Entry<String, String> e : wire.entrySet()) {
            if (K_CUSTOM.equals(e.getKey())) CUSTOM = e.getValue();
            else REMOTE_VALUES.put(e.getKey(), "true".equals(e.getValue()));
        }
        Logs.i(REMOTE_VALUES.isEmpty() ? "config loaded: 0 (defaults)"
                : "config loaded: " + REMOTE_VALUES.size());
    }

    /** 兼容旧调用点：RemotePreferences 不经 ContentResolver 鉴权，无需再按包名重读 */
    public static void load(android.content.Context ctx, String pkg) {
        load(ctx);
    }

    public static void setCustomRules(Context ctx, String rules) {
        SharedPreferences sp = ui(ctx);
        sp.edit().putString(K_CUSTOM, rules).apply();
        invalidateCache();
        pushAll(ctx);
    }

    /** 界面读取：本地优先，被清过时回落到通道里的同一份配置 */
    public static String customRulesRaw(Context ctx) {
        String v = ui(ctx).getString(K_CUSTOM, "");
        if (v != null && !v.isEmpty()) return v;
        String r = readWire().get(K_CUSTOM);
        return r != null ? r : "";
    }

    /** hook 侧解析：域名 或 域名/路径 */
    public static java.util.List<NetGuard.Rule> customRules() {
        java.util.List<NetGuard.Rule> out = new java.util.ArrayList<>();
        String s = CUSTOM;
        if (s == null || s.isEmpty()) return out;
        for (String item : s.split(";;")) {
            item = item.trim();
            if (item.isEmpty()) continue;
            if (item.indexOf('#') > 0) continue;   // 方法级规则，见 customMethodRules()
            int i = item.indexOf('/');
            if (i > 0) out.add(new NetGuard.Rule(item.substring(0, i), item.substring(i)));
            else out.add(new NetGuard.Rule(item));
        }
        return out;
    }

    /** 方法级规则：`类名#方法名`，方法名写 `*` 表示整类全部方法 */
    public static java.util.List<String> customMethodRules() {
        java.util.List<String> out = new java.util.ArrayList<>();
        String s = CUSTOM;
        if (s == null || s.isEmpty()) return out;
        for (String item : s.split(";;")) {
            item = item.trim();
            if (item.indexOf('#') <= 0 || item.endsWith("#")) continue;
            out.add(item);
        }
        return out;
    }

    public static int size() {
        return REMOTE_VALUES.size();
    }

    public static boolean on(String key, boolean fallback) {
        Boolean v = REMOTE_VALUES.get(key);
        return v == null ? fallback : v;
    }

    public static boolean strict() {
        return false;
    }
}
