package io.github.zeroedx.fuckprivacyguard;

import android.app.Service;
import android.content.Intent;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * guardprovider 的 AI 通话预警检测服务（AiGuardService）。
 *
 * 现有实现只拦上报域名，服务本身仍会启动并做本地采集（录音 / 场景检测），
 * 只是上传被域名拦住。这里在 {@code onStartCommand} 处直接拒绝启动：
 * 返回 {@link Service#START_NOT_STICKY} 并 {@code stopSelf}，与服务正常停止的语义一致。
 *
 * 只 hook 该类，不影响 guardprovider 的其它服务。
 */
final class GuardHooks {

    private static final String SERVICE = "com.miui.guardprovider.aiguard.AiGuardService";

    private final XposedModule m;

    GuardHooks(XposedModule m) {
        this.m = m;
    }

    void install(ClassLoader cl) {
        Method onStart = null;
        try {
            Class<?> c = Class.forName(SERVICE, false, cl);
            int found = 0;
            for (Method method : c.getDeclaredMethods()) {
                if (Hooks.isServiceOnStartCommand(method.getName(),
                        method.getParameterTypes(), method.getReturnType(),
                        method.getModifiers())) {
                    onStart = method;
                    found++;
                }
            }
            if (found != 1) {
                Logs.i("guard aiguard skip cands=" + found);
                return;
            }
        } catch (Throwable t) {
            Logs.i("guard aiguard skip: " + t.getClass().getSimpleName());
            return;
        }
        try {
            m.hook(onStart).intercept(chain -> {
                Hooks.noteHit(chain, "guard aiguard");
                Object self = chain.getThisObject();
                if (self instanceof Service) {
                    Object a2 = chain.getArg(2);
                    if (a2 instanceof Integer) {
                        try {
                            ((Service) self).stopSelf(((Integer) a2).intValue());
                        } catch (Throwable ignored) {
                        }
                    }
                }
                return Integer.valueOf(Service.START_NOT_STICKY);
            });
            Logs.i("guard aiguard hooked");
        } catch (Throwable t) {
            Logs.i("guard aiguard hook miss -> " + t);
        }
    }

}
