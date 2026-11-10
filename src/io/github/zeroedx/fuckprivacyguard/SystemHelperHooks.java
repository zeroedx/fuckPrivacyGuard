package io.github.zeroedx.fuckprivacyguard;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedModule;

/**
 * SystemHelper 上报链路的非出口环节。
 *
 * 现有实现只拦「数据出口」（{@code c.d.a.b.f} 加密 POST + 域名），
 * 采集与许可判断环节没有覆盖。本类按结构谓词补齐三处：
 *
 * 1) 许可判断：public static {@code (Context) -> boolean}，true 表示用户已同意
 *    运营商服务改进。直接返回 false，从源头关掉上报许可。
 * 2) 应用使用统计线程：{@code Thread} 子类的无参 {@code run()}，且类引用
 *    {@code android.app.usage.*}，内部枚举已安装应用与使用记录。
 * 3) 采集类广播接收器：7 个 {@code BroadcastReceiver.onReceive(Context,Intent)}。
 *
 * 判定只依赖「修饰符 + 参数类型 + 返回类型 + 声明类」，不写死混淆类名；
 * 判定逻辑集中在 {@link Hooks#isPolicyGate} / {@link Hooks#isUsageRun} /
 * {@link Hooks#isReceiverOnReceive}，可离线单测。
 *
 * 三类目标都要求**候选唯一**：命中 0 个或多个一律跳过并记候选数量
 * （{@code sh gate skip} / {@code sh usage skip} / {@code sh receiver skip}），绝不盲目 hook。
 * 诊断只记类名与候选数量，不读取、不记录任何隐私数据或 Intent 内容。
 */
final class SystemHelperHooks {

    private final XposedModule m;

    SystemHelperHooks(XposedModule m) {
        this.m = m;
    }

    void install(ClassLoader cl) {
        if (Config.on(Config.K_SH_POLICY, true)) gate(cl);
        if (Config.on(Config.K_SH_USAGE, true)) usage(cl);
        if (Config.on(Config.K_SH_RECEIVER, true)) receivers(cl);
    }

    /**
     * 许可判断：唯一允许的形态是 public static (Context)->boolean。
     * 已在目标 APK 上核对：扫描范围内只有 {@code g1.l.a} 一个命中，其常量池含
     * {@code operator_service_improve_plan} / {@code device_provisioned} / {@code RegisterUtils}，
     * 与上游许可判断类一致。
     *
     * 判据只依赖签名，不写死混淆类名。不同 ROM 版本可能引入同签名的其它判断方法，
     * 无法从签名区分，因此这里**不猜**：整个扫描范围内候选必须唯一，
     * 命中 0 个或多个一律跳过并记候选数量，宁可漏拦也不误伤无关功能。
     */
    private void gate(ClassLoader cl) {
        List<Method> cands = new ArrayList<>();
        for (String name : Hooks.classNames(cl, SystemHelperHooks::shCandidate, 400)) {
            Method[] ms;
            try {
                ms = Class.forName(name, false, cl).getDeclaredMethods();
            } catch (Throwable t) {
                continue;
            }
            for (Method method : ms) {
                try {
                    if (Hooks.isPolicyGate(method.getParameterTypes(),
                            method.getReturnType(), method.getModifiers())) {
                        cands.add(method);
                    }
                } catch (Throwable t) {
                    // getParameterTypes 可能因缺失类型整类抛错，逐方法兜住
                }
            }
        }
        if (cands.size() != 1) {
            Logs.i("sh gate skip cands=" + cands.size());
            return;
        }
        Method method = cands.get(0);
        String owner = method.getDeclaringClass().getName();
        try {
            m.hook(method).intercept(chain -> {
                Hooks.noteHit(chain, "sh gate");
                return Boolean.FALSE;
            });
            Logs.i("sh gate hooked " + owner + "#" + method.getName());
        } catch (Throwable t) {
            Logs.i("sh gate hook miss " + owner + "#" + method.getName() + " -> " + t);
        }
    }

