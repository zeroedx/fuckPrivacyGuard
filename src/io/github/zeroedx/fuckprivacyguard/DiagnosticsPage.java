package io.github.zeroedx.fuckprivacyguard;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 诊断页：模块信息、真实作用域、可获取的运行记录与日志导出。
 *
 * 无法可靠获得的运行状态显示「未知」，不虚构注入数量或框架状态。
 */
final class DiagnosticsPage extends Page {

    DiagnosticsPage(MainActivity host, Ui ui) {
        super(host, ui);
    }

    @Override
    String title() {
        return Ui.T("诊断");
    }

    @Override
    PgIcon.Glyph glyph() {
        return PgIcon.Glyph.PULSE;
    }

    @Override
    View build() {
        LinearLayout root = ui.column();
        root.addView(ui.pageTitle(title()));

        root.addView(ui.sectionRow(Ui.T("模块信息"), null));
        LinearLayout info = ui.card();
        info.addView(ui.infoRow(Ui.T("模块版本"), "v" + Config.VERSION));
        info.addView(ui.line());
        info.addView(ui.infoRow(Ui.T("LSPosed"),
                ModuleState.I.connected() ? Ui.T("已连接") : Ui.T("未连接")));
        info.addView(ui.line());
        String fw = ModuleState.I.framework();
        info.addView(ui.infoRow(Ui.T("框架"), fw != null ? fw : Ui.T("未知")));
        info.addView(ui.line());
        info.addView(ui.infoRow(Ui.T("总开关"),
                Config.get(host, Config.K_MASTER, true) ? Ui.T("已开启") : Ui.T("已关闭")));
        root.addView(info);

        root.addView(ui.sectionRow(Ui.T("作用域"),
                ModuleState.I.scopeKnown() ? ModuleState.I.scope().size() + " 个" : Ui.T("未知")));
        LinearLayout scopeCard = ui.card();
        boolean first = true;
        for (Catalog.Group g : Catalog.GROUPS) {
            if (!first) scopeCard.addView(ui.line());
            first = false;
            String state = scopeStateLabel(g);
            scopeCard.addView(ui.infoRow(g.title, g.pkg + "  " + state));
        }
        root.addView(scopeCard);

        root.addView(ui.sectionRow(Ui.T("运行记录"), Ui.T("「查看日志」「自检」需要 root")));
        LinearLayout logs = ui.card();
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        row.addView(host.bulkButton(Ui.T("查看日志"), () -> host.dialogs().log(false)));
        row.addView(host.bulkButton(Ui.T("分享日志"), () -> host.dialogs().log(true)));
        row.addView(host.bulkButton(Ui.T("自检"), host::showDiagnostics));
        logs.addView(row);
        LinearLayout clean = ui.clickableRow(logs, Ui.T("清理日志缓冲"),
                Ui.T("清空 logcat 环形缓冲；其他应用的日志会一起清掉，配置与截图不受影响"),
                null);
        clean.setOnClickListener(v -> host.confirmCleanLog());
        root.addView(logs);

        return root;
    }

    /**
     * 作用域行的状态：先看安装状态，再看作用域。
     * 「无法确认」不再冒充「未安装」——只标注可见性受限，作用域仍按真实值显示。
     */
    private String scopeStateLabel(Catalog.Group g) {
        InstallStatus.State st = host.installState(g.pkg);
        if (st == InstallStatus.State.NOT_INSTALLED) return Ui.T("未安装");
        String scope;
        if (ModuleState.I.inScope(g.pkg)) scope = Ui.T("已加入");
        else scope = ModuleState.I.scopeKnown() ? Ui.T("未加入") : Ui.T("未知");
        if (st == InstallStatus.State.UNKNOWN) return Ui.T("安装状态无法确认") + " / " + scope;
        return scope;
    }
}
