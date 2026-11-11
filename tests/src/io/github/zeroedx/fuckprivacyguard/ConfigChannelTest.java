package io.github.zeroedx.fuckprivacyguard;

import java.util.HashMap;
import java.util.Map;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * 配置通道的离线回归测试（纯 JVM，无需设备、无需 root）。
 *
 * 覆盖「取消 root 配置同步」后的三条关键链路：
 *   1. 协议：ConfigWire 编解码往返，含自定义规则的 URL 转义；
 *   2. 持久化 + 跨进程读取：界面写入 → 通道（RemotePreferences 替身）→ hook 侧 load 读到；
 *   3. 重启后生效：新进程用同一通道句柄重新 load，读到最新值；
 *   4. 升级回落：通道为空时读旧版 Settings.Global，两者都没有则用默认值；
 *   5. 通道断开：RemotePreferences 不可用时不再声称同步成功（推送跳过，读取回落默认）。
 *
 * 这些断言证明「非 root 通道可用且跨进程可见」，不涉及真实设备，也不声称真机验证。
 */
public final class ConfigChannelTest {

    private static int failures = 0;

    public static void main(String[] args) {
        wireRoundTrip();
        crossProcessLoad();
        restartPicksUpLatest();
        legacyFallback();
        channelUnavailable();
        encodeSkipsEmptyCustom();

        if (failures > 0) {
            System.out.println("FAILED: " + failures + " assertion(s)");
            System.exit(1);
        }
        System.out.println("OK: ConfigChannelTest passed");
    }

    /** 测试用 Context：本地偏好与「通道」都放在内存里 */
    private static final class FakeContext extends Context {
        final MemoryPrefs local = new MemoryPrefs();
        final ContentResolver cr = new ContentResolver();

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return local;
        }

