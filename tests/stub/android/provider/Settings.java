package android.provider;

import android.content.ContentResolver;

/**
 * 离线测试用的最小 Settings 替身。Config 只在升级回落到旧版 Settings.Global 时读取它，
 * 测试默认让它返回 null（读不到），从而走默认值分支。
 */
public final class Settings {

    /** 测试可注入的旧版 Settings.Global 值；null 表示读不到 */
    public static volatile String LEGACY_VALUE;

    private Settings() {
    }

    public static final class Global {
        private Global() {
        }

        public static String getString(ContentResolver cr, String name) {
            return LEGACY_VALUE;
        }
    }
}
