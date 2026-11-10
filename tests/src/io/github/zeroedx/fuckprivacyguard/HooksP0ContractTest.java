package io.github.zeroedx.fuckprivacyguard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import android.content.Context;
import android.content.Intent;
import android.os.CancellationSignal;

/**
 * 2.1.0 P0 的离线回归测试（纯 JVM，无需设备）。
 *
 * 覆盖四项 P0 的「结构判定 + 返回值契约」：
 *   1. Android ID 替代值：非 null、长度/字符集合法、稳定；
 *   2. SystemHelper 许可判断 / 统计线程 / 接收器 的签名谓词；
 *   3. guardprovider AiGuardService.onStartCommand 的签名谓词；
 *   4. getCurrentLocation 的契约：拿到 Executor+Consumer 时**恰好回调一次**且传 null；
 *      缺任一类型时不投递（宁可放行，不伪造回调）。
 *
 * 入口：tests/run.sh。任何断言失败时退出码非零。
 */
public final class HooksP0ContractTest {

    private static int failures = 0;

    public static void main(String[] args) {
        androidId();
        systemHelperPredicates();
        guardServicePredicate();
        currentLocationContract();

        if (failures > 0) {
            System.out.println("FAILED: " + failures + " assertion(s)");
            System.exit(1);
        }
        System.out.println("OK: HooksP0ContractTest passed");
    }

