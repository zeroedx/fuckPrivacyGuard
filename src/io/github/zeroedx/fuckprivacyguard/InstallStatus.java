package io.github.zeroedx.fuckprivacyguard;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/**
 * 组件安装状态判定。
 *
 * 关键点：从 Android 11（API 30）起，即使本模块的 targetSdk 是 34，按包名查询
 * 其它应用也会受 Package Visibility 过滤——未在 {@code <queries>} 里声明、也不在
 * 可见性规则内的包，{@link PackageManager#getApplicationInfo} 会抛
 * {@link PackageManager.NameNotFoundException}。旧实现把这个异常和「真的没装」
 * 一律当成未安装，于是已安装且已注入的系统组件被显示成「未安装」。
 *
 * 因此这里把「查询失败」与「确认未安装」分开：只有 {@code NameNotFoundException}
 * 才算未安装；{@code SecurityException}（可见性受限）或其它异常一律记为「无法确认」，
 * 绝不谎报未安装。{@code <queries>} 已按 Catalog 精确声明各组件包名。
 */
final class InstallStatus {

    private InstallStatus() {
    }

    /** 已安装、未安装、无法确认三态；无法确认不等于未安装 */
    static boolean isInstalled(Context ctx, String pkg) {
        return state(ctx, pkg) == State.INSTALLED;
    }

    static State state(Context ctx, String pkg) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return State.UNKNOWN;
        PackageManager pm;
        try {
            pm = ctx.getPackageManager();
        } catch (Throwable t) {
            return State.UNKNOWN;
        }
        if (pm == null) return State.UNKNOWN;
        try {
            // MATCH_DISABLED_COMPONENTS：即使组件被停用，仍算「已安装」
            ApplicationInfo info = pm.getApplicationInfo(
                    pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
            return info != null ? State.INSTALLED : State.UNKNOWN;
        } catch (PackageManager.NameNotFoundException notFound) {
            return State.NOT_INSTALLED;
        } catch (SecurityException denied) {
            Logs.i("install query denied (visibility): " + pkg);
            return State.UNKNOWN;
        } catch (Throwable t) {
            Logs.i("install query failed: " + pkg + " " + t);
            return State.UNKNOWN;
        }
    }

    enum State {
        INSTALLED,
        NOT_INSTALLED,
        UNKNOWN
    }
}
