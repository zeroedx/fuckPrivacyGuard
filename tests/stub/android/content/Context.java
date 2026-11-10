package android.content;

/**
 * 离线测试用的最小 Context 替身。
 *
 * Hooks.isPolicyGate / isReceiverOnReceive 用类名字符串比较判定参数类型，
 * 不引用 Context 类字面量；本替身只让测试能用真实 Class 对象构造参数数组。
 */
public class Context {
}
