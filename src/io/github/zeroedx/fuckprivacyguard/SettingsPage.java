package io.github.zeroedx.fuckprivacyguard;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 设置页：通用开关、外观模式、自定义规则与日志管理。
 *
 * 外观模式存在独立的 SharedPreferences 文件里，不写进 {@link Config} 的协议，
 * 避免把纯界面偏好混进 hook 侧读取的 Settings.Global。
 */
final class SettingsPage extends Page {

    static final String UI_PREFS = "pg_ui";
    static final String K_THEME = "theme_mode";
    static final String THEME_AUTO = "auto";
    static final String THEME_LIGHT = "light";
    static final String THEME_DARK = "dark";

    SettingsPage(MainActivity host, Ui ui) {
        super(host, ui);
    }

    @Override
    String title() {
        return Ui.T("设置");
    }

    @Override
    PgIcon.Glyph glyph() {
        return PgIcon.Glyph.SLIDERS;
    }

    @Override
    View build() {
        LinearLayout root = ui.column();
        root.addView(ui.pageTitle(title()));

        root.addView(ui.sectionRow(Ui.T("通用"), Ui.T("开关打开 = 拦截该行为，关闭 = 放行")));
        LinearLayout general = ui.card();
        ui.switchRow(general, Ui.T("总开关"), Ui.T("关闭后本模块在所有进程不生效"),
                Config.get(host, Config.K_MASTER, true), true, (v, c) -> {
                    Config.uiSet(host, Config.K_MASTER, c);
                    host.toast(c ? Ui.T("已开启") : Ui.T("已关闭"));
                });
        general.addView(ui.divider());
        ui.switchRow(general, Ui.T("路径特征拦截"), Ui.T("路径规则（/avl/upload 等）是否生效"),
                Config.get(host, Config.K_PATH, true), true,
                (v, c) -> Config.uiSet(host, Config.K_PATH, c));
        general.addView(ui.divider());
        ui.switchRow(general, Ui.T("记录未拦截访问"), Ui.T("把没命中规则的域名写进日志，排查漏拦时临时打开"),
                Config.get(host, Config.K_SNIFF, false), true,
                (v, c) -> Config.uiSet(host, Config.K_SNIFF, c));
        general.addView(ui.divider());
        general.addView(hideIconRow());
        root.addView(general);

        root.addView(ui.sectionRow(Ui.T("外观"), null));
        LinearLayout appearance = ui.card();
        appearance.addView(themeRow(Ui.T("跟随系统"), THEME_AUTO));
        appearance.addView(ui.divider());
        appearance.addView(themeRow(Ui.T("浅色"), THEME_LIGHT));
        appearance.addView(ui.divider());
        appearance.addView(themeRow(Ui.T("深色"), THEME_DARK));
        root.addView(appearance);

        root.addView(ui.sectionRow(Ui.T("规则"), null));
        LinearLayout rules = ui.card();
        String raw = Config.customRulesRaw(host).trim();
        int count = raw.isEmpty() ? 0 : raw.split(";;").length;
        LinearLayout cr = ui.clickableRow(rules, Ui.T("自定义规则"),
                I18n.text("按域名、域名/路径 或 类名#方法 追加拦截，当前 %d 条", count), null);
        cr.setOnClickListener(v -> host.dialogs().customRules());
        rules.addView(ui.divider());
        LinearLayout fd = ui.clickableRow(rules, Ui.T("行为库状态"),
                Ui.T("读安全中心 fraud_detection_data.db 的写入计数，用来判断拦截是否生效"), null);
        fd.setOnClickListener(v -> host.dialogs().fraudDb());
        root.addView(rules);

        root.addView(ui.sectionRow(Ui.T("日志"), Ui.T("需要 root")));
        LinearLayout logs = ui.card();
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        row.addView(host.bulkButton(Ui.T("查看日志"), () -> host.dialogs().log(false)));
        row.addView(host.bulkButton(Ui.T("分享日志"), () -> host.dialogs().log(true)));
        row.addView(host.bulkButton(Ui.T("清理日志缓冲"), host::confirmCleanLog));
        logs.addView(row);
        root.addView(logs);

        TextView rootTip = ui.text(Ui.T("「重启」「查看日志」需要 root，首次使用请允许 KernelSU 授权"),
                11, ui.sub);
        rootTip.setPadding(ui.dp(12), ui.dp(8), 0, 0);
        root.addView(rootTip);

        return root;
    }

    private View themeRow(String label, String mode) {
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(56));
        row.setPadding(ui.dp(4), ui.dp(9), ui.dp(4), ui.dp(9));
        row.setClickable(true);
        row.setBackground(ui.pressBackground());
        TextView name = ui.text(label, 16, ui.text);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);
        boolean selected = mode.equals(host.themeMode());
        if (selected) row.addView(ui.text("✓", 16, Ui.ACCENT));
        row.setOnClickListener(v -> {
            host.setThemeMode(mode);
            host.rebuildUi();
        });
        return row;
    }

    private View hideIconRow() {
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(56));
        row.setPadding(0, ui.dp(9), 0, ui.dp(9));

        LinearLayout texts = ui.column();
        texts.setPadding(0, 0, ui.dp(12), 0);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        texts.addView(ui.title(Ui.T("隐藏桌面图标")));
        TextView d = ui.text(Ui.T("隐藏后从 LSPosed 模块页进入本界面"), 12, ui.sub);
        d.setPadding(0, ui.dp(2), 0, 0);
        texts.addView(d);
        row.addView(texts);

        PgSwitch sw = ui.makeSwitch(!host.launcherIconEnabled(), true);
        sw.setOnCheckedChangeListener((v, checked) -> {
            host.setLauncherIcon(!checked);
            boolean now = !host.launcherIconEnabled();
            if (now != checked) sw.setChecked(now);
            if (checked) host.toast(Ui.T("图标已隐藏，可从 LSPosed 模块页进入"));
        });
        row.addView(sw);
        return row;
    }
}
