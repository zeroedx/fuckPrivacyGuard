package android.util;

import java.util.ArrayList;
import java.util.List;

/** 离线测试用的最小 Log 替身：把日志记进内存，供断言读取。 */
public final class Log {

    public static final List<String> LINES = new ArrayList<>();

    private Log() {
    }

    public static int i(String tag, String msg) {
        synchronized (LINES) {
            LINES.add(msg);
        }
        return 0;
    }

    public static String getStackTraceString(Throwable t) {
        return String.valueOf(t);
    }
}
