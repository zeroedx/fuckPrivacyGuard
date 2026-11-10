package io.github.vivibibi30.hyperpguard;

import android.content.ContentValues;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

/** 行为库写入与静默录屏：按框架 API 拦截，与组件版本无关。 */
final class CaptureHooks {

    private static final Set<String> TABLES = new HashSet<>(Arrays.asList(
            "screenshot", "screen_share", "call_forwarding", "airplane_mode",
            "fraud_phone", "voip_record", "fraud_url", "install_app"));

    private CaptureHooks() {
    }

    static void dbWrites(XposedModule m, ClassLoader cl) {
        hookTableReturn(m, cl, "insert", "long", -1L);
        hookTableReturn(m, cl, "insertOrThrow", "long", -1L);
        hookTableReturn(m, cl, "insertWithOnConflict", "long", -1L);
        hookTableReturn(m, cl, "update", "int", 0);
        hookTableReturn(m, cl, "delete", "int", 0);

        try {
            Method exec = Class.forName("android.database.sqlite.SQLiteDatabase", false, cl)
                    .getDeclaredMethod("execSQL", String.class);
            m.hook(exec).intercept(chain -> {
                Object a0 = chain.getArg(0);
                if (a0 instanceof String && mentionsTable(((String) a0).toLowerCase())) {
                    Hooks.noteHit("db execSQL");
                    return null;
                }
                return chain.proceed();
            });
            Logs.i("hook ok SQLiteDatabase#execSQL");
        } catch (Throwable t) {
            Logs.i("hook miss SQLiteDatabase#execSQL -> " + t);
        }
    }

    private static void hookTableReturn(XposedModule m, ClassLoader cl, String name,
                                        String retType, Object fallback) {
        try {
            Class<?> db = Class.forName("android.database.sqlite.SQLiteDatabase", false, cl);
            for (Method method : db.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterCount() < 1) continue;
                if (method.getParameterTypes()[0] != String.class) continue;
                if (!method.getReturnType().getName().endsWith(retType) && !"void".equals(retType)) {
                    if (!(retType.equals("long") && method.getReturnType() == long.class)
                            && !(retType.equals("int") && method.getReturnType() == int.class)) continue;
                }
                m.hook(method).intercept(chain -> {
                    Object a0 = chain.getArg(0);
                    if (a0 instanceof String && TABLES.contains((String) a0)) {
                        Hooks.noteHit("db write " + a0);
                        return fallback;
                    }
                    return chain.proceed();
                });
            }
            Logs.i("hook ok SQLiteDatabase#" + name);
        } catch (Throwable t) {
            Logs.i("hook miss SQLiteDatabase#" + name + " -> " + t);
        }
    }

    private static boolean mentionsTable(String sql) {
        for (String t : TABLES) {
            if (sql.contains(t)) return true;
        }
        return false;
    }

    static void screenCapture(XposedModule m, ClassLoader cl) {
        try {
            Class<?> mp = Class.forName("android.media.projection.MediaProjection", false, cl);
            int n = 0;
            for (Method method : mp.getDeclaredMethods()) {
                if (!method.getName().equals("createVirtualDisplay")) continue;
                m.hook(method).intercept(chain -> {
                    Hooks.noteHit("screenguard");
                    return null;
                });
                n++;
            }
            Logs.i("hook ok MediaProjection#createVirtualDisplay x" + n);
        } catch (Throwable t) {
            Logs.i("hook miss MediaProjection#createVirtualDisplay -> " + t);
        }
    }
}
