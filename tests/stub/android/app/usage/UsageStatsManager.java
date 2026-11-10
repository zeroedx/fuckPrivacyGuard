package android.app.usage;

/**
 * 离线测试用的最小 UsageStatsManager 替身。
 *
 * Hooks.referencesUsageTypes 用「类名前缀 android.app.usage.」判定，
 * 本替身只让测试能构造一个「引用了使用统计类型」的线程子类。
 */
public class UsageStatsManager {
}
