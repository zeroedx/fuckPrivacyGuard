package io.github.vivibibi30.hyperpguard;

import java.lang.reflect.Method;
import java.util.ArrayList;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** 隐私 API 层：在数据来源处截断。 */
final class PrivacyApis {

    private final XposedModule m;

    PrivacyApis(XposedModule m) {
        this.m = m;
    }

    /**
     * 敏感数据按应用独立：每个包只提供与它实际采集行为对应的能力，
     * 每项一个开关，互不影响。
     */
    private static final java.util.Map<String, String[]> CAPS = new java.util.HashMap<>();

    static {
        CAPS.put("com.mobiletools.systemhelper", new String[]{
                Config.K_LOC_SH, Config.K_CELL_SH, Config.K_WIFI_SH});
        CAPS.put("com.xiaomi.registration", new String[]{
                Config.K_LOC_REG, Config.K_CELL_REG});
        CAPS.put("com.miui.guardprovider", new String[]{
                Config.K_LOC_GP, Config.K_WIFI_GP, Config.K_SOCIAL_GP});
        CAPS.put("com.miui.securitycenter", new String[]{
                Config.K_LOC_SC, Config.K_SOCIAL_SC});
        CAPS.put("com.miui.dmregservice", new String[]{
                Config.K_CELL_DM, Config.K_WIFI_DM});
    }

    void install(ClassLoader cl, String pkg, boolean appList, boolean usage,
                 boolean ids, boolean sms, boolean xmsf) {
        if (appList) {
            // 应用清单查询：Android 13+ 的 flags 重载与旧的 int 重载都转发到同一条链路
            // （AOSP ApplicationPackageManager#getInstalledPackages/getInstalledApplications），
            // 两个入口都要拦，否则调用方走 flags 重载就漏了。
            // flags 类在 API 33 之前不存在，用反射取，取不到就只挂 int 重载，避免拖垮整段安装。
            boolean a = Hooks.ret(m, cl, "android.app.ApplicationPackageManager",
                    "getInstalledPackages", new Class<?>[]{int.class},
                    new ArrayList<Object>(), "applist packages(int)");
            boolean c = Hooks.ret(m, cl, "android.app.ApplicationPackageManager",
                    "getInstalledApplications", new Class<?>[]{int.class},
                    new ArrayList<Object>(), "applist applications(int)");
            Class<?> pif = findClass(cl, "android.content.pm.PackageManager$PackageInfoFlags");
            Class<?> aif = findClass(cl, "android.content.pm.PackageManager$ApplicationInfoFlags");
            boolean b = pif != null && Hooks.ret(m, cl, "android.app.ApplicationPackageManager",
                    "getInstalledPackages", new Class<?>[]{pif},
                    new ArrayList<Object>(), "applist packages(flags)");
            boolean d = aif != null && Hooks.ret(m, cl, "android.app.ApplicationPackageManager",
                    "getInstalledApplications", new Class<?>[]{aif},
                    new ArrayList<Object>(), "applist applications(flags)");
            Logs.i("applist install int=" + (a && c) + " flags=" + (b && d));
        }
        if (usage) {
            Hooks.ret(m, cl, "android.app.usage.UsageStatsManager",
                    "queryEvents", new Class<?>[]{long.class, long.class}, null,
                    "usage queryEvents");
            Hooks.ret(m, cl, "android.app.usage.UsageStatsManager",
                    "queryUsageStats", new Class<?>[]{int.class, long.class, long.class},
                    new ArrayList<Object>(), "usage queryUsageStats");
        }
        if (ids) {
            // IMEI / MEID / DeviceId 返回 null；IMSI / ICCID 保留运营商字段
            int n = hookTelephonyIds(cl, "android.telephony.TelephonyManager");
            n += hookTelephonyIds(cl, "miui.telephony.TelephonyManager");
            n += hookTelephonyIds(cl, "miui.telephony.TelephonyManagerEx");
            Logs.i("telephony ids hooked=" + n);

            // MAC：给固定值而不是 null，避免调用方判空崩溃
            int mac = Hooks.retAllByName(m, cl, "android.net.wifi.WifiInfo",
                    new String[]{"getMacAddress"}, "02:00:00:00:00:00");
            Logs.i("mac hooked=" + mac);

            // 手机号
            int line = Hooks.retAllByName(m, cl, "android.telephony.TelephonyManager",
                    new String[]{"getLine1Number", "getLine1NumberForSubscriber"}, "");
            n += line;
            Logs.i("line number hooked=" + line);

            // 硬件序列号：与系统无权限时的返回值保持一致
            int serial = Hooks.retAllByName(m, cl, "android.os.Build",
                    new String[]{"getSerial"}, "unknown");
            Logs.i("serial hooked=" + serial);

            // Android ID：只拦读取，不影响其他 Settings 项
            hookAndroidId(cl);
        }

        // 以下默认关闭；每项只对本应用生效
        String[] caps = CAPS.get(pkg);
        if (caps != null) {
            int on = 0;
            for (String k : caps) {
                if (!Config.on(k, false)) continue;
                on++;
                if (k.startsWith("loc_")) hookLocation(cl);
                else if (k.startsWith("cell_")) hookCell(cl);
                else if (k.startsWith("wifi_")) hookWifi(cl);
                else hookResolver(cl);
            }
            Logs.i("sensitive hooks=" + on + " pkg=" + pkg);
        }
        if (sms) {
            Hooks.ret(m, cl, "android.telephony.SmsManager", "sendTextMessage",
                    new Class<?>[]{String.class, String.class, String.class, String.class,
                            android.app.PendingIntent.class}, null, "sms sendTextMessage");
            Hooks.ret(m, cl, "android.telephony.SmsManager", "sendMultipartTextMessage",
                    new Class<?>[]{String.class, String.class, java.util.List.class,
                            java.util.List.class, java.util.List.class}, null,
                    "sms sendMultipartTextMessage");
            Hooks.ret(m, cl, "android.telephony.SmsManager", "sendDataMessage",
                    new Class<?>[]{String.class, String.class, short.class, byte[].class,
                            android.app.PendingIntent.class}, null, "sms sendDataMessage");
        }
        if (xmsf) {
            hookXmsfBroadcast(cl, "android.content.ContextWrapper");
            hookXmsfBroadcast(cl, "android.content.ContextImpl");
        }
    }

