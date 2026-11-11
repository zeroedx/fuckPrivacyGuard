package io.github.zeroedx.fuckprivacyguard;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/**
 * 安装状态判定的离线回归测试（纯 JVM，无需设备、无需 root）。
 *
 * 复现本次真机问题的根因：Android 11+（API 30）起，targetSdk≥30 的应用按包名查询
 * 其它应用会受 Package Visibility 过滤，未在 {@code <queries>} 声明的包
 * {@code getApplicationInfo} 会抛 {@link PackageManager.NameNotFoundException}。
 *
 * 关键回归断言：
 *   1. 查询抛 NameNotFoundException → 未安装（真实不存在）；
 *   2. 查询抛 SecurityException（可见性受限）→ 无法确认，绝不谎报未安装；
 *   3. 查询抛其它运行时异常 → 无法确认；
 *   4. 查询成功 → 已安装；
 *   5. null / 空包名 / null Context → 无法确认；
 *   6. isInstalled 仅在「确认已安装」时为 true（旧实现会把受限当成未安装）。
 *
 * 这些断言证明分类逻辑正确，不涉及真实设备，也不声称真机验证。
 */
public final class InstallStatusTest {

    private static int failures = 0;

    public static void main(String[] args) {
        installed();
        notInstalled();
        visibilityRestrictedIsUnknownNotMissing();
        otherFailureIsUnknown();
        nullSafety();
        oldBehaviorRegression();

        if (failures > 0) {
            System.out.println("FAILED: " + failures + " assertion(s)");
            System.exit(1);
        }
        System.out.println("OK: InstallStatusTest passed");
    }

    /** 查询成功 → 已安装 */
    private static void installed() {
        Fake ctx = new Fake();
        ctx.pm.next = new ApplicationInfo();
        eq(InstallStatus.State.INSTALLED, InstallStatus.state(ctx, "com.mobiletools.systemhelper"),
                "查询成功应判定为已安装");
        yes(InstallStatus.isInstalled(ctx, "com.mobiletools.systemhelper"),
                "isInstalled 对已安装应返回 true");
    }

    /** NameNotFoundException → 未安装（唯一被当作未安装的失败） */
    private static void notInstalled() {
        Fake ctx = new Fake();
        ctx.pm.next = null;   // 替身会抛 NameNotFoundException
        eq(InstallStatus.State.NOT_INSTALLED, InstallStatus.state(ctx, "com.example.absent"),
                "NameNotFoundException 应判定为未安装");
        no(InstallStatus.isInstalled(ctx, "com.example.absent"),
                "isInstalled 对未安装应返回 false");
    }

    /** 本次问题的核心：SecurityException（可见性受限）不得当成未安装 */
    private static void visibilityRestrictedIsUnknownNotMissing() {
        Fake ctx = new Fake();
        ctx.pm.nextError = new SecurityException("package not visible");
        eq(InstallStatus.State.UNKNOWN, InstallStatus.state(ctx, "com.mobiletools.systemhelper"),
                "可见性受限应判定为无法确认，而不是未安装");
        no(InstallStatus.isInstalled(ctx, "com.mobiletools.systemhelper"),
                "无法确认时 isInstalled 不能为 true");
        // 回归：受限绝不能显示成「未安装」
        ne(InstallStatus.State.NOT_INSTALLED, InstallStatus.state(ctx, "com.mobiletools.systemhelper"),
                "可见性受限不得被判为未安装");
    }

    /** 其它运行时异常 → 无法确认 */
    private static void otherFailureIsUnknown() {
        Fake ctx = new Fake();
        ctx.pm.nextError = new IllegalStateException("pm busy");
        eq(InstallStatus.State.UNKNOWN, InstallStatus.state(ctx, "com.miui.guardprovider"),
                "非 NameNotFound 的异常应判定为无法确认");
    }

    /** null 安全 */
    private static void nullSafety() {
        Fake ctx = new Fake();
        eq(InstallStatus.State.UNKNOWN, InstallStatus.state(ctx, null), "null 包名应为无法确认");
        eq(InstallStatus.State.UNKNOWN, InstallStatus.state(ctx, ""), "空包名应为无法确认");
        eq(InstallStatus.State.UNKNOWN, InstallStatus.state(null, "com.miui.guardprovider"),
                "null Context 应为无法确认");
    }

    /** 旧实现的错误行为回归：受限曾被当成未安装 */
    private static void oldBehaviorRegression() {
        Fake ctx = new Fake();
        ctx.pm.nextError = new SecurityException("visibility");
        // 旧逻辑：catch(Throwable){return false} 会把这种情况当成「未安装」；
        // 新逻辑必须区分二者。
        yes(InstallStatus.state(ctx, "com.mobiletools.systemhelper") != InstallStatus.State.NOT_INSTALLED,
                "回归：可见性受限不应等价于未安装");
    }

    /* ---------------- 断言 ---------------- */

    private static void eq(Object expected, Object actual, String msg) {
        if (expected == actual || (expected != null && expected.equals(actual))) return;
        failures++;
        System.out.println("  FAIL " + msg + " (expected=" + expected + ", actual=" + actual + ")");
    }

    private static void ne(Object a, Object b, String msg) {
        if (a != b) return;
        failures++;
        System.out.println("  FAIL " + msg + " (both=" + a + ")");
    }

    private static void yes(boolean cond, String msg) {
        if (cond) return;
        failures++;
        System.out.println("  FAIL " + msg);
    }

    private static void no(boolean cond, String msg) {
        yes(!cond, msg);
    }

    /* ---------------- 替身 ---------------- */

    /** 注入可控 PackageManager 的 Context 替身 */
    private static final class Fake extends Context {
        final FakePackageManager pm = new FakePackageManager();

        @Override
        public PackageManager getPackageManager() {
            return pm;
        }
    }

    /** 可脚本化的 PackageManager：由 next / nextError 决定这次查询的结果 */
    private static final class FakePackageManager extends PackageManager {
        @Override
        public ApplicationInfo getApplicationInfo(String packageName, int flags)
                throws NameNotFoundException {
            if (nextError != null) throw nextError;
            if (next == null) throw new NameNotFoundException(packageName);
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = packageName;
            return ai;
        }
    }
}
