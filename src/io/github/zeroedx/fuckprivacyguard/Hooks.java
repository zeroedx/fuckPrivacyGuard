package io.github.zeroedx.fuckprivacyguard;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** hook 安装的公共工具。 */
final class Hooks {

    interface Interceptor {
        Object intercept(XposedInterface.Chain chain) throws Throwable;
    }

    interface MethodFilter {
        boolean test(Method method);
    }

    private Hooks() {
    }

    /** 自定义方法规则首次命中只记一条日志 */
    private static final java.util.Set<String> CUSTOM_HITS =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    /**
     * 首次命中集合：把「hook 装上」与「真的被调用」分开。
     * 去重键 = 语义标签 + 声明类 + 方法名 + 参数类型，所以同一方法的不同重载各记一条，
     * 同名不同类也各记一条；每个键只记首次，避免高频调用刷屏。
     */
    private static final java.util.Set<String> HITS =
            java.util.Collections.newSetFromMap(
                    new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    /** 首次命中记一条日志；只记方法身份，不记任何实参内容 */
    static void noteHit(XposedInterface.Chain chain) {
        noteHit(chain, null);
    }

    /**
     * 同上，附带一个语义标签（如 applist / db write）。标签只用于人读；
     * 去重键包含标签本身，同一方法被两种用途 hook 时不会互相吞掉。
     */
    static void noteHit(XposedInterface.Chain chain, String label) {
        java.lang.reflect.Executable ex = executable(chain);
        if (ex == null) return;
        String id = identity(ex);
        String key = label == null ? id : label + ' ' + id;
        if (!HITS.add(key)) return;
        Logs.i(key + " hit");
    }

    /** 取被 hook 的方法/构造器；拿不到就放弃本次记录，不影响业务 */
    private static java.lang.reflect.Executable executable(XposedInterface.Chain chain) {
        try {
            return chain.getExecutable();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 类名#方法名(参数类型)；不含实参值，不含返回值 */
    private static String identity(java.lang.reflect.Executable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getDeclaringClass().getName()).append('#').append(ex.getName()).append('(');
        Class<?>[] ps = ex.getParameterTypes();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ps[i].getName());
        }
        return sb.append(')').toString();
    }

    /** 把方法整体替换为固定返回值。返回是否挂上。 */
    static boolean ret(XposedModule m, ClassLoader cl, String cls, String name,
                       Class<?>[] types, Object value) {
        return ret(m, cl, cls, name, types, value, null);
    }

    /**
     * 同上，并在首次命中时记一条日志（键含类名、方法名与参数签名）。
     * 「hook ok」表示安装成功，「hit」表示真的被调用，两者分开记，命中只记一次。
     */
    static boolean ret(XposedModule m, ClassLoader cl, String cls, String name,
                       Class<?>[] types, Object value, String hitTag) {
        try {
            Class<?> c = Class.forName(cls, false, cl);
            Method method = c.getDeclaredMethod(name, types);
            m.hook(method).intercept(chain -> {
                if (hitTag != null) noteHit(chain, hitTag);
                return value;
            });
            Logs.i("hook ok " + cls + "#" + name);
            return true;
        } catch (Throwable t) {
            Logs.i("hook miss " + cls + "#" + name + " -> " + t);
            return false;
        }
    }