    /** 反射取类，取不到返回 null（不触发 NoClassDefFoundError，也不打印堆栈） */
    private static Class<?> findClass(ClassLoader cl, String name) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Android ID：Settings.Secure.getString(cr, "android_id") */
    private void hookAndroidId(ClassLoader cl) {
        try {
            Class<?> secure = Class.forName("android.provider.Settings$Secure", false, cl);
            int n = 0;
            for (Method method : secure.getDeclaredMethods()) {
                if (!method.getName().equals("getString")) continue;
                Class<?>[] p = method.getParameterTypes();
                if (p.length != 2 || p[1] != String.class) continue;
                m.hook(method).intercept(chain -> {
                    Object key = chain.getArg(1);
                    if ("android_id".equals(key)) {
                        Hooks.noteHit(chain);
                        return null;
                    }
                    return chain.proceed();
                });
                n++;
            }
            Logs.i("android_id hooked=" + n);
        } catch (Throwable t) {
            Logs.i("android_id miss: " + t);
        }
    }

    /** 定位：返回 null / 不注册监听 */
    private void hookLocation(ClassLoader cl) {
        try {
            Class<?> lm = Class.forName("android.location.LocationManager", false, cl);
            int n = 0;
            for (Method method : lm.getDeclaredMethods()) {
                String name = method.getName();
                if (!name.equals("getLastKnownLocation")
                        && !name.equals("requestLocationUpdates")
                        && !name.equals("getCurrentLocation")) continue;
                try {
                    m.hook(method).intercept(chain -> {
                        Hooks.noteHit(chain);
                        Class<?> rt = ((java.lang.reflect.Method) chain.getExecutable()).getReturnType();
                        return rt == void.class || rt == boolean.class ? null : null;
                    });
                    n++;
                } catch (Throwable ignored) {
                }
            }
            Logs.i("location hooked=" + n);
        } catch (Throwable t) {
            Logs.i("location miss: " + t);
        }
    }

    /** 基站信息 */
    private void hookCell(ClassLoader cl) {
        try {
            Class<?> tm = Class.forName("android.telephony.TelephonyManager", false, cl);
            int n = 0;
            for (Method method : tm.getDeclaredMethods()) {
                String name = method.getName();
                if (!name.equals("getCellLocation") && !name.equals("getAllCellInfo")
                        && !name.equals("getNeighboringCellInfo")) continue;
                m.hook(method).intercept(chain -> {
                    Hooks.noteHit(chain);
                    Class<?> rt = ((java.lang.reflect.Method) chain.getExecutable()).getReturnType();
                    return java.util.List.class.isAssignableFrom(rt)
                            ? new java.util.ArrayList<>() : null;
                });
                n++;
            }
            Logs.i("cell hooked=" + n);
        } catch (Throwable t) {
            Logs.i("cell miss: " + t);
        }
    }

