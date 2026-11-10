package io.github.zeroedx.fuckprivacyguard;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 搜索页：按名称、说明、包名和配置 Key 过滤。
 *
 * 索引用内存表，条目来自 {@link Catalog}，不引入任何搜索库。无结果显示空状态提示。
 */
final class SearchPage extends Page {

    SearchPage(MainActivity host, Ui ui) {
        super(host, ui);
    }

    @Override
    String title() {
        return Ui.T("搜索");
    }

    @Override
    View build() {
        LinearLayout root = ui.column();

        EditText input = new EditText(host);
        input.setHint(Ui.T("搜索名称、说明、包名或配置项"));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setTextColor(ui.text);
        input.setHintTextColor(ui.sub);
        input.setSingleLine(true);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(ui.night ? 0xFF1C1C1E : 0xFFFFFFFF);
        bg.setCornerRadius(ui.dp(16));
        input.setBackground(bg);
        input.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.bottomMargin = ui.dp(12);
        input.setLayoutParams(ilp);
        root.addView(input);

        final LinearLayout results = ui.column();
        root.addView(results);

        render(results, "");
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                render(results, s.toString().trim().toLowerCase(Locale.ROOT));
            }
        });
        return root;
    }

    private void render(LinearLayout results, String q) {
        results.removeAllViews();

        if (q.isEmpty()) {
            results.addView(ui.sectionRow(Ui.T("全部分组"), Catalog.GROUPS.length + " 组"));
            LinearLayout card = ui.card();
            boolean first = true;
            for (Catalog.Group g : Catalog.GROUPS) {
                if (!first) card.addView(ui.line());
                first = false;
                card.addView(ui.linkRow(g.title, g.pkg, host::showCategories));
            }
            results.addView(card);
            return;
        }

        List<String[]> hits = new ArrayList<>();
        for (Catalog.Group g : Catalog.GROUPS) {
            if (match(g.title, g.pkg, q)) hits.add(new String[]{g.title, g.pkg, "group"});
            for (Catalog.Item it : g.items) {
                if (match(it.name, it.desc, it.key, q) || match(g.title, q)) {
                    hits.add(new String[]{it.name, g.title + " · " + it.desc, "item"});
                }
            }
        }

        if (hits.isEmpty()) {
            LinearLayout card = ui.card();
            TextView empty = ui.text(Ui.T("没有匹配的条目"), 14, ui.sub);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, ui.dp(24), 0, ui.dp(24));
            card.addView(empty);
            results.addView(card);
            return;
        }

        results.addView(ui.sectionRow(Ui.T("搜索结果"), hits.size() + " 条"));
        LinearLayout card = ui.card();
        boolean first = true;
        for (String[] h : hits) {
            if (!first) card.addView(ui.line());
            first = false;
            if ("group".equals(h[2])) {
                card.addView(ui.linkRow(h[0], h[1], host::showCategories));
            } else {
                LinearLayout r = ui.column();
                r.setPadding(ui.dp(4), ui.dp(9), ui.dp(4), ui.dp(9));
                r.addView(ui.title(h[0]));
                TextView d = ui.text(h[1], 12, ui.sub);
                d.setPadding(0, ui.dp(2), 0, 0);
                r.addView(d);
                card.addView(r);
            }
        }
        results.addView(card);
    }

    private static boolean match(String... fields) {
        String q = fields[fields.length - 1];
        for (int i = 0; i < fields.length - 1; i++) {
            String f = fields[i];
            if (f != null && f.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }
}
