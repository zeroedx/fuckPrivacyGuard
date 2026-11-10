package android.os;

/**
 * 离线测试用的最小 CancellationSignal 替身。
 *
 * Hooks 用反射按类名字符串判定该类型、反射调用 isCanceled()，不引用类字面量；
 * 本替身只让测试能构造「已取消 / 未取消」两种实参。
 */
public class CancellationSignal {
    private final boolean canceled;

    public CancellationSignal(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isCanceled() {
        return canceled;
    }
}
