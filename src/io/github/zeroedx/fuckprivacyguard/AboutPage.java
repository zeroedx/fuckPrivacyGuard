package io.github.zeroedx.fuckprivacyguard;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 关于页：品牌、版本、项目链接、原作者致谢与许可证。
 *
 * 保留上游项目署名与 GPL-3.0 说明；本仓库是 fuckPrivacyGuard 的独立分支。
 */
final class AboutPage extends Page {

    AboutPage(MainActivity host, Ui ui) {
        super(host, ui);
    }

    @Override
    String title() {
        return Ui.T("关于");
    }

    @Override
    PgIcon.Glyph glyph() {
        return PgIcon.Glyph.INFO;
    }

    @Override
    View build() {
        LinearLayout root = ui.column();
        root.addView(ui.pageTitle(title()));

        LinearLayout brand = ui.column();
        brand.setGravity(Gravity.CENTER_HORIZONTAL);
        brand.setPadding(0, ui.dp(16), 0, ui.dp(12));
        ImageView logo = new ImageView(host);
        android.graphics.drawable.Drawable icon = ui.icon("brand_icon");
        if (icon != null) logo.setImageDrawable(icon);
        logo.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(72), ui.dp(72)));
        brand.addView(logo);
        TextView name = ui.text("fuckPrivacyGuard", 20, ui.text);
        name.setTypeface(null, Typeface.BOLD);
        name.setPadding(0, ui.dp(8), 0, 0);
        brand.addView(name);
        brand.addView(ui.text("v" + Config.VERSION, 12, ui.sub));
        root.addView(brand);

        root.addView(ui.sectionRow(Ui.T("项目"), null));
        LinearLayout links = ui.card();
        links.addView(ui.linkRow(Ui.T("GitHub 仓库"), "zeroedx/fuckPrivacyGuard",
                () -> host.openUrl(Catalog.URL_REPO)));
        links.addView(ui.line());
        links.addView(ui.linkRow(Ui.T("LSPosed 模块页"), "modules.lsposed.org",
                () -> host.openUrl(Catalog.URL_MODULE)));
        links.addView(ui.line());
        links.addView(ui.linkRow(Ui.T("问题反馈"), "Issues",
                () -> host.openUrl(Catalog.URL_ISSUES)));
        links.addView(ui.line());
        links.addView(ui.linkRow(Ui.T("上游项目"), "vivibibi30/hyperpguard",
                () -> host.openUrl(Catalog.URL_UPSTREAM)));
        links.addView(ui.line());
        links.addView(ui.infoRow(Ui.T("版本"), "v" + Config.VERSION));
        links.addView(ui.line());
        links.addView(ui.linkRow(Ui.T("检查更新"), "GitHub Releases",
                () -> host.dialogs().update()));
        root.addView(links);

        root.addView(ui.sectionRow(Ui.T("开源与致谢"), null));
        LinearLayout credit = ui.card();
        TextView c1 = ui.text(Ui.T("本项目基于 vivibibi30/hyperpguard 的 GPL-3.0 源码二次开发，"
                + "保留原作者版权与出处致谢。"), 12, ui.text);
        c1.setPadding(0, ui.dp(12), 0, ui.dp(6));
        c1.setLineSpacing(0f, 1.25f);
        credit.addView(c1);
        TextView c2 = ui.text("libxposed API / service（Apache-2.0）；"
                + "hyperos-dm-report（文档 CC BY 4.0）。", 12, ui.sub);
        c2.setPadding(0, 0, 0, ui.dp(6));
        c2.setLineSpacing(0f, 1.25f);
        credit.addView(c2);
        credit.addView(ui.line());
        credit.addView(ui.linkRow(Ui.T("许可证"), "GPL-3.0",
                () -> host.openUrl(Catalog.URL_UPSTREAM + "/blob/main/LICENSE")));
        credit.addView(ui.line());
        LinearLayout lic = ui.clickableRow(credit, Ui.T("免责声明 / 使用条款"), null, null);
        lic.setOnClickListener(v -> host.dialogs().disclaimer());
        root.addView(credit);

        return root;
    }
}
