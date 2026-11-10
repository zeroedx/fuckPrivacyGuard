package io.github.vivibibi30.hyperpguard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

/**
 * Hooks.noteHit 的离线单元测试（纯 JVM，无需设备）。
 *
 * 验证：
 *   1. 首次命中去重：同一签名重复调用只记一条；
 *   2. 去重键含类名 / 方法名 / 参数签名：同一类里不同重载各自成键
 *      （跨类同名方法的成键由去重键含类名保证，本测试只用了一个类，未单独覆盖）；
 *   3. 语义标签参与去重键，同一方法带标签与不带标签互不影响；
 *   4. 只记方法身份，不记任何实参内容；
 *   5. 拿不到 Executable 时不记录、不抛异常。
 *
 * 入口：tests/run.sh。任何断言失败时退出码非零。
 */
public final class HooksNoteHitTest {

    private static int failures = 0;

    public static void main(String[] args) {
        android.util.Log.LINES.clear();

        Method overInt = method("over", int.class);
        Method overLong = method("over", long.class);
        Method overString = method("over", String.class);
        Method multi = method("multi", int.class, long.class, String.class);
        Method arr = method("arr", byte[].class, byte[].class);
        Constructor<?> ctorInt = ctor(int.class);

        String idOverInt = Target.class.getName() + "#over(int)";

        // 1. 首次命中去重：连打 100 次只记 1 条
        for (int i = 0; i < 100; i++) {
            Hooks.noteHit(new FakeChain(overInt, "SECRET_IMEI_861234567890123"));
        }
        expect(1, "同签名重复调用只记一条");
        expectLineContains(idOverInt + " hit", "格式为 类#方法(参数) hit");
        expectNoSensitiveValue("SECRET_IMEI_861234567890123");

        // 2. 同一类里不同重载各自成键（含基本类型区分）
        Hooks.noteHit(new FakeChain(overLong));
        Hooks.noteHit(new FakeChain(overString));
        Hooks.noteHit(new FakeChain(multi));
        Hooks.noteHit(new FakeChain(arr));
        Hooks.noteHit(new FakeChain(ctorInt));
        expect(6, "同一类里不同重载各自成键");

        // 3. 标签参与去重键：同一方法带标签记一条，且与无标签那条并存
        Hooks.noteHit(new FakeChain(overInt), "applist");
        Hooks.noteHit(new FakeChain(overInt), "applist");
        expect(7, "带标签的命中独立成键且只记一次");
        expectLineContains("applist " + idOverInt + " hit", "标签前缀保留");

        // 4. 拿不到 Executable：不记录、不抛异常
        Hooks.noteHit(new FakeChain(null));
        Hooks.noteHit(new FakeChain(null), "x");
        expect(7, "Executable 为 null 时静默跳过");

        System.out.println(failures == 0 ? "ALL PASS (7 unique hit lines)" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---------------- 断言 ----------------

    private static void expect(int n, String what) {
        int got;
        synchronized (android.util.Log.LINES) {
            got = android.util.Log.LINES.size();
        }
        if (got != n) {
            failures++;
            System.out.println("FAIL: " + what + " 期望 " + n + " 条，实际 " + got);
            dump();
        } else {
            System.out.println("ok: " + what + " = " + got);
        }
    }

    private static void expectLineContains(String needle, String what) {
        boolean found = false;
        synchronized (android.util.Log.LINES) {
            for (String l : android.util.Log.LINES) {
                if (l.contains(needle)) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            failures++;
            System.out.println("FAIL: " + what + " 未找到包含 \"" + needle + "\" 的行");
            dump();
        } else {
            System.out.println("ok: " + what);
        }
    }

    private static void expectNoSensitiveValue(String secret) {
        boolean leaked = false;
        synchronized (android.util.Log.LINES) {
            for (String l : android.util.Log.LINES) {
                if (l.contains(secret)) {
                    leaked = true;
                    break;
                }
            }
        }
        if (leaked) {
            failures++;
            System.out.println("FAIL: 日志泄露了实参内容");
            dump();
        } else {
            System.out.println("ok: 未记录实参内容");
        }
    }

    private static void dump() {
        synchronized (android.util.Log.LINES) {
            for (String l : android.util.Log.LINES) {
                System.out.println("  | " + l);
            }
        }
    }

    // ---------------- 夹具 ----------------

    private static Method method(String name, Class<?>... p) {
        try {
            return Target.class.getDeclaredMethod(name, p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Constructor<?> ctor(Class<?>... p) {
        try {
            return Target.class.getDeclaredConstructor(p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 只用于提供方法签名，不会真的被调用。 */
    static final class Target {
        Target() {
        }

        Target(int a) {
        }

        Target(String a) {
        }

        int over(int a) {
            return a;
        }

        int over(long a) {
            return (int) a;
        }

        String over(String a) {
            return a;
        }

        void multi(int a, long b, String c) {
        }

        void arr(byte[] a, byte[] b) {
        }
    }

    /** 最小 Chain 实现：只实现测试用到的 getExecutable / getArgs。 */
    private static final class FakeChain implements XposedInterface.Chain {
        private final Executable ex;
        private final List<Object> args;

        FakeChain(Executable ex) {
            this(ex, Collections.emptyList());
        }

        FakeChain(Executable ex, Object arg) {
            this(ex, Collections.singletonList(arg));
        }

        private FakeChain(Executable ex, List<Object> args) {
            this.ex = ex;
            this.args = args;
        }

        @Override
        public Executable getExecutable() {
            return ex;
        }

        @Override
        public Object getThisObject() {
            return null;
        }

        @Override
        public List<Object> getArgs() {
            return args;
        }

        @Override
        public Object getArg(int index) {
            return args.get(index);
        }

        @Override
        public Object proceed() {
            return null;
        }

        @Override
        public Object proceed(Object[] a) {
            return null;
        }

        @Override
        public Object proceedWith(Object o) {
            return null;
        }

        @Override
        public Object proceedWith(Object o, Object[] a) {
            return null;
        }
    }

    private HooksNoteHitTest() {
    }
}
