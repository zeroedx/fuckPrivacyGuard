package io.github.zeroedx.fuckprivacyguard;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/** analytics 方法层：固定目标 + 签名扫描。 */
final class AnalyticsHooks {

    private final XposedModule m;

    AnalyticsHooks(XposedModule m) {
        this.m = m;
    }

    void install(ClassLoader cl) {
        Hooks.ret(m, cl, "com.miui.analytics.onetrack.p.u", "w",
                new Class<?>[]{long.class, long.class}, null, "analytics p.u#w");
        Hooks.ret(m, cl, "com.miui.analytics.c.f.k", "y",
                new Class<?>[]{long.class, long.class}, null, "analytics c.f.k#y");
        Hooks.ret(m, cl, "com.miui.analytics.onetrack.q.c", "a",
                new Class<?>[]{android.content.Context.class}, Integer.valueOf(0),
                "analytics q.c#a");
        Hooks.ret(m, cl, "com.miui.analytics.onetrack.q.c", "b",
                new Class<?>[]{android.content.Context.class}, "NONE", "analytics q.c#b");
        Hooks.ret(m, cl, "com.miui.analytics.onetrack.q.c", "c",
                new Class<?>[0], Boolean.FALSE, "analytics q.c#c");
        Hooks.ret(m, cl, "com.miui.analytics.d.f.n", "y",
                new Class<?>[]{long.class, long.class}, null, "analytics d.f.n#y");
        scanUsageMethods(cl);
    }

    /** 遍历 analytics 的 dex，把 (long, long) -> JSONObject 的方法全部置空。 */
    private void scanUsageMethods(ClassLoader cl) {
        int scanned = 0, hooked = 0, failed = 0, skipped = 0;
        try {
            java.util.List<String> names = new java.util.ArrayList<>();
            Class<?> bdcl = Class.forName("dalvik.system.BaseDexClassLoader", false, cl);
            java.lang.reflect.Field fPathList = bdcl.getDeclaredField("pathList");
            fPathList.setAccessible(true);
            Object pathList = fPathList.get(cl);
            if (pathList == null) return;
            java.lang.reflect.Field fElems = pathList.getClass().getDeclaredField("dexElements");
            fElems.setAccessible(true);
            Object[] elems = (Object[]) fElems.get(pathList);
            for (Object el : elems) {
                if (el == null) continue;
                try {
                    java.lang.reflect.Field fDex = el.getClass().getDeclaredField("dexFile");
                    fDex.setAccessible(true);
                    Object dex = fDex.get(el);
                    if (dex instanceof dalvik.system.DexFile) {
                        java.util.Enumeration<String> en = ((dalvik.system.DexFile) dex).entries();
                        while (en.hasMoreElements() && names.size() < 20000) {
                            names.add(en.nextElement());
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            for (String n : names) {
                if (!n.startsWith("com.miui.analytics") && !n.startsWith("a.b.a.")) continue;
                Class<?> c;
                try {
                    c = Class.forName(n, false, cl);
                } catch (Throwable t) {
                    continue;
                }
                scanned++;
                Method[] ms;
                try {
                    ms = c.getDeclaredMethods();   // 签名含运行时缺失类型时整类抛错
                } catch (Throwable t) {
                    skipped++;
                    continue;
                }
                for (Method method : ms) {
                    Class<?>[] p;
                    Class<?> ret;
                    try {
                        p = method.getParameterTypes();
                        ret = method.getReturnType();
                    } catch (Throwable t) {
                        continue;
                    }
                    if (p.length != 2 || p[0] != long.class || p[1] != long.class) continue;
                    if (ret != org.json.JSONObject.class) continue;
                    try {
                        m.hook(method).intercept(chain -> {
                            Hooks.noteHit(chain, "analytics usage");
                            return null;
                        });
                        hooked++;
                    } catch (Throwable t) {
                        failed++;
                    }
                }
            }
            Logs.i("analytics scan scanned=" + scanned + " hooked=" + hooked
                    + (failed > 0 ? " failed=" + failed : "")
                    + (skipped > 0 ? " skipped=" + skipped : ""));
        } catch (Throwable t) {
            Logs.i("analytics scan fail: " + t);
        }
    }
}