    /**
     * 把某个类里所有同名方法（不区分重载）都替换为固定返回值。
     * 用于类名稳定、但方法签名随版本增加 slot 参数的情况。
     */
    static int retAllByName(XposedModule m, ClassLoader cl, String cls,
                            String[] names, Object value) {
        int n = 0;
        try {
            Class<?> c = Class.forName(cls, false, cl);
            for (Method method : c.getDeclaredMethods()) {
                for (String name : names) {
                    if (!method.getName().equals(name)) continue;
                    try {
                        m.hook(method).intercept(chain -> value);
                        n++;
                    } catch (Throwable ignored) {
                    }
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    static void allByName(XposedModule m, Class<?> c, String name, Interceptor interceptor) {
        for (Method method : c.getDeclaredMethods()) {
            if (!method.getName().equals(name)) continue;
            m.hook(method).intercept(interceptor::intercept);
        }
    }

    /**
     * 遍历进程内各 dex 的类名，按 filter 过滤，最多返回 limit 个。
     * 类名随版本变化时，靠这个替代写死的混淆类名。
     */
    static java.util.List<String> classNames(ClassLoader cl,
                                             java.util.function.Predicate<String> filter,
                                             int limit) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            Class<?> bdcl = Class.forName("dalvik.system.BaseDexClassLoader", false, cl);
            java.lang.reflect.Field fPathList = bdcl.getDeclaredField("pathList");
            fPathList.setAccessible(true);
            Object pathList = fPathList.get(cl);
            if (pathList == null) return out;
            java.lang.reflect.Field fElems = pathList.getClass().getDeclaredField("dexElements");
            fElems.setAccessible(true);
            Object[] elems = (Object[]) fElems.get(pathList);
            for (Object el : elems) {
                if (el == null) continue;
                try {
                    java.lang.reflect.Field fDex = el.getClass().getDeclaredField("dexFile");
                    fDex.setAccessible(true);
                    Object dex = fDex.get(el);
                    if (!(dex instanceof dalvik.system.DexFile)) continue;
                    java.util.Enumeration<String> en = ((dalvik.system.DexFile) dex).entries();
                    while (en.hasMoreElements() && out.size() < limit) {
                        String n = en.nextElement();
                        if (filter == null || filter.test(n)) out.add(n);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Logs.i("class scan fail: " + t);
        }
        return out;
    }

    /** 在候选类里按谓词挑出方法并 hook，返回挂上的数量。 */
    static int hookBySignature(XposedModule m, java.util.List<String> classNames,
                               ClassLoader cl, MethodFilter filter, Interceptor interceptor) {
        int n = 0;
        int broken = 0;
        for (String name : classNames) {
            Method[] ms;
            try {
                // 签名解析在 getDeclaredMethods 里发生：类里含运行时缺失的类型
                // （如 AutoValue 生成的 javax.lang.model.element.*）会整类抛错
                ms = Class.forName(name, false, cl).getDeclaredMethods();
            } catch (Throwable t) {
                broken++;
                continue;
            }
            for (Method method : ms) {
                boolean hit;
                try {
                    hit = filter.test(method);
                } catch (Throwable t) {
                    continue;
                }
                if (!hit) continue;
                try {
                    m.hook(method).intercept(interceptor::intercept);
                    n++;
                } catch (Throwable ignored) {
                }
            }
        }
        if (broken > 0) Logs.i("hookBySignature skipped classes=" + broken);
        return n;
    }

    /**
     * 自定义方法规则：`类名#方法名`，方法名 `*` 表示整类。
     * 命中后直接返回该返回类型的默认值（void 与引用类型返回 null）。
     */
    static void customMethods(XposedModule m, ClassLoader cl, java.util.List<String> rules) {
        for (String rule : rules) {
            int i = rule.indexOf('#');
            if (i <= 0 || i >= rule.length() - 1) continue;
            String cls = rule.substring(0, i).trim();
            String name = rule.substring(i + 1).trim();
            int n = 0;
            try {
                Class<?> c = Class.forName(cls, false, cl);
                for (Method method : c.getDeclaredMethods()) {
                    if (!"*".equals(name) && !method.getName().equals(name)) continue;
                    int mod = method.getModifiers();
                    if (java.lang.reflect.Modifier.isAbstract(mod)
                            || java.lang.reflect.Modifier.isNative(mod)) continue;
                    final Object value = defaultValue(method.getReturnType());
                    final String key = rule;
                    try {
                        m.hook(method).intercept(chain -> {
                            if (CUSTOM_HITS.add(key)) Logs.i("custom hit " + key);
                            return value;
                        });
                        n++;
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                Logs.i("custom method miss " + rule + " -> " + t);
                continue;
            }
            Logs.i("custom method " + rule + " hooked=" + n);
        }
    }

    /**
     * SmsManager 发送入口的签名判定。
     *
     * 按「方法名 + 参数结构 + void 返回」判定，而不是写死某一个重载，
     * 这样 Android 10–17 上存在的每个重载都能命中，也不会再因为签名写错而整段失效。
     * 覆盖的真实签名：
     *   sendTextMessage / sendTextMessageWithoutPersisting
     *       (String, String, String, PendingIntent, PendingIntent[, long])
     *   sendMultipartTextMessage
     *       (String, String, ArrayList, ArrayList, ArrayList)
     *       (String, String, List, List, List, long)
     *       (String, String, List, List, List, String, String)
     *   sendDataMessage
     *       (String, String, short, byte[], PendingIntent, PendingIntent)
     *
     * 类型用类名字符串比较而不是类字面量：既跨类加载器稳定，也让本方法能在
     * 离线单测里用替身类验证（见 tests/src 的 HooksSmsSignatureTest）。
     */
    static boolean isSmsSend(String name, Class<?>[] p, Class<?> ret) {
        if (ret != void.class) return false;
        if (name == null || p == null || p.length < 5) return false;
        if (!isNamed(p[0], "java.lang.String") || !isNamed(p[1], "java.lang.String")) return false;
        switch (name) {
            case "sendTextMessage":
            case "sendTextMessageWithoutPersisting":
                if (p.length != 5 && p.length != 6) return false;
                if (!isNamed(p[2], "java.lang.String")) return false;
                if (!isPendingIntent(p[3]) || !isPendingIntent(p[4])) return false;
                return p.length == 5 || p[5] == long.class;
            case "sendMultipartTextMessage":
                if (p.length != 5 && p.length != 6 && p.length != 7) return false;
                if (!isListType(p[2]) || !isListType(p[3]) || !isListType(p[4])) return false;
                if (p.length == 5) return p[2] == java.util.ArrayList.class;
                if (p.length == 6) return p[5] == long.class;
                return isNamed(p[5], "java.lang.String") && isNamed(p[6], "java.lang.String");
            case "sendDataMessage":
                if (p.length != 6) return false;
                if (p[2] != short.class || p[3] != byte[].class) return false;
                return isPendingIntent(p[4]) && isPendingIntent(p[5]);
            default:
                return false;
        }
    }

    /**
     * SystemHelper 许可判断的形态：public static + 单参 Context + 返回 boolean。
     * 只判签名与修饰符，不依赖混淆类名。类型用类名字符串比较，便于离线单测。
     */
    static boolean isPolicyGate(Class<?>[] p, Class<?> ret, int mods) {
        if (ret != boolean.class) return false;
        if (!java.lang.reflect.Modifier.isStatic(mods)) return false;
        if (!java.lang.reflect.Modifier.isPublic(mods)) return false;
        return p != null && p.length == 1 && isNamed(p[0], "android.content.Context");
    }

    /**
     * 应用使用统计线程 run() 的形态：Thread 子类的无参 void run()，
     * 且该类的成员签名里出现 {@code android.app.usage.*} 类型。
     *
     * 目标 APK 里 Thread 子类不止一个（下载、定时器等），只按「Thread 子类 + run()」
     * 判定会误伤。使用统计线程必然引用 {@code UsageStatsManager} / {@code UsageStats}，
     * 因此追加「引用使用统计类型」这一结构性条件，不写死混淆类名。
     */
    static boolean isUsageRun(Class<?> cls, String name, Class<?>[] p, Class<?> ret, int mods) {
        if (!"run".equals(name)) return false;
        if (ret != void.class) return false;
        if (p == null || p.length != 0) return false;
        if (java.lang.reflect.Modifier.isAbstract(mods)
                || java.lang.reflect.Modifier.isNative(mods)) return false;
        return isThreadSubclass(cls) && referencesUsageTypes(cls);
    }

    /** 类的成员签名（方法参数/返回、字段）里是否出现 android.app.usage.* 类型 */
    static boolean referencesUsageTypes(Class<?> c) {
        try {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (isUsageType(m.getReturnType())) return true;
                for (Class<?> t : m.getParameterTypes()) {
                    if (isUsageType(t)) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (isUsageType(f.getType())) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isUsageType(Class<?> t) {
        return t != null && t.getName().startsWith("android.app.usage.");
    }

    private static boolean isThreadSubclass(Class<?> c) {
        for (Class<?> s = c; s != null; s = s.getSuperclass()) {
            if ("java.lang.Thread".equals(s.getName())) return true;
        }
        return false;
    }

    /**
     * 采集类广播接收器 onReceive 的形态：onReceive(Context,Intent) -> void。
     * 用于按 FQN 取到的 Receiver 类里挑选唯一入口。
     */
    static boolean isReceiverOnReceive(String name, Class<?>[] p, Class<?> ret, int mods) {
        if (!"onReceive".equals(name)) return false;
        if (ret != void.class) return false;
        if (java.lang.reflect.Modifier.isAbstract(mods)
                || java.lang.reflect.Modifier.isNative(mods)) return false;
        return p != null && p.length == 2
                && isNamed(p[0], "android.content.Context")
                && isNamed(p[1], "android.content.Intent");
    }

    /**
     * Service.onStartCommand 的形态：public onStartCommand(Intent,int,int) -> int。
     * 用于按 FQN 取到的服务类里挑选唯一入口，只依赖签名与修饰符，不依赖混淆名。
     *
     * 必须是 public 的实例方法（非 static/abstract/native）：Service 生命周期入口按契约
     * 就是 public 实例方法，排除这些修饰符可避免误取同名 helper 或未实现的桥接方法。
     */
    static boolean isServiceOnStartCommand(String name, Class<?>[] p, Class<?> ret, int mods) {
        if (!"onStartCommand".equals(name)) return false;
        if (ret != int.class) return false;
        if (!java.lang.reflect.Modifier.isPublic(mods)) return false;
        if (java.lang.reflect.Modifier.isStatic(mods)
                || java.lang.reflect.Modifier.isAbstract(mods)
                || java.lang.reflect.Modifier.isNative(mods)) return false;
        if (p == null || p.length != 3) return false;
        return isNamed(p[0], "android.content.Intent")
                && p[1] == int.class && p[2] == int.class;
    }

    /**
     * Android ID 的替代值：固定 16 位小写十六进制。
     *
     * Settings.Secure.getString(cr, "android_id") 的调用方常直接当 String 用，
     * 返回 null 会 NPE。AOSP 在无权限/无值时返回 null，但拼接型调用方不判空。
     * 这里返回一个格式合法、不含真实标识的稳定值：长度与字符集都符合
     * Android ID 的既有形态（16 位 hex），且对同一安装稳定，便于需要稳定 ID 的场景。
     */
    static String androidIdStub() {
        return "0000000000000000";
    }

    /** getCurrentLocation 的一次性回调接收器：拿到形参里的 Consumer 后投递结果。 */
    interface LocationSink {
        void deliver(Object consumer);
    }

    /**
     * LocationManager.getCurrentLocation 的形参判定与回调投递。
     *
     * 该方法是 void，返回 null 只会让原逻辑不执行——调用方既拿不到结果也不报错，
     * 会一直等待或超时。正确做法是替它完成一次回调：在调用方传入的 Executor 上
     * 用 Consumer 接收一个 null 位置（表示「无可用位置」），且**恰好回调一次**。
     *
     * 形参里必须同时存在 Executor 与 Consumer 类型，否则不做任何事（宁可放行也不伪造）。
     * 类型用类名字符串比较，参数用 Object 处理，便于离线单测。
     * 返回是否投递了回调。
     */
    static boolean deliverEmptyCurrentLocation(Class<?>[] pts, java.util.List<Object> args,
                                               LocationSink sink) {
        if (pts == null || args == null || sink == null) return false;
        Object executor = null;
        Object consumer = null;
        Object cancelSignal = null;
        for (Class<?> p : pts) {
            if (executor == null && "java.util.concurrent.Executor".equals(p.getName())) {
                executor = argOfType(args, p);
            } else if (consumer == null && "java.util.function.Consumer".equals(p.getName())) {
                consumer = argOfType(args, p);
            } else if (cancelSignal == null && "android.os.CancellationSignal".equals(p.getName())) {
                cancelSignal = argOfType(args, p);
            }
        }
        // 调用方已取消：不再回调，与平台语义一致（取消后不应再收到结果）。
        if (cancelSignal != null && isCanceled(cancelSignal)) return false;
        if (executor == null || consumer == null) return false;
        final Object cb = consumer;
        try {
            ((java.util.concurrent.Executor) executor).execute(() -> sink.deliver(cb));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object argOfType(java.util.List<Object> args, Class<?> type) {
        for (Object a : args) {
            if (type.isInstance(a)) return a;
        }
        return null;
    }

    /**
     * 反射读取 CancellationSignal.isCanceled()：用类名字符串判定，不引用该类，
     * 便于离线单测；读不到时按「未取消」处理（宁可回调一次，也不静默丢弃结果）。
     */
    private static boolean isCanceled(Object signal) {
        try {
            java.lang.reflect.Method m = signal.getClass().getMethod("isCanceled");
            Object v = m.invoke(signal);
            return Boolean.TRUE.equals(v);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isNamed(Class<?> c, String name) {
        return c != null && name.equals(c.getName());
    }

    private static boolean isPendingIntent(Class<?> c) {
        return isNamed(c, "android.app.PendingIntent");
    }

    private static boolean isListType(Class<?> c) {
        return c != null && java.util.List.class.isAssignableFrom(c);
    }

    private static Object defaultValue(Class<?> t) {
        if (t == null || !t.isPrimitive()) return null;
        if (t == boolean.class) return Boolean.FALSE;
        if (t == char.class) return Character.valueOf('\u0000');
        if (t == byte.class) return Byte.valueOf((byte) 0);
        if (t == short.class) return Short.valueOf((short) 0);
        if (t == int.class) return Integer.valueOf(0);
        if (t == long.class) return Long.valueOf(0L);
        if (t == float.class) return Float.valueOf(0f);
        if (t == double.class) return Double.valueOf(0d);
        return null;
    }
}