    /**
     * 应用使用统计线程：{@code Thread} 子类的无参 {@code run()}，且类成员签名里
     * 引用 {@code android.app.usage.*}（见 {@link Hooks#isUsageRun}）。
     *
     * 目标在目标 APK 里是**单个**混淆类。结构判据不足以唯一定位时（命中 0 个或多个），
     * 按「宁可漏拦也不误伤」整体跳过并记候选数量，不盲 hook。
     */
    private void usage(ClassLoader cl) {
        List<Method> cands = new ArrayList<>();
        for (String name : Hooks.classNames(cl, SystemHelperHooks::shCandidate, 400)) {
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
                    hit = Hooks.isUsageRun(c, method.getName(),
                            method.getParameterTypes(), method.getReturnType(),
                            method.getModifiers());
                } catch (Throwable t) {
                    continue;
                }
                if (hit) cands.add(method);
            }
        }
        if (cands.size() != 1) {
            Logs.i("sh usage skip cands=" + cands.size());
            return;
        }
        Method method = cands.get(0);
        String owner = method.getDeclaringClass().getName();
        try {
            m.hook(method).intercept(chain -> {
                Hooks.noteHit(chain, "sh usage");
                return null;
            });
            Logs.i("sh usage hooked " + owner + "#run");
        } catch (Throwable t) {
            Logs.i("sh usage hook miss " + owner + "#run -> " + t);
        }
    }

    /**
     * 采集类广播接收器：类名在目标 APK 里稳定、未被混淆，按 FQN 取类；
     * 只 hook 每个接收器自身的 onReceive(Context,Intent)。
     *
     * 不触碰系统广播框架（不 hook BroadcastReceiver 基类 / ActivityManager）；
     * 每个类要求入口唯一，否则跳过该类并记候选数量。
     * 只记类名，不读取、不记录 Intent 内容。
     */
    private static final String[] RECEIVERS = {
            "AppInfoRegisterReceiver", "ScheduleRegisterReceiver", "GetSignalInfoReceiver",
            "SignalInfoRegisterReceiver", "OnBootReceiver", "SIMStateReceiver",
            "ConnectStateReceiver",
    };

    private void receivers(ClassLoader cl) {
        int hooked = 0;
        for (String simple : RECEIVERS) {
            String fqn = "com.mobiletools.systemhelper.control." + simple;
            try {
                Class<?> c = Class.forName(fqn, false, cl);
                List<Method> cands = new ArrayList<>();
                for (Method method : c.getDeclaredMethods()) {
                    boolean hit;
                    try {
                        hit = Hooks.isReceiverOnReceive(method.getName(),
                                method.getParameterTypes(), method.getReturnType(),
                                method.getModifiers());
                    } catch (Throwable t) {
                        continue;
                    }
                    if (hit) cands.add(method);
                }
                if (cands.size() != 1) {
                    Logs.i("sh receiver skip " + fqn + " cands=" + cands.size());
                    continue;
                }
                try {
                    m.hook(cands.get(0)).intercept(chain -> {
                        Hooks.noteHit(chain, "sh receiver");
                        return null;
                    });
                    hooked++;
                    Logs.i("sh receiver " + simple + " hooked=1");
                } catch (Throwable t) {
                    Logs.i("sh receiver hook miss " + fqn + " -> " + t);
                }
            } catch (Throwable t) {
                Logs.i("sh receiver skip " + fqn + " -> " + t.getClass().getSimpleName());
            }
        }
        Logs.i("sh receivers hooked=" + hooked);
    }

    /** 只扫系统组件自身与其混淆包，避免整进程反射 */
    private static boolean shCandidate(String name) {
        return name.startsWith("com.mobiletools.systemhelper")
                || name.startsWith("c.d.a.")
                || name.startsWith("e1.")
                || name.startsWith("g1.");
    }
}
