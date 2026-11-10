package dalvik.system;

import java.util.Collections;
import java.util.Enumeration;

/**
 * 离线测试用的最小 DexFile 替身。
 * Hooks.classNames 里的 `(DexFile) dex` 转换需要该类在编译/链接期存在；
 * 测试不调用 classNames，这里只要满足引用即可。
 */
public final class DexFile {

    public Enumeration<String> entries() {
        return Collections.emptyEnumeration();
    }
}
