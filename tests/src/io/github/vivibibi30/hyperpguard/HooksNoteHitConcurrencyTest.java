package io.github.vivibibi30.hyperpguard;

import java.lang.reflect.Executable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

/**
 * Hooks.noteHit 并发去重的离线单元测试（纯 JVM，无需设备）。
 *
 * 覆盖首次命中去重的线程安全：多个线程同时用同一方法签名调用 noteHit 时，
 * 去重集合必须只放行一条日志，不能因为竞态而重复记录。
 * 去重集合由 ConcurrentHashMap 支撑（Hooks.HITS），本测试验证该性质在并发下成立。
 *
 * 入口：tests/run.sh。任何断言失败时退出码非零。
 */
public final class HooksNoteHitConcurrencyTest {

    private static int failures = 0;

    private static final int THREADS = 64;
    private static final int ITERS = 2000;

    public static void main(String[] args) throws Exception {
        // 去重集合是静态的、跨用例共享，因此每个用例用不同的方法签名，避免相互污染。
        // 同一签名、同一标签：64 线程各打 ITERS 次，只应记 1 条
        runConcurrent("unlabeled", method("s1", int.class), null, 1);

        // 同一签名、带标签：只应记 1 条，且与无标签那条互不吞掉（并发下各自独立成键）
        Log.LINES.clear();
        Executable ex = method("s2", int.class);
        fire(ex, null);
        fire(ex, "applist");
        expect(2, "带标签与不带标签在并发下各自成键");

        System.out.println(failures == 0
                ? "ALL PASS (concurrent dedup: 2 scenarios)"
                : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** 多线程并发调用 noteHit(chain,label)，断言只产生 expected 条日志。 */
    private static void runConcurrent(String what, Executable ex, String label, int expected)
            throws InterruptedException {
        Log.LINES.clear();
        fire(ex, label);
        int lines = lines();
        if (lines != expected) {
            failures++;
            System.out.println("FAIL: 并发去重(" + what + ") 期望 " + expected + " 条，实际 " + lines);
            dump();
        } else {
            System.out.println("ok: 并发去重(" + what + ") = " + lines + " 条 / "
                    + (THREADS * ITERS) + " 次调用");
        }
    }

    /** 启动 THREADS 个线程同时压同一签名，等全部结束后返回。 */
    private static void fire(Executable ex, String label) throws InterruptedException {
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(THREADS);
        for (int t = 0; t < THREADS; t++) {
            new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < ITERS; i++) {
                    Hooks.noteHit(new Chain(ex), label);
                }
                done.countDown();
            }).start();
        }
        start.countDown();
        done.await();
    }

    private static int lines() {
        synchronized (Log.LINES) {
            return Log.LINES.size();
        }
    }

    private static void expect(int n, String what) {
        int got = lines();
        if (got != n) {
            failures++;
            System.out.println("FAIL: " + what + " 期望 " + n + " 条，实际 " + got);
            dump();
        } else {
            System.out.println("ok: " + what + " = " + got);
        }
    }

    private static void dump() {
        synchronized (Log.LINES) {
            for (String l : Log.LINES) {
                System.out.println("  | " + l);
            }
        }
    }

    private static Executable method(String name, Class<?>... p) {
        try {
            return ConcurrentTarget.class.getDeclaredMethod(name, p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 只用于提供方法签名，不会真的被调用。每个用例一个独立签名。 */
    static final class ConcurrentTarget {
        int s1(int a) {
            return a;
        }

        int s2(int a) {
            return a;
        }
    }

    /** 最小 Chain 实现：只实现测试用到的 getExecutable。 */
    private static final class Chain implements XposedInterface.Chain {
        private final Executable ex;

        Chain(Executable ex) {
            this.ex = ex;
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
            return Collections.emptyList();
        }

        @Override
        public Object getArg(int index) {
            return null;
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

    private HooksNoteHitConcurrencyTest() {
    }
}
