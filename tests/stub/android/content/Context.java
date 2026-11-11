package android.content;

/**
 * 离线测试用的最小 Context 替身。
 *
 * Hooks.isPolicyGate / isReceiverOnReceive 用类名字符串比较判定参数类型，
 * 不引用 Context 类字面量；本替身只让测试能用真实 Class 对象构造参数数组。
 *
 * Config 的离线测试会继承本类并覆写 getSharedPreferences / getContentResolver，
 * 注入内存实现，因此这里给出默认实现（返回 null）。
 */
public class Context {

    public static final int MODE_PRIVATE = 0;

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return null;
    }

    public ContentResolver getContentResolver() {
        return null;
    }

    public android.content.pm.PackageManager getPackageManager() {
        return null;
    }
}
