package android.app;

/**
 * 离线测试用的最小 PendingIntent 替身。
 *
 * Hooks.isSmsSend 用类名字符串比较判定参数类型，不引用 PendingIntent 类字面量；
 * 本替身只让测试能用真实 Class 对象构造参数数组。
 */
public class PendingIntent {
}
