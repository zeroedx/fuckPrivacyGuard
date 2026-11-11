package android.content;

/**
 * 离线测试用的最小 ContentResolver 替身。
 * Config 的旧版 Settings.Global 回落读取会调用它；测试里可返回 null 表示读不到。
 */
public class ContentResolver {
}
