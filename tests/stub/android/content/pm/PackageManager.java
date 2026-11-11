package android.content.pm;

import android.content.pm.ApplicationInfo;

/**
 * 离线测试用的最小 PackageManager 替身。
 *
 * 只实现 InstallStatus 用到的 {@code getApplicationInfo} 与常量。子类通过
 * {@link #next} 决定这次查询是「已安装」「未安装（NameNotFoundException）」
 * 还是「受限（SecurityException）」，用来复现 Android 11+ 的可见性过滤。
 */
public class PackageManager {

    public static final int MATCH_DISABLED_COMPONENTS = 0x00000200;

    /** 下一次 getApplicationInfo 的行为：null=未安装，异常=受限，其它=已安装 */
    public Object next;
    /** 非 null 时抛出该异常（模拟 SecurityException / 运行时异常） */
    public RuntimeException nextError;

    public ApplicationInfo getApplicationInfo(String packageName, int flags)
            throws NameNotFoundException {
        if (nextError != null) throw nextError;
        if (next == null) throw new NameNotFoundException(packageName);
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = packageName;
        return ai;
    }

    public static class NameNotFoundException extends Exception {
        public NameNotFoundException() {
            super();
        }

        public NameNotFoundException(String name) {
            super(name);
        }
    }
}
