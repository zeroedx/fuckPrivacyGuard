package io.github.vivibibi30.hyperpguard;

import java.util.ArrayList;
import java.util.List;

import android.app.PendingIntent;

/**
 * Hooks.isSmsSend 的离线单元测试（纯 JVM，无需设备）。
 *
 * 回归审计 P0-1：旧代码写死的三个 SmsManager 重载签名与 AOSP 不符，
 * getDeclaredMethod 抛 NoSuchMethodException，整段静默失效。改为签名谓词后，
 * 这里用真实存在的参数类型构造数组，验证：
 *   1. Android 10–17 上存在的每个发送重载都被判定为命中；
 *   2. 旧代码写错的签名被判定为不命中；
 *   3. 非发送方法 / 非 void 返回 / 参数不足一律不命中。
 *
 * 参数类型用真实 Class 对象（stub 提供 PendingIntent），与运行时一致。
 * 入口：tests/run.sh。任何断言失败时退出码非零。
 */
public final class HooksSmsSignatureTest {

    private static int failures = 0;

    public static void main(String[] args) {
        Class<?> PI = PendingIntent.class;

        // 1. 真实存在的发送重载 —— 必须命中
        yes("sendTextMessage(String,String,String,PI,PI)",
                "sendTextMessage", new Class<?>[]{
                        String.class, String.class, String.class, PI, PI});
        yes("sendTextMessage(String,String,String,PI,PI,long)",
                "sendTextMessage", new Class<?>[]{
                        String.class, String.class, String.class, PI, PI, long.class});
        yes("sendTextMessageWithoutPersisting(String,String,String,PI,PI)",
                "sendTextMessageWithoutPersisting", new Class<?>[]{
                        String.class, String.class, String.class, PI, PI});
        yes("sendMultipartTextMessage(String,String,ArrayList,ArrayList,ArrayList)",
                "sendMultipartTextMessage", new Class<?>[]{
                        String.class, String.class, ArrayList.class, ArrayList.class,
                        ArrayList.class});
        yes("sendMultipartTextMessage(String,String,List,List,List,long)",
                "sendMultipartTextMessage", new Class<?>[]{
                        String.class, String.class, List.class, List.class, List.class,
                        long.class});
        yes("sendMultipartTextMessage(String,String,List,List,List,String,String)",
                "sendMultipartTextMessage", new Class<?>[]{
                        String.class, String.class, List.class, List.class, List.class,
                        String.class, String.class});
        yes("sendDataMessage(String,String,short,byte[],PI,PI)",
                "sendDataMessage", new Class<?>[]{
                        String.class, String.class, short.class, byte[].class, PI, PI});

        // 2. 旧代码写错的签名 —— 必须不命中
        no("旧错误签名 sendTextMessage(String,String,String,String,PI)",
                "sendTextMessage", new Class<?>[]{
                        String.class, String.class, String.class, String.class, PI});
        no("旧错误签名 sendMultipartTextMessage(String,String,List,List,List) 5 参",
                "sendMultipartTextMessage", new Class<?>[]{
                        String.class, String.class, List.class, List.class, List.class});
        no("旧错误签名 sendDataMessage(String,String,short,byte[],PI) 5 参",
                "sendDataMessage", new Class<?>[]{
                        String.class, String.class, short.class, byte[].class, PI});

        // 3. 非发送方法 / 返回非 void / 参数不足
        no("非发送方法 sendMultimediaMessage",
                "sendMultimediaMessage", new Class<?>[]{
                        String.class, String.class, String.class, PI, PI});
        no("返回 boolean 不算发送", "sendTextMessage",
                new Class<?>[]{
                        String.class, String.class, String.class, PI, PI}, boolean.class);
        no("参数不足", "sendTextMessage",
                new Class<?>[]{String.class, String.class, String.class});
        no("首参非 String", "sendTextMessage",
                new Class<?>[]{int.class, String.class, String.class, PI, PI});
        no("末参非 PendingIntent", "sendTextMessage",
                new Class<?>[]{String.class, String.class, String.class, String.class,
                        String.class});

        if (failures > 0) {
            System.out.println("FAILED: " + failures + " assertion(s)");
            System.exit(1);
        }
        System.out.println("OK: HooksSmsSignatureTest passed");
    }

    private static void yes(String name, String method, Class<?>[] params) {
        expect(true, name, method, params, void.class);
    }

    private static void no(String name, String method, Class<?>[] params) {
        expect(false, name, method, params, void.class);
    }

    private static void no(String name, String method, Class<?>[] params, Class<?> ret) {
        expect(false, name, method, params, ret);
    }

    private static void expect(boolean want, String name, String method,
                               Class<?>[] params, Class<?> ret) {
        boolean got = Hooks.isSmsSend(method, params, ret);
        if (got != want) {
            failures++;
            System.out.println("  FAIL  " + name + "  want=" + want + " got=" + got);
        } else {
            System.out.println("  ok    " + name);
        }
    }
}
