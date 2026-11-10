package io.github.zeroedx.fuckprivacyguard;

import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** XSOF 四层：Provider / AIDL / 写库 / 评分，以及 MRM 服务屏蔽。 */
final class XsofHooks {

    private static final AtomicBoolean XSOF_HIT = new AtomicBoolean(false);

    private final XposedModule m;

    XsofHooks(XposedModule m) {
        this.m = m;
    }

    void install(ClassLoader cl) {
        installProvider(cl);
        installAidl(cl);
        installDbWrites(cl);
        installScore(cl);
    }

    private void installProvider(ClassLoader cl) {
        try {
            Class<?> prov = Class.forName("com.xiaomi.security.xsof.XSOFProvider", false, cl);
            Hooks.allByName(m, prov, "query", chain -> {
                Hooks.noteHit(chain, "xsof query");
                if (XSOF_HIT.compareAndSet(false, true)) Logs.i("xsof query blocked (first)");
                return new android.database.MatrixCursor(new String[]{"_id"});
            });
            Hooks.allByName(m, prov, "call", chain -> {
                Hooks.noteHit(chain, "xsof call");
                return new Bundle();
            });
            Hooks.allByName(m, prov, "insert", chain -> {
                Hooks.noteHit(chain, "xsof insert");
                return null;
            });
            Logs.i("xsof provider hooked");
        } catch (Throwable t) {
            Logs.i("xsof provider miss: " + t);
        }
    }

    private void installAidl(ClassLoader cl) {
        int n = hookAidl(cl, "com.xiaomi.security.xsof.MiSafetyDetectService$MiSafetyDetectImpl");
        if (n == 0) {
            java.util.List<String> cands = Hooks.classNames(cl, s -> s.contains("MiSafetyDetect"), 50);
            for (String c : cands) n += hookAidl(cl, c);
            Logs.i("xsof aidl fallback cands=" + cands.size());
        }
        Logs.i("xsof aidl hooked=" + n);
    }

