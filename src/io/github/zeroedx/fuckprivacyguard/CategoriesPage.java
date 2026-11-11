package io.github.zeroedx.fuckprivacyguard;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 分类页：以系统组件为单位列出全部 {@link Catalog.Group}。
 *
 * 每项显示组件名称、包名、安装状态与作用域状态，点击进入该组件的二级页面
 * （{@link ComponentDetailPage}），二级页面里才是全部开关。本页不再直接放开关。
 * 未安装组件照样列出并明确标注「未安装」，不会显示成正在保护。
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
    PgIcon.Glyph glyph() {
        return PgIcon.Glyph.GRID;
    }

    @Override
    View build() {
        LinearLayout root = ui.column();
        root.addView(ui.pageTitle(title()));
        root.addView(ui.sectionRow(Ui.T("系统组件"),
                Ui.T("点击组件查看该组件的全部开关")));

        final boolean scopeKnown = ModuleState.I.scopeKnown();
        LinearLayout card = ui.card();
        boolean first = true;
        for (final Catalog.Group g : Catalog.GROUPS) {
            if (!first) card.addView(ui.divider());
            first = false;
            card.addView(componentRow(g, scopeKnown));
        }
        root.addView(card);

        TextView tip = ui.text(Ui.T("开关改动后需重启目标进程才生效"), 12, ui.sub);
        tip.setPadding(ui.dp(4), ui.dp(2), ui.dp(4), 0);
        root.addView(tip);
        return root;
    }

    private View componentRow(final Catalog.Group g, boolean scopeKnown) {
        final InstallStatus.State state = host.installState(g.pkg);
        final boolean installed = state == InstallStatus.State.INSTALLED;

        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(68));
        row.setPadding(ui.dp(2), ui.dp(12), ui.dp(2), ui.dp(12));
        row.setClickable(true);
        row.setBackground(ui.pressBackground());

        android.widget.ImageView badge = ui.componentIcon(g.pkg, badgeLabel(g.title), 40);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40));
        blp.rightMargin = ui.dp(14);
        blp.gravity = Gravity.CENTER_VERTICAL;
        row.addView(badge, blp);

        LinearLayout texts = ui.column();
        texts.setPadding(0, 0, ui.dp(12), 0);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        texts.addView(ui.title(g.title));

        TextView pkg = ui.text(g.pkg, 12, ui.sub);
        pkg.setMaxLines(1);
        pkg.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pkg.setPadding(0, ui.dp(2), 0, 0);
        texts.addView(pkg);

        boolean inScope = scopeKnown && ModuleState.I.inScope(g.pkg);
        int fg = stateColor(state, scopeKnown, inScope);
        TextView stateChip = ui.chip(stateText(state, scopeKnown, inScope), fg,
                installed && inScope ? ui.accentSoft : ui.chipBg);
        LinearLayout chipWrap = ui.row();
        chipWrap.setPadding(0, ui.dp(6), 0, 0);
        chipWrap.addView(stateChip);
        texts.addView(chipWrap);
        row.addView(texts);

        android.widget.ImageView arrow = ui.icon(PgIcon.Glyph.ARROW, ui.sub, 16);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16));
        alp.gravity = Gravity.CENTER_VERTICAL;
        row.addView(arrow, alp);

        row.setOnClickListener(v -> host.openComponent(g));
        // 只有「确认未安装」才置灰；「无法确认」保持正常，不暗示没装
        row.setAlpha(state == InstallStatus.State.NOT_INSTALLED ? 0.55f : 1f);
        return row;
    }

    /** 无真实应用图标时的兜底徽标：取标题首字 */
    static String badgeLabel(String title) {
        if (title == null || title.isEmpty()) return "?";
        String t = title.trim();
        return t.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    /** 安装状态优先：未安装 / 无法确认单独标注，绝不与「正在保护」混淆 */
    private String stateText(InstallStatus.State state, boolean scopeKnown, boolean inScope) {
        if (state == InstallStatus.State.NOT_INSTALLED) return Ui.T("未安装");
        if (state == InstallStatus.State.UNKNOWN) return Ui.T("安装状态无法确认");
        if (!scopeKnown) return Ui.T("作用域未知");
        return inScope ? Ui.T("正在保护") : Ui.T("未加入作用域");
    }

    private int stateColor(InstallStatus.State state, boolean scopeKnown, boolean inScope) {
        if (state != InstallStatus.State.INSTALLED) return ui.sub;
        if (!scopeKnown) return ui.sub;
        return inScope ? Ui.ACCENT : ui.sub;
    }
}