        @Override
        public ContentResolver getContentResolver() {
            return cr;
        }
    }

    /** 1. 协议往返 */
    private static void wireRoundTrip() {
        Map<String, String> in = new HashMap<>();
        in.put(Config.K_MASTER, "true");
        in.put(Config.K_LOC_SH, "false");
        in.put(Config.K_CUSTOM, "a.com/path;;b.com");
        String wire = ConfigWire.encode(in);
        Map<String, String> out = ConfigWire.decode(wire);
        expect("wire master", "true".equals(out.get(Config.K_MASTER)));
        expect("wire loc_sh", "false".equals(out.get(Config.K_LOC_SH)));
        // 自定义规则含 ; 与 /，必须原样还原
        expect("wire custom round-trip",
                "a.com/path;;b.com".equals(out.get(Config.K_CUSTOM)));
        // 含 | 或 = 的规则也要能还原（URL 编码后不破坏分隔）
        Map<String, String> in2 = new HashMap<>();
        in2.put(Config.K_CUSTOM, "x.com/p?a=1|b=2");
        expect("wire custom special chars",
                "x.com/p?a=1|b=2".equals(
                        ConfigWire.decode(ConfigWire.encode(in2)).get(Config.K_CUSTOM)));
    }

    /** 2. 界面写入 → 通道 → hook 侧读到（跨进程） */
    private static void crossProcessLoad() {
        FakeContext ctx = new FakeContext();
        MemoryPrefs channel = new MemoryPrefs();
        Config.bindRemote(channel);

        Config.uiSet(ctx, Config.K_MASTER, true);
        Config.uiSet(ctx, Config.K_LOC_SH, true);
        Config.uiSet(ctx, Config.K_SNIFF, true);
        waitForPush(channel, Config.K_SNIFF + "=true");

        // 通道里必须已经有整份 wire
        String wire = channel.getString(Config.REMOTE_KEY, null);
        expect("channel has wire", wire != null && wire.contains(Config.K_MASTER));

        // 模拟目标进程：同一份通道句柄，fresh load 后读到界面设置
        hookReset();
        Config.load(null);
        expect("hook master on", Config.masterOn());
        expect("hook loc_sh on", Config.on(Config.K_LOC_SH, false));
        expect("hook sniff on", Config.on(Config.K_SNIFF, false));
        // 未设置的项仍走默认
        expect("hook unset default", !Config.on(Config.K_AIASST, false));

        Config.bindRemote(null);
    }

    /** 3. 改开关后重启目标进程读到最新值 */
    private static void restartPicksUpLatest() {
        FakeContext ctx = new FakeContext();
        MemoryPrefs channel = new MemoryPrefs();
        Config.bindRemote(channel);

        Config.uiSet(ctx, Config.K_LOC_SH, false);
        waitForPush(channel, Config.K_LOC_SH + "=false");
        hookReset();
        Config.load(null);
        expect("before restart off", !Config.on(Config.K_LOC_SH, true));

        // 界面改成开，再「重启」目标进程（fresh load）
        Config.uiSet(ctx, Config.K_LOC_SH, true);
        waitForPush(channel, Config.K_LOC_SH + "=true");
        hookReset();
        Config.load(null);
        expect("after restart on", Config.on(Config.K_LOC_SH, false));

        Config.bindRemote(null);
    }

    /** 4. 通道为空时回落旧版 Settings.Global（升级兼容） */
    private static void legacyFallback() {
        FakeContext ctx = new FakeContext();
        Config.bindRemote(new MemoryPrefs()); // 空通道
        Settings.LEGACY_VALUE = Config.K_LOC_SH + "=true|" + Config.K_MASTER + "=true";

        hookReset();
        Config.load(ctx);
        expect("legacy loc_sh on", Config.on(Config.K_LOC_SH, false));
        expect("legacy master on", Config.masterOn());

        Settings.LEGACY_VALUE = null;
        hookReset();
        Config.load(ctx);
        expect("no channel no legacy -> default",
                !Config.on(Config.K_LOC_SH, false));

        Config.bindRemote(null);
    }

    /** 5. 通道不可用时推送跳过、读取回落默认（不声称同步成功） */
    private static void channelUnavailable() {
        FakeContext ctx = new FakeContext();
        Config.bindRemote(null);
        Config.uiSet(ctx, Config.K_LOC_SH, true);
        // 本地仍写入，供界面显示
        expect("local still written", Config.get(ctx, Config.K_LOC_SH, false));
        // 但 hook 侧读不到，走默认
        hookReset();
        Config.load(ctx);
        expect("unavailable -> default", !Config.on(Config.K_LOC_SH, false));
    }

    /** 6. 空自定义规则不写入通道，避免污染 */
    private static void encodeSkipsEmptyCustom() {
        Map<String, String> in = new HashMap<>();
        in.put(Config.K_CUSTOM, "");
        in.put(Config.K_MASTER, "true");
        String wire = ConfigWire.encode(in);
        expect("empty custom skipped", !wire.contains(Config.K_CUSTOM));
    }

    /* ---------------- 断言 ---------------- */

    /** 模拟「目标进程重启」：清掉 hook 侧的进程内缓存，下次 load 重新读通道 */
    @SuppressWarnings("unchecked")
    private static void hookReset() {
        try {
            java.lang.reflect.Field values = Config.class.getDeclaredField("REMOTE_VALUES");
            values.setAccessible(true);
            ((Map<String, Boolean>) values.get(null)).clear();
            java.lang.reflect.Field custom = Config.class.getDeclaredField("CUSTOM");
            custom.setAccessible(true);
            custom.set(null, "");
        } catch (Throwable t) {
            failures++;
            System.out.println("FAIL: hookReset " + t);
        }
    }

    /** 等后台推送线程把 wire 写进通道（内存实现很快，轮询即可） */
    private static void waitForPush(MemoryPrefs channel, String mustContain) {
        for (int i = 0; i < 400; i++) {
            String w = channel.getString(Config.REMOTE_KEY, null);
            if (w != null && w.contains(mustContain)) return;
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignored) {
                return;
            }
        }
    }

    private static void expect(String name, boolean ok) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + name);
        } else {
            System.out.println("ok: " + name);
        }
    }
}
