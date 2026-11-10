package io.github.vivibibi30.hyperpguard;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 配置。
 *
 * UI 侧通过普通 SharedPreferences 读写；hook 侧在新 API 下无法直接读应用私有目录，
 * 因此使用各规则的默认值（与 UI 的默认值一致）。细粒度开关由「应用是否在作用域内」
 * 加默认规则共同决定。
 */
public final class Config {

    public static final String PKG = "io.github.vivibibi30.hyperpguard";
    public static final String PREFS = "pg";
    public static final String VERSION = "2.0.0";

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
        SharedPreferences sp = ui(ctx);
        sp.edit().putBoolean(key, v).apply();
        invalidateCache();
        syncOut(ctx, sp, key);
    }

    /**
     * 把开关写进 Settings.Global 的一个自定义 key，目标进程读它。
     *
     * 以 Settings 里已有的键值打底，再叠加本地偏好与本次改动：
     * 本地偏好被清理过时，不会把其余开关一起冲成默认值。
     */
    private static void syncOut(Context ctx, SharedPreferences sp, String changed) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        String cur = null;
        try {
            cur = android.provider.Settings.Global.getString(
                    ctx.getContentResolver(), GLOBAL_KEY);
        } catch (Throwable t) {
            Logs.i("config read before sync fail: " + t);
        }
        if (cur != null && !cur.isEmpty()) {
            for (String kv : cur.split("\\|")) {
                int i = kv.indexOf('=');
                if (i <= 0) continue;
                String k = kv.substring(0, i).trim();
                String val = kv.substring(i + 1).trim();
                map.put(k, K_CUSTOM.equals(k) ? urlDecode(val) : val);
            }
        }
        for (java.util.Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean) map.put(e.getKey(), String.valueOf(v));
            else if (v instanceof String) map.put(e.getKey(), (String) v);
        }
        Object localChanged = sp.getAll().get(changed);
        map.put(changed, localChanged instanceof String
                ? (String) localChanged : String.valueOf(sp.getBoolean(changed, false)));

        final StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : map.entrySet()) {
            String val = e.getValue();
            if (K_CUSTOM.equals(e.getKey())) {
                if (val == null || val.isEmpty()) continue;
                val = urlEncode(val);
            }
            if (sb.length() > 0) sb.append('|');
            sb.append(e.getKey()).append('=').append(val);
        }
        if (sb.length() == 0) return;
        final String dump = sb.toString();
        new Thread(() -> {
            Process p = null;
            try {
                p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                        "settings put global " + GLOBAL_KEY + " '" + dump + "'"});
                p.waitFor();
            } catch (Throwable t) {
                Logs.i("config sync fail: " + t);
            } finally {
                if (p != null) try { p.destroy(); } catch (Throwable ignored) { }
            }
        }, "hyperpguard-config-sync").start();
    }

    /** 界面读取开关：本地没有时回落到 Settings，清过数据也不会显示成默认值 */
    public static boolean get(Context ctx, String key, boolean def) {
        SharedPreferences sp = ui(ctx);
        if (sp.contains(key)) return sp.getBoolean(key, def);
        Boolean remote = fallback(ctx).get(key);
        return remote != null ? remote : def;
    }

    /** Settings 里的整表，只在本地缺键时载入一次 */
    private static java.util.Map<String, Boolean> fallback(Context ctx) {
        if (FALLBACK != null) return FALLBACK;
        java.util.LinkedHashMap<String, Boolean> map = new java.util.LinkedHashMap<>();
        try {
            String v = android.provider.Settings.Global.getString(
                    ctx.getContentResolver(), GLOBAL_KEY);
            if (v != null && !v.isEmpty()) {
                for (String kv : v.split("\\|")) {
                    int i = kv.indexOf('=');
                    if (i <= 0) continue;
                    map.put(kv.substring(0, i).trim(), "true".equals(kv.substring(i + 1).trim()));
                }
            }
        } catch (Throwable t) {
            Logs.i("config fallback fail: " + t);
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

    /* ---------------- Hook 侧 ---------------- */

    public static boolean masterOn() {
        return on(K_MASTER, true);
    }

    /** 唯一的对外写入：Settings.Global 下的一个自定义 key */
    public static final String GLOBAL_KEY = "hyperpguard_conf";

    /** hook 侧读到的开关，进程内缓存一份，读不到就用各规则的默认值 */
    private static final java.util.Map<String, Boolean> REMOTE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 目标进程读取界面开关：查询本应用的只读 provider。
     * 不写文件、不写系统设置，查询失败时按默认值运行。
     */
    public static void load(android.content.Context ctx) {
        load(ctx, null);
    }

    /**
     * pkg 用于系统 Context 读配置被 AMS 拒绝时，换成该包自己的 Context 再读一次。
     * 系统 Context 的包名是 android，uid 与包名不一致会被 provider 拒。
     */
    public static void load(android.content.Context ctx, String pkg) {
        if (!REMOTE.isEmpty() || !CUSTOM.isEmpty()) return;
        if (ctx != null) loadFromSettings(ctx);
        if (REMOTE.isEmpty() && ctx != null && pkg != null) {
            try {
                loadFromSettings(ctx.createPackageContext(pkg, 0));
            } catch (Throwable t) {
                Logs.i("config pkg ctx fail: " + t);
            }
        }
        Logs.i(REMOTE.isEmpty() ? "config loaded: 0 (defaults)"
                : "config loaded: " + REMOTE.size());
    }

    private static void loadFromSettings(android.content.Context ctx) {
        try {
            String v = android.provider.Settings.Global.getString(
                    ctx.getContentResolver(), GLOBAL_KEY);
            if (v == null || v.isEmpty()) return;
            for (String kv : v.split("\\|")) {
                int i = kv.indexOf('=');
                if (i <= 0) continue;
                String k = kv.substring(0, i).trim();
                String val = kv.substring(i + 1).trim();
                if (K_CUSTOM.equals(k)) CUSTOM = urlDecode(val);
                else REMOTE.put(k, "true".equals(val));
            }
        } catch (Throwable t) {
            Logs.i("config read fail: " + t);
        }
    }

    public static void setCustomRules(Context ctx, String rules) {
        SharedPreferences sp = ui(ctx);
        sp.edit().putString(K_CUSTOM, rules).apply();
        invalidateCache();
        syncOut(ctx, sp, K_CUSTOM);
    }

    /** 界面读取：本地优先，被清过时回落到 Settings 里的同一份配置 */
    public static String customRulesRaw(Context ctx) {
        String v = ui(ctx).getString(K_CUSTOM, "");
        if (v != null && !v.isEmpty()) return v;
        try {
            String all = android.provider.Settings.Global.getString(
                    ctx.getContentResolver(), GLOBAL_KEY);
            if (all != null) {
                for (String kv : all.split("\\|")) {
                    int i = kv.indexOf('=');
                    if (i > 0 && K_CUSTOM.equals(kv.substring(0, i).trim())) {
                        return urlDecode(kv.substring(i + 1).trim());
                    }
                }
            }
        } catch (Throwable t) {
            Logs.i("custom rules read fail: " + t);
        }
        return "";
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

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    public static int size() {
        return REMOTE.size();
    }

    public static boolean on(String key, boolean fallback) {
        Boolean v = REMOTE.get(key);
        return v == null ? fallback : v;
    }

    public static boolean strict() {
        return false;
    }
}
