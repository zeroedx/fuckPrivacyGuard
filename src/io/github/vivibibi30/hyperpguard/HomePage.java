package io.github.vivibibi30.hyperpguard;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 首页：品牌图标、模块版本、运行状态、总开关与快捷入口。
 *
 * 状态一律来自真实来源（LSPosed 连接 / 真实作用域），读不到就显示「未知」，不猜测。
 */
final class HomePage extends Page {

    HomePage(MainActivity host, Ui ui) {
        super(host, ui);
    }

    @Override
    String title() {
        return Ui.T("首页");
    }

    @Override
    View build() {
        LinearLayout root = ui.column();

        /* 品牌头图 */
        LinearLayout brand = ui.column();
        brand.setGravity(Gravity.CENTER_HORIZONTAL);
        brand.setPadding(0, ui.dp(16), 0, ui.dp(16));
        ImageView logo = new ImageView(host);
        android.graphics.drawable.Drawable icon = ui.icon("brand_icon");
        if (icon != null) logo.setImageDrawable(icon);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(88), ui.dp(88));
        logo.setLayoutParams(lp);
        brand.addView(logo);
        TextView name = ui.text("fuckPrivacyGuard", 22, ui.text);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setPadding(0, ui.dp(10), 0, 0);
        brand.addView(name);
        TextView ver = ui.text("v" + Config.VERSION, 12, ui.sub);
        brand.addView(ver);
        root.addView(brand);

        /* 运行状态 */
        root.addView(ui.sectionRow(Ui.T("运行状态"), null));
        LinearLayout status = ui.card();
        status.addView(ui.infoRow(Ui.T("LSPosed"),
                ModuleState.I.connected() ? Ui.T("已连接") : Ui.T("未连接")));
        status.addView(ui.line());
        String fw = ModuleState.I.framework();
        status.addView(ui.infoRow(Ui.T("框架"), fw != null ? fw : Ui.T("未知")));
        status.addView(ui.line());
        status.addView(ui.infoRow(Ui.T("作用域"),
                ModuleState.I.scopeKnown() ? ModuleState.I.scope().size() + " 个" : Ui.T("未知")));
        status.addView(ui.line());
        status.addView(ui.infoRow(Ui.T("组件分组"), Catalog.GROUPS.length + " 组"));
        root.addView(status);

        /* 总开关 */
        root.addView(ui.sectionRow(Ui.T("总开关"), Ui.T("关闭后本模块在所有进程不生效")));
        LinearLayout master = ui.card();
        ui.switchRow(master, Ui.T("总开关"), Ui.T("关闭后本模块在所有进程不生效"),
                Config.get(host, Config.K_MASTER, true), true, (v, checked) -> {
                    Config.uiSet(host, Config.K_MASTER, checked);
                    host.toast(checked ? Ui.T("已开启") : Ui.T("已关闭"));
                });
        root.addView(master);

        /* 快捷入口 */
        root.addView(ui.sectionRow(Ui.T("快捷入口"), null));
        LinearLayout quick = ui.card();
        LinearLayout bulk = ui.row();
        bulk.setPadding(0, ui.dp(8), 0, ui.dp(8));
        bulk.addView(host.bulkButton(Ui.T("全部启用"), host::requestAll));
        bulk.addView(host.bulkButton(Ui.T("全部停用"), host::removeAll));
        bulk.addView(host.bulkButton(Ui.T("全部重启"), host::restartAll));
        quick.addView(bulk);
        root.addView(quick);

        return root;
    }
}
