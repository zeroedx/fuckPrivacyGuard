package io.github.zeroedx.fuckprivacyguard;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 各类弹窗：文本查看、自定义规则、行为库、日志、免责声明、检查更新。
 *
 * 从原 MainActivity 抽出，逻辑与文案保持不变，仅改用 {@link Ui} 取色以支持深浅色。
 */
final class Dialogs {

    private static final String FRAUD_DB =
            "/data/misc/user/0/securitycenter/fraud_detection_data.db";
    private static java.util.Map<String, Integer> lastSeq;

    private final MainActivity host;
    private final Ui ui;

    Dialogs(MainActivity host, Ui ui) {
        this.host = host;
        this.ui = ui;
    }

    private void style(AlertDialog d) {
        ui.styleDialog(d);
        host.trackDialog(d);
    }

    /* ---------------- 文本查看 ---------------- */

    void text(String title, final String body) {
        TextView tv = new TextView(host);
        tv.setText(body);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTextIsSelectable(true);
        tv.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));

        ScrollView sv = new ScrollView(host);
        sv.addView(tv);

        LinearLayout titleBar = ui.row();
        titleBar.setPadding(ui.dp(24), ui.dp(18), ui.dp(24), ui.dp(6));
        TextView titleText = ui.text(title, 18, ui.text);
        titleText.setTypeface(null, Typeface.BOLD);
        titleBar.addView(titleText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (title != null && !title.contains("自检")) {
            titleBar.addView(ui.text("v" + Config.VERSION, 12, ui.sub));
        }

        AlertDialog d = new AlertDialog.Builder(host)
                .setCustomTitle(titleBar)
                .setView(sv)
                .setPositiveButton(Ui.T("复制"), (x, w) -> {
                    ClipboardManager cm = (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("fpg", body));
                    Toast.makeText(host, "已复制", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(Ui.T("分享"), (x, w) -> share(body))
                .setNegativeButton(Ui.T("关闭"), null)
                .create();
        d.show();
        style(d);
    }

    void share(String log) {
        if (log == null || log.trim().isEmpty()) {
            Toast.makeText(host, "没有可分享的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "运行日志");
        i.putExtra(Intent.EXTRA_TEXT, log);
        host.startActivity(Intent.createChooser(i, "分享运行日志"));
    }

    /* ---------------- 自定义规则 ---------------- */

    void customRules() {
        LinearLayout box = ui.column();
        box.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(6));

        final EditText input = new EditText(host);
        input.setHint(Ui.T("域名、域名/路径 或 类名#方法"));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setTextColor(ui.text);
        input.setHintTextColor(ui.sub);
        input.setSingleLine(true);
        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setColor(ui.night ? 0xFF2C2C2E : 0xFFF2F3F5);
        fieldBg.setCornerRadius(ui.dp(12));
        input.setBackground(fieldBg);
        input.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        box.addView(input);

        final LinearLayout ruleList = ui.column();

        LinearLayout actions = ui.row();
        actions.setPadding(0, ui.dp(6), 0, ui.dp(2));

        TextView add = ui.text(Ui.T("添加"), 14, Ui.ACCENT);
        add.setTag("accent");
        add.setGravity(Gravity.CENTER);
        add.setBackground(ui.pressBackground());
        add.setPadding(0, ui.dp(10), 0, ui.dp(10));
        add.setOnClickListener(v -> {
            String val = input.getText().toString().trim();
            boolean okHost = val.matches("[A-Za-z0-9._\\-]+(/[A-Za-z0-9._\\-]*)*");
            boolean okMethod = val.matches("[A-Za-z0-9._$]+#[A-Za-z0-9_$*<>]+");
            if (!okHost && !okMethod) {
                Toast.makeText(host, "格式：域名、域名/路径 或 类名#方法", Toast.LENGTH_SHORT).show();
                return;
            }
            String raw = Config.customRulesRaw(host).trim();
            for (String one : raw.split(";;")) {
                if (one.trim().equals(val)) {
                    Toast.makeText(host, "已存在", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Config.setCustomRules(host, raw.isEmpty() ? val : raw + ";;" + val);
            input.setText("");
            renderRules(ruleList);
        });
        actions.addView(add, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = ui.text(Ui.T("清空"), 14, Ui.ACCENT);
        clear.setTag("accent");
        clear.setGravity(Gravity.CENTER);
        clear.setBackground(ui.pressBackground());
        clear.setPadding(0, ui.dp(10), 0, ui.dp(10));
        clear.setOnClickListener(v -> {
            Config.setCustomRules(host, "");
            renderRules(ruleList);
        });
        actions.addView(clear, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView doc = ui.text(Ui.T("使用说明"), 13, Ui.ACCENT);
        doc.setTag("accent");
        doc.setBackground(ui.pressBackground());
        doc.setPadding(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(10));
        doc.setOnClickListener(v -> host.openUrl(Catalog.URL_CUSTOM_RULES_DOC));

        TextView note = ui.text(Ui.T("添加或删除后需要重启对应应用才生效"), 12, ui.sub);

        box.addView(actions);
        box.addView(ruleList);
        box.addView(doc);
        box.addView(note);

        ScrollView sv = new ScrollView(host);
        sv.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle(Ui.T("自定义规则"))
                .setView(sv)
                .setNegativeButton(Ui.T("关闭"), null)
                .create();
        dialog.show();
        style(dialog);
        renderRules(ruleList);
    }

    private void renderRules(final LinearLayout ruleList) {
        ruleList.removeAllViews();
        String raw = Config.customRulesRaw(host).trim();
        if (raw.isEmpty()) {
            TextView empty = ui.text(Ui.T("还没有规则"), 13, ui.sub);
            empty.setPadding(0, ui.dp(14), 0, ui.dp(8));
            ruleList.addView(empty);
            return;
        }
        for (String one : raw.split(";;")) {
            final String rule = one.trim();
            if (rule.isEmpty()) continue;
            LinearLayout row = ui.row();
            row.setMinimumHeight(ui.dp(44));
            TextView name = ui.text(rule, 14, ui.text);
            name.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);
            TextView del = ui.text(Ui.T("删除"), 13, Ui.ACCENT);
            del.setTag("accent");
            del.setBackground(ui.pressBackground());
            del.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));
            del.setOnClickListener(v -> {
                StringBuilder sb = new StringBuilder();
                for (String x : Config.customRulesRaw(host).trim().split(";;")) {
                    String t = x.trim();
                    if (t.isEmpty() || t.equals(rule)) continue;
                    if (sb.length() > 0) sb.append(";;");
                    sb.append(t);
                }
                Config.setCustomRules(host, sb.toString());
                renderRules(ruleList);
            });
            row.addView(del);
            ruleList.addView(row);
        }
    }

    /* ---------------- 行为库状态 ---------------- */

    void fraudDb() {
        LinearLayout box = ui.column();
        box.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));

        final TextView tv = ui.text("读取中…", 12, ui.text);
        tv.setTextIsSelectable(false);
        tv.setLongClickable(false);
        box.addView(tv);

        TextView refresh = ui.text(Ui.T("刷新"), 14, Ui.ACCENT);
        refresh.setTag("accent");
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(ui.pressBackground());
        refresh.setPadding(0, ui.dp(10), 0, ui.dp(10));
        box.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(host);
        sv.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle(Ui.T("行为库状态"))
                .setView(sv)
                .setNegativeButton(Ui.T("关闭"), null)
                .create();

        final Runnable load = () -> new Thread(() -> {
            final String text = readFraudDb();
            host.runOnUiThread(() -> tv.setText(text));
        }, "fpg-frauddb").start();

        refresh.setOnClickListener(v -> {
            tv.setText("读取中…");
            load.run();
        });
        dialog.show();
        style(dialog);
        load.run();
    }

    /** 只读计数：复制主库与 -wal 到私有目录，查 sqlite_sequence，不读任何记录内容 */
    private String readFraudDb() {
        String dir = host.getFilesDir().getAbsolutePath();
        ModuleState.I.execShell("cp " + FRAUD_DB + " " + dir + "/fd.db 2>/dev/null; "
                + "cp " + FRAUD_DB + "-wal " + dir + "/fd.db-wal 2>/dev/null; "
                + "chmod 644 " + dir + "/fd.db 2>/dev/null; "
                + "chmod 644 " + dir + "/fd.db-wal 2>/dev/null; echo ok");
        java.io.File f = new java.io.File(dir, "fd.db");
        if (!f.exists() || f.length() == 0) {
            return "读不到行为库。\n\n需要 root 授权，且设备上存在 "
                    + "securitycenter/fraud_detection_data.db。";
        }
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            java.util.TreeMap<String, Integer> now = new java.util.TreeMap<>();
            android.database.Cursor c = db.rawQuery("select name, seq from sqlite_sequence", null);
            while (c.moveToNext()) now.put(c.getString(0), c.getInt(1));
            c.close();
            if (now.isEmpty()) return "库里没有写入记录。";
            StringBuilder sb = new StringBuilder();
            int total = 0;
            for (java.util.Map.Entry<String, Integer> e : now.entrySet()) {
                int v = e.getValue();
                total += v;
                sb.append(e.getKey()).append("  ").append(v);
                Integer old = lastSeq == null ? null : lastSeq.get(e.getKey());
                if (old != null && v != old) sb.append("  (+").append(v - old).append(')');
                sb.append('\n');
            }
            lastSeq = now;
            sb.append("\n共 ").append(now.size()).append(" 张表，累计 ").append(total).append(" 次写入");
            sb.append("\n\n这些数字只增不减。刷新后出现 (+N) 说明期间有写入；");
            sb.append("拦截生效时应当保持不变。");
            return sb.toString();
        } catch (Throwable t) {
            return "读取失败：" + t;
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) { }
            new java.io.File(dir, "fd.db-wal").delete();
            new java.io.File(dir, "fd.db-shm").delete();
            f.delete();
        }
    }

    /* ---------------- 日志 ---------------- */

    void log(final boolean share) {
        Toast.makeText(host, "正在读取日志，约需数秒（需 root）", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            final String log = execLogcat();
            host.runOnUiThread(() -> {
                if (share) share(log);
                else showLog(log);
            });
        }, "fpg-log").start();
    }

    private void showLog(final String log) {
        String body = "改动任一开关后，需要点该组右上角「重启」或「全部重启」，"
                + "目标应用重新加载规则才会生效。\n\n" + classify(log);
        text("模块日志（" + log.split("\n").length + " 行）", body);
    }

    private static final int LOG_SECTION_LIMIT = 20;

    /** 按未拦截 / 已拦截 / 其他分段，避免混在一起看不出结果 */
    private static String classify(String log) {
        java.util.List<String> blocked = new java.util.ArrayList<>();
        java.util.List<String> passed = new java.util.ArrayList<>();
        java.util.List<String> other = new java.util.ArrayList<>();
        for (String line : log.split("\n")) {
            if (line.trim().isEmpty()) continue;
            if (line.contains("blocked by fuckPrivacyGuard")
                    || line.contains(" X(host") || line.contains(" X(strict)")) {
                blocked.add(line);
            } else if (line.contains(": ? ")) {
                passed.add(line);
            } else {
                other.add(line);
            }
        }
        if (blocked.isEmpty() && passed.isEmpty()) return log;
        StringBuilder sb = new StringBuilder();
        sb.append(section("未拦截", passed,
                "未拦截指该域名不在当前规则里，不是拦截失败。\n"));
        sb.append(section("已拦截", blocked, null));
        sb.append(section("模块日志", other, null));
        return sb.toString();
    }

    /** 常见上报域名的用途，用于给「未拦截」行加注 */
    private static final String[][] HOST_HINTS = {
            {"tracking.miui.com", "统计埋点"},
            {"msg.xiaomi.net", "推送接入调度"},
            {"adv.sec.miui.com", "广告位"},
            {"logupdate.avlyun", "病毒引擎日志"},
            {"rcpc-sdk", "腾讯 RCPC SDK"},
            {"net-assistant", "网络助手"},
            {"game.sec.miui.com", "游戏加速"},
            {"pre-api.miui.security", "游戏加速用户信息"},
            {"flash.sec.miui.com", "安全检测接口"},
            {"api.sec.miui.com", "安全中心接口"},
            {"data.sec.miui.com", "月报与配置上报"},
            {"framework.sec.miui.com", "XSOF 鉴权"},
            {"virus-engine", "病毒库云查"},
            {"deviceapi.micloud", "设备证书/查找设备"},
            {"find.api.micloud", "查找设备"},
            {"zzhc.vnet.cn", "运营商注册"},
            {"typdm.tydevice.com", "CTEI 明文上报"},
            {"fxltsbl.com", "移动 DM 上报"},
            {"dm.wo.com.cn", "运营商遥测"},
            {"xiaomimobile.com", "运营商合作服务"},
            {"m.qq.com", "腾讯 SDK"},
    };

    private static String hintFor(String line) {
        int i = line.indexOf(": ? ");
        if (i < 0) return null;
        String rest = line.substring(i + 4).trim();
        int sp = rest.indexOf(' ');
        if (sp < 0) return null;
        String target = rest.substring(sp + 1).trim();
        int slash = target.indexOf('/');
        String host = (slash > 0 ? target.substring(0, slash) : target).toLowerCase();
        for (String[] h : HOST_HINTS) {
            if (host.contains(h[0])) return h[1];
        }
        return null;
    }

    /** 每段只显示最近若干行，附总数与说明 */
    private static String section(String name, java.util.List<String> lines, String note) {
        if (lines.isEmpty()) return "";
        int total = lines.size();
        int from = Math.max(0, total - LOG_SECTION_LIMIT);
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("（共 ").append(total).append(" 条");
        if (total > LOG_SECTION_LIMIT) sb.append("，显示最近 ").append(LOG_SECTION_LIMIT).append(" 条");
        sb.append("）\n");
        if (note != null) sb.append(note);
        for (int i = from; i < total; i++) {
            String line = lines.get(i);
            String hint = note == null ? null : hintFor(line);
            sb.append(hint == null ? line : line + "   ← " + hint).append('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    private String execLogcat() {
        String out = ModuleState.I.execShell(
                "logcat -d -b all -v time | grep -E 'fuckPrivacyGuard(\\( *[0-9]+\\))?: |blocked by fuckPrivacyGuard' | tail -n 600");
        if (out.trim().isEmpty()) {
            return "没有记录。\n\n"
                    + "这里只列模块自身的运行日志（attach、hook 结果）和被拦截的域名访问；"
                    + "没有发生拦截时就是空的，不代表模块没工作。\n\n"
                    + "可能原因：\n"
                    + "1. root 未授权（su 弹窗没点允许）\n"
                    + "2. 模块刚启用，目标应用还没重启\n"
                    + "3. 对应应用的「启用」开关未打开\n"
                    + "4. 旧日志已被 logcat 环形缓冲覆盖\n\n"
                    + "「诊断」页可以看框架连接、作用域和已注入进程。";
        }
        return out;
    }

    /* ---------------- 免责声明 ---------------- */

    String readDisclaimerText() {
        String full;
        try {
            java.io.InputStream is = host.getAssets().open("DISCLAIMER.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            full = sb.toString();
        } catch (Throwable t) {
            return "免责声明读取失败：" + t;
        }
        if (!I18n.english()) return full;
        int idx = full.indexOf("English Summary");
        if (idx < 0) return full;
        return full.substring(idx + "English Summary".length()).trim();
    }

    String readDisclaimerBrief() {
        try {
            java.io.InputStream is = host.getAssets().open("DISCLAIMER_BRIEF.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            String text = sb.toString().trim();
            int idx = text.indexOf("English Summary");
            if (idx < 0) return text;
            if (I18n.english()) return text.substring(idx + "English Summary".length()).trim();
            return text.substring(0, idx).trim();
        } catch (Throwable t) {
            return readDisclaimerText();
        }
    }

    void disclaimer() {
        TextView tv = new TextView(host);
        tv.setText(ui.styleDisclaimer(readDisclaimerText()));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextIsSelectable(true);
        tv.setLineSpacing(0f, 1.25f);
        tv.setTextColor(ui.text);
        tv.setPadding(ui.dp(18), ui.dp(10), ui.dp(18), ui.dp(10));

        ScrollView sv = new ScrollView(host);
        sv.addView(tv);

        AlertDialog d = new AlertDialog.Builder(host)
                .setTitle(Ui.T("免责声明"))
                .setView(sv)
                .setPositiveButton(Ui.T("我知道了"), null)
                .create();
        d.show();
        style(d);
    }

    /* ---------------- 检查更新 ---------------- */

    void update() {
        Toast.makeText(host, "正在检查更新…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String tag = null, err = null;
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(Catalog.API_LATEST).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "fuckPrivacyGuard/" + Config.VERSION);
                int code = c.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String ln;
                    while ((ln = br.readLine()) != null) sb.append(ln);
                    br.close();
                    tag = new org.json.JSONObject(sb.toString()).optString("tag_name", "");
                } else {
                    err = "HTTP " + code;
                }
            } catch (Throwable t) {
                err = t.getClass().getSimpleName();
            } finally {
                if (c != null) c.disconnect();
            }
            final String fTag = tag, fErr = err;
            host.runOnUiThread(() -> showUpdateResult(fTag, fErr));
        }, "fpg-check-update").start();
    }

    private void showUpdateResult(String tag, String err) {
        if (err != null) {
            Toast.makeText(host, "检查失败：" + err, Toast.LENGTH_LONG).show();
            return;
        }
        String v = tag == null ? "" : tag.trim().replaceAll("^[vV]", "");
        if (v.isEmpty()) {
            Toast.makeText(host, "没拿到版本号，稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (compareVersion(v, Config.VERSION) > 0) {
            AlertDialog d = new AlertDialog.Builder(host)
                    .setTitle(Ui.T("发现新版本 v") + v)
                    .setMessage("当前 v" + Config.VERSION + "，最新 v" + v + "。")
                    .setPositiveButton(Ui.T("打开发布页"), (x, w) -> host.openUrl(Catalog.URL_RELEASES))
                    .setNegativeButton(Ui.T("稍后"), null)
                    .create();
            d.show();
            style(d);
        } else {
            Toast.makeText(host, "已是最新版本（v" + Config.VERSION + "）", Toast.LENGTH_SHORT).show();
        }
    }

    private static int compareVersion(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int xi = i < x.length ? versionPart(x[i]) : 0;
            int yi = i < y.length ? versionPart(y[i]) : 0;
            if (xi != yi) return xi > yi ? 1 : -1;
        }
        return 0;
    }

    private static int versionPart(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9].*$", ""));
        } catch (Throwable t) {
            return 0;
        }
    }
}