    /**
     * IMSI / ICCID 不返回 null：安全中心靠 IMSI 前 5 位的 MCC+MNC 识别运营商，
     * 返回 null 会让剩余流量、套餐、营业厅入口一起失效。
     * 这里保留真实运营商前缀，MSIN 与 ICCID 用固定假值。
     */
    private int hookTelephonyIds(ClassLoader cl, String cls) {
        int n = 0;
        try {
            Class<?> c = Class.forName(cls, false, cl);
            for (Method method : c.getDeclaredMethods()) {
                String name = method.getName();
                try {
                    if (name.contains("getSubscriberId")) {
                        m.hook(method).intercept(chain -> imsi(chain));
                    } else if (name.contains("getSimSerialNumber")) {
                        m.hook(method).intercept(chain -> ICCID);
                    } else if (name.contains("getDeviceId") || name.contains("getImei")
                            || name.contains("getMeid")) {
                        m.hook(method).intercept(chain -> null);
                    } else {
                        continue;
                    }
                    n++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Logs.i("telephony ids miss " + cls + " -> " + t);
        }
        return n;
    }

    private static final String ICCID = "8986000000000000000";

    /** 真实 MCC+MNC（5–6 位）+ 固定 MSIN，凑满 15 位 */
    private Object imsi(XposedInterface.Chain chain) {
        String op = operatorPrefix(chain);
        String s = op + "1234567890";
        return s.length() > 15 ? s.substring(0, 15) : s;
    }

    /** 优先取 SIM 的 MCC+MNC，其次网络的；都取不到时按 46000 兜底 */
    private String operatorPrefix(XposedInterface.Chain chain) {
        Object self = null;
        try {
            self = chain.getThisObject();
        } catch (Throwable ignored) {
        }
        if (self != null) {
            Object slot = null;
            try {
                if (chain.getArgs().size() == 1) slot = chain.getArg(0);
            } catch (Throwable ignored) {
            }
            for (String name : new String[]{"getSimOperator", "getNetworkOperator"}) {
                String v = callOperator(self, name, slot);
                if (isMccMnc(v)) return v;
            }
        }
        return "46000";
    }

    private static String callOperator(Object self, String name, Object slot) {
        Class<?> c = self.getClass();
        try {
            Object v = c.getMethod(name).invoke(self);
            if (v instanceof String && !((String) v).isEmpty()) return (String) v;
        } catch (Throwable ignored) {
        }
        if (slot instanceof Integer) {
            for (Class<?> t : new Class<?>[]{int.class, long.class}) {
                try {
                    Object arg = t == int.class ? slot : (Object) ((Integer) slot).longValue();
                    Object v = c.getMethod(name, t).invoke(self, arg);
                    if (v instanceof String) return (String) v;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean isMccMnc(String v) {
        if (v == null || v.length() < 5 || v.length() > 6) return false;
        for (int i = 0; i < v.length(); i++) {
            if (!Character.isDigit(v.charAt(i))) return false;
        }
        return true;
    }

    /** Wi-Fi 扫描与连接信息里的 SSID/BSSID */
    private void hookWifi(ClassLoader cl) {
        try {
            Class<?> wm = Class.forName("android.net.wifi.WifiManager", false, cl);
            int n = 0;
            for (Method method : wm.getDeclaredMethods()) {
                if (!method.getName().equals("getScanResults")) continue;
                m.hook(method).intercept(chain -> {
                    Hooks.noteHit(chain);
                    return new java.util.ArrayList<>();
                });
                n++;
            }
            // WifiInfo 的 SSID / BSSID
            Class<?> wi = Class.forName("android.net.wifi.WifiInfo", false, cl);
            for (Method method : wi.getDeclaredMethods()) {
                String name = method.getName();
                if (!name.equals("getSSID") && !name.equals("getBSSID")) continue;
                m.hook(method).intercept(chain -> {
                    Hooks.noteHit(chain);
                    return name.equals("getSSID") ? "<unknown ssid>" : "02:00:00:00:00:00";
                });
                n++;
            }
            Logs.i("wifi hooked=" + n);
        } catch (Throwable t) {
            Logs.i("wifi miss: " + t);
        }
    }

    /** 通讯录 / 通话记录 / 短信 查询：只拦这三类 uri，其余放行 */
    private void hookResolver(ClassLoader cl) {
        try {
            Class<?> cr = Class.forName("android.content.ContentResolver", false, cl);
            int n = 0;
            for (Method method : cr.getDeclaredMethods()) {
                if (!method.getName().equals("query")) continue;
                Class<?>[] p = method.getParameterTypes();
                if (p.length == 0 || p[0] != android.net.Uri.class) continue;
                m.hook(method).intercept(chain -> {
                    Object u = chain.getArg(0);
                    String s = u == null ? "" : u.toString();
                    if (s.startsWith("content://call_log")
                            || s.startsWith("content://sms")
                            || s.startsWith("content://mms")
                            || s.startsWith("content://com.android.contacts")) {
                        Hooks.noteHit(chain);
                        return null;
                    }
                    return chain.proceed();
                });
                n++;
            }
            Logs.i("resolver hooked=" + n);
        } catch (Throwable t) {
            Logs.i("resolver miss: " + t);
        }
    }

    private void hookXmsfBroadcast(ClassLoader cl, String cls) {
        try {
            Class<?> c = Class.forName(cls, false, cl);
            Method method = c.getDeclaredMethod("sendBroadcast", android.content.Intent.class);
            m.hook(method).intercept(chain -> {
                Object a = chain.getArg(0);
                if (a instanceof android.content.Intent) {
                    String act = ((android.content.Intent) a).getAction();
                    if ("com.xiaomi.xmsf.push.XMSF_UPLOAD_ACTIVE".equals(act)) {
                        Logs.i("block XMSF upload broadcast");
                        return null;
                    }
                }
                return chain.proceed();
            });
            Logs.i("hook ok " + cls + "#sendBroadcast(XMSF 过滤)");
        } catch (Throwable t) {
            Logs.i("hook miss " + cls + "#sendBroadcast -> " + t);
        }
    }
}