    // 1. Android ID 替代值
    private static void androidId() {
        String v = Hooks.androidIdStub();
        expectTrue(v != null, "androidId 非 null");
        expectTrue(v.length() == 16, "androidId 长度 16（实际 " + (v == null ? -1 : v.length()) + "）");
        boolean hex = v != null;
        for (int i = 0; hex && i < v.length(); i++) {
            char c = v.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) hex = false;
        }
        expectTrue(hex, "androidId 仅含小写十六进制字符");
        expectTrue(v.equals(Hooks.androidIdStub()), "androidId 稳定（两次调用一致）");
    }

    // 2. SystemHelper 谓词
    private static void systemHelperPredicates() {
        int pubStatic = java.lang.reflect.Modifier.PUBLIC | java.lang.reflect.Modifier.STATIC;

        expectTrue(Hooks.isPolicyGate(new Class<?>[]{Context.class}, boolean.class, pubStatic),
                "gate: public static (Context)->boolean 命中");
        expectTrue(!Hooks.isPolicyGate(new Class<?>[]{Context.class}, boolean.class,
                        java.lang.reflect.Modifier.PUBLIC),
                "gate: 非 static 不命中");
        expectTrue(!Hooks.isPolicyGate(new Class<?>[]{Context.class}, int.class, pubStatic),
                "gate: 返回 int 不命中");
        expectTrue(!Hooks.isPolicyGate(new Class<?>[]{String.class}, boolean.class, pubStatic),
                "gate: 参数非 Context 不命中");
        expectTrue(!Hooks.isPolicyGate(new Class<?>[]{Context.class, String.class}, boolean.class,
                        pubStatic),
                "gate: 参数个数不为 1 不命中");

        // 统计线程：Thread 子类 + 无参 void run() + 引用 android.app.usage.*
        expectTrue(Hooks.isUsageRun(UsageThread.class, "run", new Class<?>[]{}, void.class,
                        java.lang.reflect.Modifier.PUBLIC),
                "usage: 引用使用统计类型的 Thread 子类 run 命中");
        expectTrue(!Hooks.isUsageRun(PlainThread.class, "run", new Class<?>[]{}, void.class,
                        java.lang.reflect.Modifier.PUBLIC),
                "usage: 不引用使用统计类型的 Thread 子类不命中（避免误伤）");
        expectTrue(!Hooks.isUsageRun(HooksP0ContractTest.class, "run", new Class<?>[]{}, void.class,
                        java.lang.reflect.Modifier.PUBLIC),
                "usage: 非 Thread 子类不命中");
        expectTrue(!Hooks.isUsageRun(UsageThread.class, "other", new Class<?>[]{}, void.class,
                        java.lang.reflect.Modifier.PUBLIC),
                "usage: 方法名非 run 不命中");
        expectTrue(!Hooks.isUsageRun(UsageThread.class, "run", new Class<?>[]{int.class}, void.class,
                        java.lang.reflect.Modifier.PUBLIC),
                "usage: 带参 run 不命中");
        expectTrue(!Hooks.isUsageRun(UsageThread.class, "run", new Class<?>[]{}, void.class,
                        java.lang.reflect.Modifier.ABSTRACT),
                "usage: abstract run 不命中");

        // 接收器：onReceive(Context,Intent)->void
        expectTrue(Hooks.isReceiverOnReceive("onReceive",
                        new Class<?>[]{Context.class, Intent.class}, void.class,
                        java.lang.reflect.Modifier.PUBLIC),
                "receiver: onReceive(Context,Intent) 命中");
        expectTrue(!Hooks.isReceiverOnReceive("onReceive",
                        new Class<?>[]{Context.class}, void.class,
                        java.lang.reflect.Modifier.PUBLIC),
                "receiver: 单参不命中");
        expectTrue(!Hooks.isReceiverOnReceive("onReceive",
                        new Class<?>[]{Context.class, Intent.class}, int.class,
                        java.lang.reflect.Modifier.PUBLIC),
                "receiver: 返回非 void 不命中");
    }

    // 3. guardprovider 服务入口
    private static void guardServicePredicate() {
        int pub = java.lang.reflect.Modifier.PUBLIC;
        expectTrue(Hooks.isServiceOnStartCommand("onStartCommand",
                        new Class<?>[]{Intent.class, int.class, int.class}, int.class, pub),
                "guard: public onStartCommand(Intent,int,int)->int 命中");
        expectTrue(!Hooks.isServiceOnStartCommand("onStartCommand",
                        new Class<?>[]{Intent.class, int.class, int.class}, void.class, pub),
                "guard: 返回 void 不命中");
        expectTrue(!Hooks.isServiceOnStartCommand("onStartCommand",
                        new Class<?>[]{Intent.class, int.class}, int.class, pub),
                "guard: 参数不足不命中");
        expectTrue(!Hooks.isServiceOnStartCommand("onBind",
                        new Class<?>[]{Intent.class, int.class, int.class}, int.class, pub),
                "guard: 方法名非 onStartCommand 不命中");
        expectTrue(!Hooks.isServiceOnStartCommand("onStartCommand",
                        new Class<?>[]{Intent.class, int.class, int.class}, int.class,
                        java.lang.reflect.Modifier.PRIVATE),
                "guard: 非 public 不命中（避免误取同名 helper）");
        expectTrue(!Hooks.isServiceOnStartCommand("onStartCommand",
                        new Class<?>[]{Intent.class, int.class, int.class}, int.class,
                        pub | java.lang.reflect.Modifier.STATIC),
                "guard: static 不命中");
        expectTrue(!Hooks.isServiceOnStartCommand("onStartCommand",
                        new Class<?>[]{Intent.class, int.class, int.class}, int.class,
                        pub | java.lang.reflect.Modifier.ABSTRACT),
                "guard: abstract 不命中");
    }

    // 4. getCurrentLocation 契约
    //
    // 契约：返回 true = 已替调用方回调一次（拦截方吞掉原逻辑）；返回 false = 未回调，
    // 调用方必须放行原逻辑（chain.proceed()）。若返回 false 却吞掉原逻辑，调用方会永久等待。
    // 因此「失败路径一律返回 false 且 sink 不被调用」是放行语义的前提，逐条断言。
    private static void currentLocationContract() {
        Class<?>[] withBoth = {String.class, Executor.class, Consumer.class};
        Class<?>[] withoutExecutor = {String.class, Consumer.class};
        Class<?>[] withoutConsumer = {String.class, Executor.class};

        // 4a. 正常形态：恰好回调一次，且传 null
        final AtomicInteger count = new AtomicInteger();
        final List<Object> seen = Collections.synchronizedList(new ArrayList<>());
        List<Object> args = Arrays.<Object>asList("gps", (Executor) Runnable::run,
                (Consumer<Object>) seen::add);
        boolean delivered = Hooks.deliverEmptyCurrentLocation(withBoth, args, consumer -> {
            count.incrementAndGet();
            ((Consumer<Object>) consumer).accept(null);
        });
        expectTrue(delivered, "getCurrentLocation: 正常形态投递成功（拦截）");
        expectTrue(count.get() == 1, "getCurrentLocation: 恰好回调一次（实际 " + count.get() + "）");
        expectTrue(seen.size() == 1 && seen.get(0) == null, "getCurrentLocation: 回调传 null");

        // 4b. 缺 Executor：不投递、不回调 → 返回 false，调用方放行
        final AtomicInteger miss = new AtomicInteger();
        boolean d2 = Hooks.deliverEmptyCurrentLocation(withoutExecutor,
                Arrays.asList("gps", (Consumer<Object>) seen::add),
                consumer -> miss.incrementAndGet());
        expectTrue(!d2, "getCurrentLocation: 缺 Executor 返回 false（放行）");
        expectTrue(miss.get() == 0, "getCurrentLocation: 缺 Executor 时不触发回调");
        expectTrue(count.get() == 1, "getCurrentLocation: 缺 Executor 时不额外回调");

        // 4c. 缺 Consumer：不投递 → 返回 false，调用方放行
        boolean d3 = Hooks.deliverEmptyCurrentLocation(withoutConsumer,
                Arrays.asList("gps", (Executor) Runnable::run),
                consumer -> miss.incrementAndGet());
        expectTrue(!d3, "getCurrentLocation: 缺 Consumer 返回 false（放行）");
        expectTrue(miss.get() == 0, "getCurrentLocation: 缺 Consumer 时不触发回调");

        // 4d. Executor 抛异常：返回 false 且不抛 → 调用方放行
        Executor boom = command -> {
            throw new RuntimeException("executor down");
        };
        boolean d4 = Hooks.deliverEmptyCurrentLocation(withBoth,
                Arrays.asList("gps", boom, (Consumer<Object>) seen::add),
                consumer -> miss.incrementAndGet());
        expectTrue(!d4, "getCurrentLocation: Executor 异常返回 false（放行）且不抛");
        expectTrue(miss.get() == 0, "getCurrentLocation: Executor 异常时不触发回调");

        // 4e. 回调目标抛异常：不得向外传播，返回 false
        boolean d5;
        try {
            d5 = Hooks.deliverEmptyCurrentLocation(withBoth, args,
                    consumer -> {
                        throw new RuntimeException("consumer down");
                    });
            expectTrue(true, "getCurrentLocation: 回调抛异常不向外传播");
        } catch (Throwable t) {
            d5 = true;
            expectTrue(false, "getCurrentLocation: 回调抛异常不向外传播（实际抛出 " + t + "）");
        }
        expectTrue(!d5, "getCurrentLocation: 回调抛异常时返回 false");

        // 4f. 已取消：不回调，返回 false，与平台「取消后不再给结果」一致
        Class<?>[] withCancel = {String.class, CancellationSignal.class, Executor.class,
                Consumer.class};
        final AtomicInteger c2 = new AtomicInteger();
        boolean d6 = Hooks.deliverEmptyCurrentLocation(withCancel,
                Arrays.<Object>asList("gps", new CancellationSignal(true),
                        (Executor) Runnable::run, (Consumer<Object>) seen::add),
                consumer -> c2.incrementAndGet());
        expectTrue(!d6, "getCurrentLocation: 已取消返回 false（放行）");
        expectTrue(c2.get() == 0, "getCurrentLocation: 已取消时回调次数为 0");

        // 4g. 未取消：正常投递，恰好一次
        boolean d7 = Hooks.deliverEmptyCurrentLocation(withCancel,
                Arrays.<Object>asList("gps", new CancellationSignal(false),
                        (Executor) Runnable::run, (Consumer<Object>) seen::add),
                consumer -> c2.incrementAndGet());
        expectTrue(d7 && c2.get() == 1, "getCurrentLocation: 未取消时恰好投递一次（拦截）");

        // 4h. 已取消 + 缺 Executor：仍返回 false（取消优先），不触发回调
        boolean d8 = Hooks.deliverEmptyCurrentLocation(
                new Class<?>[]{String.class, CancellationSignal.class, Consumer.class},
                Arrays.<Object>asList("gps", new CancellationSignal(true),
                        (Consumer<Object>) seen::add),
                consumer -> c2.incrementAndGet());
        expectTrue(!d8, "getCurrentLocation: 已取消且缺 Executor 返回 false（放行）");
        expectTrue(c2.get() == 1, "getCurrentLocation: 已取消且缺 Executor 时不额外回调");
    }

    /** 引用了 android.app.usage.* 的线程子类：应被识别为使用统计线程。 */
    static final class UsageThread extends Thread {
        private android.app.usage.UsageStatsManager usm;

        @Override
        public void run() {
        }
    }

    /** 普通线程子类：不引用使用统计类型，不应被识别。 */
    static final class PlainThread extends Thread {
        @Override
        public void run() {
        }
    }

    private static void expectTrue(boolean cond, String what) {
        if (cond) {
            System.out.println("  ok    " + what);
        } else {
            failures++;
            System.out.println("  FAIL  " + what);
        }
    }

    private HooksP0ContractTest() {
    }
}
