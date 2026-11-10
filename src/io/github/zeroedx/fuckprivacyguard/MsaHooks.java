package io.github.zeroedx.fuckprivacyguard;

import android.content.Context;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.api.XposedModule;

/**
 * MSA（com.miui.systemAdSolution）广告组件的两处收口。
 *
 * 1) 使用画像：判断「应用今天是否打开」「最后使用时间」「首次活跃时间」，
 *    用于推送广告的筛选与召回。这一层不能按清空 UsageStats 的做法处理——
 *    查不到数据会返回 false，AppOpenedFilter 全部放行，推送反而变多。
 *    所以显式给「今天已打开 = true」「最后使用 = -1」。
 *
 * 2) 埋点：OneTrack 的 track / adTrack 系列。
 *
 * 类名与 bean 包在版本间会漂移（2025.07 是 com.miui.d.g，2026.08 是 com.miui.e.g），
 * 因此按「修饰符 + 参数 + 返回类型 + 声明类所在包」组合判定，不用写死的类名。
 */
final class MsaHooks {

    private final XposedModule m;

    MsaHooks(XposedModule m) {
        this.m = m;
    }

    void installUsage(ClassLoader cl) {
        int opened = 0, lastUsed = 0, firstActive = 0;
        for (String name : Hooks.classNames(cl, MsaHooks::usageCandidate, 600)) {
            Class<?> c;
            Method[] methods;
            try {
                c = Class.forName(name, false, cl);
                methods = c.getDeclaredMethods();
            } catch (Throwable t) {
                continue;
            }
            for (Method method : methods) {
                try {
                    if (isAppOpenedToday(name, method)) {
                        m.hook(method).intercept(chain -> {
                            Hooks.noteHit(chain, "msa opened");
                            return Boolean.TRUE;
                        });
                        opened++;
                    } else if (isLastUsedTime(method)) {
                        m.hook(method).intercept(chain -> {
                            Hooks.noteHit(chain, "msa lastUsed");
                            return Long.valueOf(-1L);
                        });
                        lastUsed++;
                    } else if (isFirstActiveTime(name, method)) {
                        m.hook(method).intercept(chain -> {
                            Hooks.noteHit(chain, "msa firstActive");
                            return Long.valueOf(-1L);
                        });
                        firstActive++;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        Logs.i("msa usage opened=" + opened + " lastUsed=" + lastUsed
                + " firstActive=" + firstActive);
    }

    void installTrack(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> c = Class.forName("com.xiaomi.onetrack.OneTrack", false, cl);
            for (Method method : c.getDeclaredMethods()) {
                String name = method.getName();
                if (!name.startsWith("track") && !name.startsWith("adTrack")) continue;
                if (method.getReturnType() != void.class) continue;
                if (Modifier.isAbstract(method.getModifiers())) continue;
                try {
                    m.hook(method).intercept(chain -> {
                        Hooks.noteHit(chain, "msa track");
                        return null;
                    });
                    n++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Logs.i("msa track miss: " + t);
        }
        Logs.i("msa track hooked=" + n);
    }

    /** 只扫可能承载这几个方法的包，避免整进程全量反射 */
    private static boolean usageCandidate(String name) {
        return name.startsWith("com.miui.utils.notification")
                || name.startsWith("com.miui.cache")
                || name.startsWith("com.miui.d.")
                || name.startsWith("com.miui.e.");
    }

    /** static + (Context,String) -> boolean，且声明在 com.miui.utils.notification 下 */
    private static boolean isAppOpenedToday(String cls, Method method) {
        if (!Modifier.isStatic(method.getModifiers())) return false;
        if (method.getReturnType() != boolean.class) return false;
        Class<?>[] p = method.getParameterTypes();
        return p.length == 2 && p[0] == Context.class && p[1] == String.class
                && cls.startsWith("com.miui.utils.notification");
    }

    /** public + (String,long,long) -> long，全包唯一 */
    private static boolean isLastUsedTime(Method method) {
        if (!Modifier.isPublic(method.getModifiers())) return false;
        if (method.getReturnType() != long.class) return false;
        Class<?>[] p = method.getParameterTypes();
        return p.length == 3 && p[0] == String.class
                && p[1] == long.class && p[2] == long.class;
    }

    /** 非 static + (Context,String) -> long，且声明在 com.miui.utils.notification 下 */
    private static boolean isFirstActiveTime(String cls, Method method) {
        if (Modifier.isStatic(method.getModifiers())) return false;
        if (method.getReturnType() != long.class) return false;
        Class<?>[] p = method.getParameterTypes();
        return p.length == 2 && p[0] == Context.class && p[1] == String.class
                && cls.startsWith("com.miui.utils.notification");
    }
}