    private int hookAidl(ClassLoader cl, String className) {
        int n = 0;
        try {
            Class<?> impl = Class.forName(className, false, cl);
            for (Method method : impl.getDeclaredMethods()) {
                Class<?>[] pts = method.getParameterTypes();
                if (pts.length < 2) continue;
                if (!pts[pts.length - 1].getName().contains("IMiSafetyDetectCallback")) continue;
                m.hook(method).intercept(chain -> {
                    Hooks.noteHit(chain, "xsof aidl");
                    Object cb = chain.getArg(chain.getArgs().size() - 1);
                    if (cb != null) {
                        try {
                            Method g2 = cb.getClass().getMethod("G2", String.class, int.class);
                            g2.invoke(cb, null, Integer.valueOf(1017));
                        } catch (Throwable ignored) {
                        }
                    }
                    return null;
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    private void installDbWrites(ClassLoader cl) {
        int n = hookBehaviorHelper(cl);
        if (n == 0) {
            // 结构化识别失败时才退回写死的旧混淆名
            n = hookDbWritesIn(cl, "Ra.k");
            if (n > 0) Logs.i("xsof db write legacy=Ra.k x" + n);
        }
        Logs.i("xsof db write hooked=" + n);
    }

    /**
     * 行为库 helper 的结构特征：SQLiteOpenHelper 子类、实现同包接口、
     * 且有一组 public void 的 bean 参数写方法。
     * 12.8.6 是 Ra.k + Qa.*，13.6.6 是 jo.k + io.*，类名和 bean 包都换过，
     * 按签名谓词写死 Qa.* 在 13.6.6 上一个都命中不了。
     */
    private int hookBehaviorHelper(ClassLoader cl) {
        int n = 0;
        for (String name : Hooks.classNames(cl, XsofHooks::obfuscatedLikely, 8000)) {
            Class<?> c;
            Method[] ms;
            try {
                c = Class.forName(name, false, cl);
                ms = c.getDeclaredMethods();
            } catch (Throwable t) {
                continue;
            }
            String pkg = name.lastIndexOf('.') < 0 ? "" : name.substring(0, name.lastIndexOf('.'));
            if (!isBehaviorHelper(c, pkg)) continue;
            java.util.List<Method> writes = new java.util.ArrayList<>();
            for (Method method : ms) {
                boolean beanWrite;
                try {
                    beanWrite = isBeanWrite(method, pkg);
                } catch (Throwable t) {
                    continue;
                }
                if (beanWrite) writes.add(method);
            }
            if (writes.size() < 4) continue;
            int ok = 0;
            for (Method method : writes) {
                try {
                    m.hook(method).intercept(chain -> {
                        Hooks.noteHit(chain, "xsof db write");
                        return null;
                    });
                    ok++;
                } catch (Throwable ignored) {
                }
            }
            Logs.i("xsof db helper " + name + " writes=" + writes.size() + " hooked=" + ok);
            n += ok;
        }
        return n;
    }

    private static boolean isBehaviorHelper(Class<?> c, String pkg) {
        try {
            Class<?> sup = c.getSuperclass();
            if (sup == null || !"android.database.sqlite.SQLiteOpenHelper".equals(sup.getName())) {
                return false;
            }
            for (Class<?> itf : c.getInterfaces()) {
                String in = itf.getName();
                String ip = in.lastIndexOf('.') < 0 ? "" : in.substring(0, in.lastIndexOf('.'));
                if (ip.equals(pkg)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** public void，首参是本包内 bean（Qa.* \/ io.*），参数 1~2 个 */
    private static boolean isBeanWrite(Method method, String pkg) {
        if (method.getReturnType() != void.class) return false;
        if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) return false;
        int pc = method.getParameterCount();
        if (pc < 1 || pc > 2) return false;
        String p0 = method.getParameterTypes()[0].getName();
        String pp = p0.lastIndexOf('.') < 0 ? "" : p0.substring(0, p0.lastIndexOf('.'));
        return pp.equals(pkg);
    }

    private int hookDbWritesIn(ClassLoader cl, String className) {
        int n = 0;
        Method[] ms;
        try {
            ms = Class.forName(className, false, cl).getDeclaredMethods();
        } catch (Throwable t) {
            return 0;
        }
        for (Method method : ms) {
            boolean hit;
            try {
                hit = isDbWrite(method);   // getParameterTypes 也会解析签名
            } catch (Throwable t) {
                continue;
            }
            if (!hit) continue;
            try {
                m.hook(method).intercept(chain -> {
                    Hooks.noteHit(chain, "xsof db write");
                    return null;
                });
                n++;
            } catch (Throwable ignored) {
            }
        }
        return n;
    }

    /** 版本变化后混淆类名会变，靠"短名或 XSOF 包内"缩小扫描范围 */
    private static boolean obfuscatedLikely(String name) {
        return name.startsWith("com.xiaomi.security.xsof") || name.indexOf('.') < 0 || name.length() <= 5;
    }

    /** 写行为库的出口：void + public + 首参是 Qa.* 或 ContentValues */
    private static boolean isDbWrite(Method method) {
        if (method.getReturnType() != void.class) return false;
        if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) return false;
        Class<?>[] pts = method.getParameterTypes();
        if (pts.length == 0) return false;
        String p0 = pts[0].getName();
        return p0.startsWith("Qa.") || p0.equals("android.content.ContentValues");
    }

    /**
     * 评分出口按签名找：返回 float，三个参数是 XSOF 的评分 bean
     * （ScoreConfigBean / PastFraudData / DurationFraudData）。
     * 12.8.6 是 Pa.b#j，13.6.6 是 ho.b#j，类名换了但签名没换。
     */
    private void installScore(ClassLoader cl) {
        int n = 0;
        for (String name : Hooks.classNames(cl, XsofHooks::obfuscatedLikely, 8000)) {
            Class<?> c;
            Method[] ms;
            try {
                c = Class.forName(name, false, cl);
                ms = c.getDeclaredMethods();
            } catch (Throwable t) {
                continue;
            }
            for (Method method : ms) {
                boolean hit;
                try {
                    hit = isScoreMethod(method);
                } catch (Throwable t) {
                    continue;
                }
                if (!hit) continue;
                try {
                    m.hook(method).intercept(chain -> {
                        Hooks.noteHit(chain, "xsof score");
                        return Float.valueOf(0f);
                    });
                    n++;
                    Logs.i("xsof score " + name + "#" + method.getName());
                } catch (Throwable ignored) {
                }
            }
        }
        Logs.i("xsof score hooked=" + n);
    }

    private static boolean isScoreMethod(Method method) {
        if (method.getReturnType() != float.class) return false;
        if (method.getParameterCount() != 3) return false;
        int beans = 0;
        for (Class<?> p : method.getParameterTypes()) {
            String sn = p.getSimpleName();
            if (sn.endsWith("FraudData") || sn.equals("ScoreConfigBean")) beans++;
        }
        return beans >= 2;
    }

    void mrm(ClassLoader cl) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager", false,
                    ClassLoader.getSystemClassLoader());
            for (Method method : sm.getDeclaredMethods()) {
                if (!method.getName().equals("getService")
                        && !method.getName().equals("waitForService")) continue;
                if (method.getParameterCount() != 1) continue;
                m.hook(method).intercept(chain -> {
                    Object a0 = chain.getArg(0);
                    if (a0 instanceof String && ((String) a0).toLowerCase().contains("mrm")) {
                        return null;
                    }
                    return chain.proceed();
                });
            }
            Logs.i("mrm hooked");
        } catch (Throwable t) {
            Logs.i("mrm hook miss: " + t);
        }
    }
}
