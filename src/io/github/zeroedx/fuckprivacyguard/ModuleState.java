package io.github.zeroedx.fuckprivacyguard;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.libxposed.service.XposedService;

/**
 * 运行状态与跨进程操作。
 *
 * 只保留「无法从界面层可靠获得」的信息：LSPosed 连接、真实作用域、root 命令。
 * 读不到就如实返回未知，不猜测、不伪造注入数量。
 */
final class ModuleState {

    interface Callback {
        void run(boolean ok, String message);
    }

    static final ModuleState I = new ModuleState();

    private final Handler ui = new Handler(Looper.getMainLooper());

    /** LSPosed 服务；未连接时为 null */
    XposedService xposed;
    /** 真实作用域；null 表示尚未读取到（未知），空列表表示读取成功且为空 */
    private volatile List<String> scope;
    /** 状态变化回调，由宿主 Activity 设置 */
    Runnable onChanged = () -> {
    };

    private ModuleState() {
    }

    void notifyChanged() {
        try {
            onChanged.run();
        } catch (Throwable ignored) {
        }
    }

    boolean connected() {
        return xposed != null;
    }

    String framework() {
        if (xposed == null) return null;
        try {
            return xposed.getFrameworkName() + " " + xposed.getFrameworkVersion();
        } catch (Throwable t) {
            return null;
        }
    }

    boolean scopeKnown() {
        return scope != null;
    }

    List<String> scope() {
        return scope;
    }

    void setScope(List<String> s) {
        scope = s == null ? null : new ArrayList<>(s);
    }

    boolean inScope(String pkg) {
        List<String> s = scope;
        return s != null && s.contains(pkg);
    }

    /* ---------------- 作用域 ---------------- */

    void requestScope(final List<String> pkgs, final Callback cb) {
        if (xposed == null) {
            cb.run(false, "LSPosed 服务未连接");
            return;
        }
        try {
            xposed.requestScope(pkgs, new XposedService.OnScopeEventListener() {
                @Override
                public void onScopeRequestApproved(List<String> approved) {
                    ui.post(() -> cb.run(true, approved == null ? "" : String.valueOf(approved)));
                }

                @Override
                public void onScopeRequestFailed(String message) {
                    ui.post(() -> cb.run(false, message == null ? "" : message));
                }
            });
        } catch (Throwable t) {
            cb.run(false, String.valueOf(t));
        }
    }

    void removeScope(List<String> pkgs, final Callback cb) {
        if (xposed == null) {
            cb.run(false, "LSPosed 服务未连接");
            return;
        }
        try {
            xposed.removeScope(pkgs);
            cb.run(true, "");
        } catch (Throwable t) {
            cb.run(false, String.valueOf(t));
        }
    }

    /* ---------------- root 命令 ---------------- */

    /** 执行 root 命令并回读 stdout；失败时把异常文本原样返回，不吞掉 */
    String execShell(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            p.waitFor();
            return sb.toString();
        } catch (Throwable t) {
            return "执行失败：" + t;
        } finally {
            if (p != null) p.destroy();
        }
    }

    void execSu(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.waitFor();
        } catch (Throwable t) {
            Logs.i("su failed: " + cmd + " -> " + t);
        }
    }

    void restartApp(final String pkg, final Callback cb) {
        new Thread(() -> {
            String msg;
            boolean ok;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "am force-stop " + pkg});
                int code = p.waitFor();
                ok = code == 0;
                msg = ok ? (pkg + " 已重启") : (pkg + " 重启失败 code=" + code);
            } catch (Throwable t) {
                ok = false;
                msg = "重启失败：" + t;
            }
            final boolean fOk = ok;
            final String fMsg = msg;
            ui.post(() -> cb.run(fOk, fMsg));
        }, "fpg-restart").start();
    }

    void restartAll(final List<String> pkgs, final Callback cb) {
        if (pkgs == null || pkgs.isEmpty()) {
            cb.run(false, "作用域为空");
            return;
        }
        final StringBuilder cmd = new StringBuilder();
        for (String p : pkgs) cmd.append("am force-stop ").append(p).append("; ");
        new Thread(() -> {
            String msg;
            boolean ok;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd.toString()});
                int code = p.waitFor();
                ok = code == 0;
                msg = "已重启 " + pkgs.size() + " 个应用" + (ok ? "" : "（code=" + code + "）");
            } catch (Throwable t) {
                ok = false;
                msg = "重启失败：" + t;
            }
            final boolean fOk = ok;
            final String fMsg = msg;
            ui.post(() -> cb.run(fOk, fMsg));
        }, "fpg-restart-all").start();
    }

    static List<String> one(String pkg) {
        return Collections.singletonList(pkg);
    }
}
