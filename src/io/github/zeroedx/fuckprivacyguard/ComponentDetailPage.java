package io.github.zeroedx.fuckprivacyguard;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 组件二级页面：显示单个系统组件（{@link Catalog.Group}）的全部开关。
 *
 * 分组标题、说明与开关行沿用分类页原有结构，开关直接写配置；组总开关等同该应用在
 * LSPosed 的作用域开关，点击只上报目标状态、等作用域真的变了再切换。顶部提供
 * 「返回」与组件级 / 全部重启入口，并明确提示改动开关后需重启目标进程。
 */
final class ComponentDetailPage extends Page {

    private final Catalog.Group group;

    ComponentDetailPage(MainActivity host, Ui ui, Catalog.Group group) {
        super(host, ui);
        this.group = group;
    }

    @Override
    String title() {
        return group.title;
    }

    @Override
    PgIcon.Glyph glyph() {
        return PgIcon.Glyph.GRID;
    }

    @Override
    View build() {
        final Catalog.Group g = group;
        final InstallStatus.State state = host.installState(g.pkg);
        final boolean scopeKnown = ModuleState.I.scopeKnown();

        LinearLayout root = ui.column();
        root.addView(backRow());

        /* 组件信息头卡 */
        LinearLayout head = ui.card();
        head.addView(componentHeader(g, state, scopeKnown));
        if (g.note != null && !g.note.isEmpty()) {
            TextView note = ui.text(g.note, 12, ui.sub);
            note.setLineSpacing(0f, 1.2f);
            note.setPadding(0, ui.dp(6), 0, ui.dp(8));
            head.addView(note);
        }
        if (state == InstallStatus.State.NOT_INSTALLED) {
            TextView warn = ui.text(Ui.T("未安装：该组件当前不在本机，开关不会生效"), 12, ui.sub);
            warn.setPadding(0, 0, 0, ui.dp(8));
            head.addView(warn);
        } else if (state == InstallStatus.State.UNKNOWN) {
            TextView warn = ui.text(Ui.T("安装状态无法确认：可能是系统组件或可见性受限，开关仍会按配置下发"),
                    12, ui.sub);
            warn.setPadding(0, 0, 0, ui.dp(8));
            head.addView(warn);
        }
        root.addView(head);

        /* 作用域开关：与下方隐私功能开关明确区分 */
        root.addView(ui.sectionRow(Ui.T("作用域"),
                Ui.T("决定本模块是否注入该组件进程")));
        LinearLayout scopeCard = ui.card();
        scopeCard.addView(scopeRow(g, scopeKnown));
        final TextView scopeHint = ui.text(Ui.T("未在作用域，点此加入"), 13, Ui.ACCENT);
        scopeHint.setPadding(ui.dp(4), 0, 0, ui.dp(10));
        scopeHint.setVisibility(scopeKnown && !ModuleState.I.inScope(g.pkg)
                ? View.VISIBLE : View.GONE);
        scopeHint.setOnClickListener(v -> host.ensureInScope(g));
        scopeCard.addView(scopeHint);
        root.addView(scopeCard);

        /* 该组件的全部隐私功能开关 */
        root.addView(ui.sectionRow(Ui.T("隐私功能"),
                Ui.T("开关打开 = 拦截该行为，关闭 = 放行")));

        LinearLayout card = ui.card();
        for (Catalog.Item it : g.items) {
            if (card.getChildCount() > 0) card.addView(ui.divider());
            card.addView(itemRow(g, it, scopeKnown));
        }
        root.addView(card);

        /* 重启入口 */
        root.addView(ui.sectionRow(Ui.T("重启"), Ui.T("开关改动后需重启目标进程才生效")));
        LinearLayout rebootCard = ui.card();
        LinearLayout reboot = ui.row();
        reboot.setPadding(0, ui.dp(8), 0, ui.dp(8));
        TextView rebootOne = (TextView) host.bulkButton(Ui.T("重启") + " " + g.title,
                () -> host.restartApp(g.pkg));
        // 只有「确认未安装」才禁用；「无法确认」照常可点（重启走 root，与可见性无关）
        boolean canRestart = state != InstallStatus.State.NOT_INSTALLED;
        rebootOne.setEnabled(canRestart);
        rebootOne.setAlpha(canRestart ? 1f : 0.4f);
        reboot.addView(rebootOne);
        reboot.addView(host.bulkButton(Ui.T("全部重启"), host::restartAll));
        rebootCard.addView(reboot);
        root.addView(rebootCard);

        TextView tip = ui.text(Ui.T("改动任一开关后，需重启目标进程才生效"), 12, ui.sub);
        tip.setPadding(ui.dp(4), ui.dp(2), ui.dp(4), 0);
        root.addView(tip);
        return root;
    }

