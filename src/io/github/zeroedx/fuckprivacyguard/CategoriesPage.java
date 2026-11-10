package io.github.zeroedx.fuckprivacyguard;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 分类页：完整保留原有 13 组与全部子项。
 *
 * 组开关等同该应用在 LSPosed 的作用域开关，点击只上报目标状态、等作用域真的变了再切换。
 * 子项开关直接写配置；打开子项时若该应用不在作用域内，顺手请求加入。
 */
final class CategoriesPage extends Page {

    CategoriesPage(MainActivity host, Ui ui) {
        super(host, ui);
    }

    @Override
    String title() {
        return Ui.T("分类");
    }

    @Override
    View build() {
        LinearLayout root = ui.column();
        root.addView(ui.sectionRow(Ui.T("组件拦截"),
                Ui.T("开关打开 = 拦截该行为，关闭 = 放行")));

        for (final Catalog.Group g : Catalog.GROUPS) {
            LinearLayout card = ui.card();
            card.addView(groupHeader(g));
            if (g.note != null && !g.note.isEmpty()) card.addView(ui.groupNote(g.note));

            // 作用域未知（LSPosed 未连接 / 尚未读取）时不能当成「未加入」：
            // 那会把已加入的应用显示成未加入。只有确实读到作用域且不含该包才提示加入。
            final boolean scopeKnown = ModuleState.I.scopeKnown();
            final TextView scopeHint = ui.text(Ui.T("未在作用域，点此加入"), 13, Ui.ACCENT);
            scopeHint.setPadding(ui.dp(4), ui.dp(10), 0, ui.dp(8));
            scopeHint.setVisibility(scopeKnown && !ModuleState.I.inScope(g.pkg)
                    ? View.VISIBLE : View.GONE);
            scopeHint.setOnClickListener(v -> host.ensureInScope(g));
            card.addView(scopeHint);

            for (Catalog.Item it : g.items) {
                if (card.getChildCount() > 0) card.addView(ui.divider());
                final LinearLayout row = ui.row();
                row.setMinimumHeight(ui.dp(56));
                row.setPadding(0, ui.dp(9), 0, ui.dp(9));

                LinearLayout texts = ui.column();
                texts.setPadding(0, 0, ui.dp(12), 0);
                texts.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                texts.addView(ui.title(it.name));
                TextView d = ui.text(it.desc, 12, ui.sub);
                d.setPadding(0, ui.dp(2), 0, 0);
                texts.addView(d);
                row.addView(texts);

                PgSwitch sw = ui.makeSwitch(Config.get(host, it.key, it.def), true);
                sw.setOnCheckedChangeListener((v, checked) -> {
                    Config.uiSet(host, it.key, checked);
                    if (checked) host.ensureInScope(g);
                });
                row.addView(sw);

                // 作用域未知时不做「变暗」处理：未知 ≠ 未加入。
                row.setAlpha(!scopeKnown || ModuleState.I.inScope(g.pkg) ? 1f : 0.4f);
                card.addView(row);
            }
            root.addView(card);
        }
        return root;
    }

    private View groupHeader(final Catalog.Group g) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(12), 0, ui.dp(8));

        LinearLayout texts = ui.column();
        texts.setPadding(0, 0, ui.dp(12), 0);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t1 = ui.text(g.title, 17, ui.text);
        TextView t2 = ui.text(g.pkg, 14, ui.sub);
        t2.setMaxLines(1);
        t2.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t2.setPadding(0, ui.dp(2), 0, 0);
        texts.addView(t1);
        texts.addView(t2);
        row.addView(texts);

        TextView reboot = ui.text(Ui.T("重启"), 13, Ui.ACCENT);
        reboot.setBackground(ui.pressBackground());
        reboot.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));
        reboot.setOnClickListener(v -> host.restartApp(g.pkg));
        row.addView(reboot);

        PgSwitch sw = ui.makeSwitch(Config.get(host, g.key, true), true);
        sw.setOnToggleRequest(target -> {
            if (host.updatingSwitch()) return;
            if (target) host.requestAdd(g.pkg, g);
            else host.requestRemove(g.pkg, g);
        });
        row.addView(sw);
        return row;
    }
}
