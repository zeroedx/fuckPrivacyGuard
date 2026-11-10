package io.github.vivibibi30.hyperpguard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Miuix 风格界面的公共构建件：色板、卡片、分组标题、开关行、弹窗着色。
 *
 * 全部自绘，不依赖任何第三方 UI 库。每个页面用 {@link #palette()} 取一份当前深浅色的色板，
 * 构建时统一从这里取色，深浅色切换时整页重建即可。
 */
final class Ui {

    static final int ACCENT = 0xFF3478F6;

    private static Context app;
    private static float density;

    int bg, card, text, sub, line, trackOff, pressed;
    boolean night;

    private Ui() {
    }

    /** 在 Activity.onCreate 里调用一次，之后所有页面共享同一份密度与 Context */
    static void attach(Context c) {
        app = c.getApplicationContext();
        density = app.getResources().getDisplayMetrics().density;
    }

    static Context ctx() {
        return app;
    }

    static float density() {
        return density;
    }

    static int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    /** 文案查表：中文原文，英文环境返回英文 */
    static String T(String zh) {
        return I18n.t(zh);
    }

    /** 按系统当前深浅色取色板 */
    static Ui palette() {
        Configuration c = app.getResources().getConfiguration();
        return palette((c.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
    }

    static Ui palette(boolean night) {
        Ui u = new Ui();
        u.setNight(night);
        return u;
    }

    void setNight(boolean night) {
        this.night = night;
        if (night) {
            bg = 0xFF000000;
            card = 0xFF1C1C1E;
            text = 0xFFFFFFFF;
            sub = 0xFF8E8E93;
            line = 0xFF2C2C2E;
            trackOff = 0xFF3A3A3C;
            pressed = 0xFF3A3A3C;
        } else {
            bg = 0xFFF2F3F5;
            card = 0xFFFFFFFF;
            text = 0xFF1A1A1A;
            sub = 0xFF8A8A8E;
            line = 0xFFEFEFF0;
            trackOff = 0xFFE9E9EB;
            pressed = 0xFFE5E5E5;
        }
    }

    /* ---------------- 基础控件 ---------------- */

    TextView text(String s, float sp, int color) {
        TextView tv = new TextView(app);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        return tv;
    }

    TextView title(String s) {
        return text(s, 16, text);
    }

    TextView caption(String s) {
        return text(s, 12, sub);
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(app);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(app);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** 大圆角卡片 */
    LinearLayout card() {
        LinearLayout c = new LinearLayout(app);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(card);
        bgd.setCornerRadius(dp(20));
        c.setBackground(bgd);
        c.setPadding(dp(16), dp(4), dp(16), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    /** 分组行：左侧小标题，右侧可选说明 */
    View sectionRow(String title, String right) {
        LinearLayout r = row();
        r.setGravity(Gravity.BOTTOM);
        r.setMinimumHeight(dp(35));
        r.setPadding(dp(12), dp(16), dp(12), dp(8));
        TextView left = text(title, 14, sub);
        r.addView(left, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (right != null && !right.isEmpty()) {
            TextView rr = text(right, 12, sub);
            rr.setMaxLines(1);
            rr.setEllipsize(android.text.TextUtils.TruncateAt.END);
            r.addView(rr);
        }
        return r;
    }

    TextView sectionTitle(String s) {
        TextView tv = text(s, 14, sub);
        tv.setMinHeight(dp(35));
        tv.setGravity(Gravity.BOTTOM);
        tv.setPadding(dp(12), dp(16), 0, dp(8));
        return tv;
    }

    /** 列表分隔线：0.75dp */
    View divider() {
        View v = new View(app);
        v.setBackgroundColor(line);
        int h = Math.max(1, Math.round(density * 0.75f));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));
        return v;
    }

    View line() {
        View v = new View(app);
        v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        v.setBackgroundColor(line);
        return v;
    }

    TextView groupNote(String s) {
        TextView tv = text(s, 11, sub);
        tv.setPadding(0, 0, 0, dp(8));
        return tv;
    }

    /** 自绘开关，颜色跟随色板 */
    PgSwitch makeSwitch(boolean checked, boolean enabled) {
        PgSwitch sw = new PgSwitch(app, ACCENT, trackOff);
        sw.setLayoutParams(new LinearLayout.LayoutParams(dp(49), dp(28)));
        sw.setChecked(checked);
        sw.setEnabled(enabled);
        return sw;
    }

    /** 标题 + 副标题 + 开关的一行；返回该开关 */
    PgSwitch switchRow(LinearLayout parent, String name, String desc,
                       boolean checked, boolean enabled,
                       CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout r = row();
        r.setMinimumHeight(dp(56));
        r.setPadding(0, dp(9), 0, dp(9));
        LinearLayout texts = column();
        texts.setPadding(0, 0, dp(12), 0);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        texts.addView(title(name));
        if (desc != null && !desc.isEmpty()) {
            TextView d = text(desc, 12, sub);
            d.setPadding(0, dp(2), 0, 0);
            texts.addView(d);
        }
        r.addView(texts);
        PgSwitch sw = makeSwitch(checked, enabled);
        if (listener != null) sw.setOnCheckedChangeListener(listener);
        r.addView(sw);
        parent.addView(r);
        return sw;
    }

    /** 可点击的整行（标题 + 副标题），右侧可放一个强调色文字 */
    LinearLayout clickableRow(LinearLayout parent, String name, String desc, String action) {
        LinearLayout r = column();
        r.setMinimumHeight(dp(56));
        r.setPadding(dp(4), dp(9), dp(4), dp(9));
        r.setClickable(true);
        r.setBackground(pressBackground());
        LinearLayout top = row();
        TextView t1 = title(name);
        top.addView(t1, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (action != null && !action.isEmpty()) {
            TextView a = text(action, 13, ACCENT);
            a.setTag("accent");
            top.addView(a);
        }
        r.addView(top);
        if (desc != null && !desc.isEmpty()) {
            TextView d = text(desc, 12, sub);
            d.setPadding(0, dp(2), 0, 0);
            r.addView(d);
        }
        parent.addView(r);
        return r;
    }

    ViewGroup infoRow(String label, String value) {
        LinearLayout r = row();
        r.setMinimumHeight(dp(56));
        r.setPadding(0, dp(9), 0, dp(9));
        TextView name = text(label, 14, text);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(name);
        r.addView(text(value, 12, sub));
        return r;
    }

    ViewGroup linkRow(String label, String value, final Runnable action) {
        LinearLayout r = row();
        r.setMinimumHeight(dp(56));
        r.setPadding(dp(4), dp(9), dp(4), dp(9));
        r.setClickable(true);
        r.setBackground(pressBackground());
        TextView name = text(label, 14, text);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(name);
        r.addView(text(value, 12, ACCENT));
        r.setOnClickListener(v -> action.run());
        return r;
    }

    /** 按下高亮：浅色比背景深、深色比背景浅 */
    Drawable pressBackground() {
        GradientDrawable on = new GradientDrawable();
        on.setColor(pressed);
        on.setCornerRadius(dp(12));
        GradientDrawable off = new GradientDrawable();
        off.setColor(0x00000000);
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, on);
        d.addState(new int[]{}, off);
        return d;
    }

    Drawable icon(String name) {
        try {
            int id = app.getResources().getIdentifier(name, "drawable", app.getPackageName());
            if (id != 0) return app.getResources().getDrawable(id, null);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /* ---------------- 弹窗 ---------------- */

    /** 圆角卡片底 + 按钮强调色 + 正文/附注分色，深浅色切换时可重新调用 */
    void styleDialog(AlertDialog d) {
        if (d == null || d.getWindow() == null) return;
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(card);
        bgd.setCornerRadius(dp(20));
        d.getWindow().setBackgroundDrawable(bgd);
        paintDialogText(d.getWindow().getDecorView());
    }

    private void paintDialogText(View v) {
        if (v == null) return;
        if (v instanceof EditText) {
            EditText et = (EditText) v;
            et.setTextColor(text);
            et.setHintTextColor(sub);
            GradientDrawable field = new GradientDrawable();
            field.setColor(night ? 0xFF2C2C2E : 0xFFF2F3F5);
            field.setCornerRadius(dp(12));
            et.setBackground(field);
        } else if (v instanceof Button) {
            ((Button) v).setTextColor(ACCENT);
        } else if (v instanceof TextView) {
            TextView tv = (TextView) v;
            if (!"accent".equals(tv.getTag())) {
                float sp = tv.getTextSize() / app.getResources().getDisplayMetrics().scaledDensity;
                tv.setTextColor(sp <= 12.5f ? sub : text);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) paintDialogText(g.getChildAt(i));
        }
    }

    /** 免责声明排版：标题、版本行、章节、列表项分别上样式 */
    CharSequence styleDisclaimer(String txt) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        boolean en = I18n.english();
        boolean first = true;
        for (String raw : txt.split("\n", -1)) {
            String ln = raw.trim();
            if (ln.isEmpty()) {
                if (sb.length() == 0) continue;
                if (sb.length() >= 2 && sb.charAt(sb.length() - 2) == '\n') continue;
                sb.append('\n');
                continue;
            }
            int start = sb.length();
            sb.append(ln);
            int end = sb.length();
            sb.append('\n');
            if (first) {
                if (!en) {
                    sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, end,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new android.text.style.RelativeSizeSpan(1.15f), start, end,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                first = false;
            } else if (!en && ln.startsWith("版本 ")) {
                sb.setSpan(new android.text.style.RelativeSizeSpan(0.85f), start, end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new android.text.style.ForegroundColorSpan(sub), start, end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (!en && ln.matches("^[一二三四五六七八九十]+、.*")) {
                sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (ln.matches("^\\d+\\)\\s.*") || (en && ln.startsWith("- "))) {
                sb.setSpan(new android.text.style.LeadingMarginSpan.Standard(0, dp(18)),
                        start, end + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }
}