    private View backRow() {
        LinearLayout top = ui.row();
        top.setPadding(0, 0, 0, ui.dp(4));
        android.widget.ImageView chevron = ui.icon(PgIcon.Glyph.ARROW, Ui.ACCENT, 16);
        chevron.setRotation(180f);
        LinearLayout back = ui.row();
        back.setPadding(ui.dp(8), ui.dp(8), ui.dp(12), ui.dp(8));
        back.setBackground(ui.pressBackground());
        back.addView(chevron);
        TextView label = ui.text(Ui.T("返回"), 14, Ui.ACCENT);
        label.setPadding(ui.dp(4), 0, 0, 0);
        back.addView(label);
        back.setOnClickListener(v -> host.closeDetail());
        top.addView(back);
        return top;
    }

    /** 组件标题行：徽标 + 名称 + 包名 + 安装 / 作用域状态 chip */
    private View componentHeader(final Catalog.Group g, InstallStatus.State state, boolean scopeKnown) {
        boolean installed = state == InstallStatus.State.INSTALLED;
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(12), 0, ui.dp(4));

        android.widget.ImageView badge = ui.componentIcon(g.pkg, CategoriesPage.badgeLabel(g.title), 48);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48));
        blp.rightMargin = ui.dp(14);
        blp.gravity = Gravity.CENTER_VERTICAL;
        row.addView(badge, blp);

        LinearLayout texts = ui.column();
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        texts.addView(ui.title(g.title));

        TextView pkg = ui.text(g.pkg, 13, ui.sub);
        pkg.setMaxLines(1);
        pkg.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pkg.setPadding(0, ui.dp(2), 0, 0);
        texts.addView(pkg);

        boolean inScope = scopeKnown && ModuleState.I.inScope(g.pkg);
        LinearLayout chipWrap = ui.row();
        chipWrap.setPadding(0, ui.dp(6), 0, 0);
        chipWrap.addView(ui.chip(stateText(state, scopeKnown, inScope),
                installed && inScope ? Ui.ACCENT : ui.sub,
                installed && inScope ? ui.accentSoft : ui.chipBg));
        texts.addView(chipWrap);
        row.addView(texts);
        return row;
    }

    /** 组总开关：等同 LSPosed 作用域开关，点击只上报目标状态，等作用域回填后再切换 */
    private View scopeRow(final Catalog.Group g, boolean scopeKnown) {
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(56));
        row.setPadding(0, ui.dp(9), 0, ui.dp(9));

        LinearLayout texts = ui.column();
        texts.setPadding(0, 0, ui.dp(12), 0);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        texts.addView(ui.title(Ui.T("启用该组件")));
        TextView d = ui.text(scopeKnown && ModuleState.I.inScope(g.pkg)
                ? Ui.T("正在保护") : Ui.T("未加入作用域"), 12, ui.sub);
        d.setPadding(0, ui.dp(2), 0, 0);
        texts.addView(d);
        row.addView(texts);

        PgSwitch sw = ui.makeSwitch(Config.get(host, g.key, true), true);
        sw.setOnToggleRequest(target -> {
            if (host.updatingSwitch()) return;
            if (target) host.requestAdd(g.pkg, g);
            else host.requestRemove(g.pkg, g);
        });
        row.addView(sw);
        return row;
    }

    private View itemRow(final Catalog.Group g, final Catalog.Item it, boolean scopeKnown) {
        LinearLayout row = ui.row();
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
        return row;
    }

    private String stateText(InstallStatus.State state, boolean scopeKnown, boolean inScope) {
        if (state == InstallStatus.State.NOT_INSTALLED) return Ui.T("未安装");
        if (state == InstallStatus.State.UNKNOWN) return Ui.T("安装状态无法确认");
        if (!scopeKnown) return Ui.T("作用域未知");
        return inScope ? Ui.T("正在保护") : Ui.T("未加入作用域");
    }
}
