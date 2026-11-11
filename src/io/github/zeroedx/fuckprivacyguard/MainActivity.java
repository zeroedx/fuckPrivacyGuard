package io.github.zeroedx.fuckprivacyguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 宿主 Activity：负责导航、滚动、Insets、深浅色与 LSPosed 连接。
 *
 * 具体页面各自成类（{@link HomePage} 等），本类不再堆叠界面代码。
 * 拦截逻辑、配置协议、作用域操作与 root 通道保持与原实现一致。
 */
public class MainActivity extends Activity {

    private static final String TAG = "fuckPrivacyGuard";
    private static final String LAUNCHER_ALIAS = "io.github.zeroedx.fuckprivacyguard.Launcher";
    private static final int DISCLAIMER_VER = 14;
    /** 分类页在底部导航中的下标；组件二级页挂在该页下 */
    private static final int TAB_CATEGORIES = 1;

    private Ui ui;
    private Dialogs dialogs;
    private Page[] pages;
    private TextView[] navTabs;
    private android.widget.ImageView[] navIcons;
    private LinearLayout[] navItems;
    private ScrollView scroll;
    private LinearLayout contentRoot;
    private LinearLayout navBar;

    /** 非空时「分类」页显示该组件的二级页面 */
    private Catalog.Group detailGroup;

    private int current = 0;
    private int appliedNight = -1;
    private boolean updatingSwitch = false;
    private AlertDialog shownDialog;
    private int restoreScrollY;
    private final Handler h = new Handler(Looper.getMainLooper());

    private int bindRetry = 0;
    private boolean autoRequested = false;
    private Runnable pendingAction;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.attach(this);
        applyPalette(effectiveNight());
        applyWindowChrome();
        dialogs = new Dialogs(this, ui);
        ModuleState.I.onChanged = () -> h.post(this::refreshCurrent);
        restoreScrollY = b == null ? 0 : b.getInt("scrollY", 0);
        current = b == null ? 0 : b.getInt("page", 0);
        bindServiceHelper();
        buildUi();
        try {
            registerReceiver(themeReceiver,
                    new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
        } catch (Throwable t) {
            android.util.Log.w(TAG, "theme receiver fail", t);
        }
        maybeShowDisclaimer();
    }

    /* ---------------- 深浅色 ---------------- */

    String themeMode() {
        try {
            return getSharedPreferences(SettingsPage.UI_PREFS, MODE_PRIVATE)
                    .getString(SettingsPage.K_THEME, SettingsPage.THEME_AUTO);
        } catch (Throwable t) {
            return SettingsPage.THEME_AUTO;
        }
    }

    void setThemeMode(String mode) {
        getSharedPreferences(SettingsPage.UI_PREFS, MODE_PRIVATE)
                .edit().putString(SettingsPage.K_THEME, mode).apply();
    }

    /** 当前应使用的深浅色：显式模式优先，跟随系统时读配置并用 UiModeManager 兜底 */
    private boolean effectiveNight() {
        String mode = themeMode();
        if (SettingsPage.THEME_LIGHT.equals(mode)) return false;
        if (SettingsPage.THEME_DARK.equals(mode)) return true;
        Configuration c = getResources().getConfiguration();
        boolean night = (c.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        try {
            android.app.UiModeManager um = (android.app.UiModeManager) getSystemService(UI_MODE_SERVICE);
            if (um != null) {
                int m = um.getNightMode();
                if (m == android.app.UiModeManager.MODE_NIGHT_YES) night = true;
                else if (m == android.app.UiModeManager.MODE_NIGHT_NO) night = false;
            }
        } catch (Throwable ignored) {
        }
        return night;
    }

    private void applyPalette(boolean night) {
        ui = Ui.palette(night);
        appliedNight = night ? 1 : 0;
        try {
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ui.bg));
        } catch (Throwable ignored) {
        }
    }

    private void applyWindowChrome() {
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(0);
            getWindow().setNavigationBarColor(0);
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                getWindow().setDecorFitsSystemWindows(false);
            } else {
                int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                if (!ui.night) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                getWindow().getDecorView().setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        // 跟随系统模式下才需要响应；显式模式保持不变
        if (!SettingsPage.THEME_AUTO.equals(themeMode())) return;
        int y = scroll == null ? 0 : scroll.getScrollY();
        rebuildUi();
        if (y > 0 && scroll != null) scroll.post(() -> scroll.scrollTo(0, y));
        if (shownDialog != null && shownDialog.isShowing()) ui.styleDialog(shownDialog);
    }

    private final BroadcastReceiver themeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            syncThemeIfNeeded();
        }
    };

    /** 系统深浅色变了就重建一次；onConfigurationChanged 没回调时靠它兜底 */
    private void syncThemeIfNeeded() {
        if (!SettingsPage.THEME_AUTO.equals(themeMode())) return;
        int want = effectiveNight() ? 1 : 0;
        if (appliedNight == -1 || want == appliedNight) return;
        int y = scroll == null ? 0 : scroll.getScrollY();
        rebuildUi();
        if (y > 0 && scroll != null) scroll.post(() -> scroll.scrollTo(0, y));
        if (shownDialog != null && shownDialog.isShowing()) ui.styleDialog(shownDialog);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) return;
        syncThemeIfNeeded();
        h.postDelayed(this::syncThemeIfNeeded, 400);
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(themeReceiver);
        } catch (Throwable ignored) {
        }
        ModuleState.I.onChanged = () -> {
        };
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (scroll != null) out.putInt("scrollY", scroll.getScrollY());
        out.putInt("page", current);
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncThemeIfNeeded();
        if (ModuleState.I.connected()) syncFromService();
        else bindServiceHelper();
    }

    /* ---------------- 界面骨架 ---------------- */

    /** 整页重建（深浅色切换或主题模式变化时） */
    void rebuildUi() {
        applyPalette(effectiveNight());
        applyWindowChrome();
        dialogs = new Dialogs(this, ui);
        buildUi();
    }

    private void buildUi() {
        // 页面与色板绑定，深浅色变化时随 buildUi 一起重建
        pages = new Page[]{
                new HomePage(this, ui),
                new CategoriesPage(this, ui),
                new DiagnosticsPage(this, ui),
                new SettingsPage(this, ui),
                new AboutPage(this, ui),
        };
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(ui.bg);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ui.bg);
        outer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        contentRoot = new LinearLayout(this);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        contentRoot.setPadding(ui.dp(16), ui.dp(20) + statusBarHeight(), ui.dp(16), ui.dp(24));
        scroll.addView(contentRoot, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View navDivider = new View(this);
        navDivider.setBackgroundColor(ui.line);
        outer.addView(navDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, ui.dp(0.75f))));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setBackgroundColor(ui.card);
        navBar.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6) + navBarHeight());
        outer.addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        navTabs = new TextView[pages.length];
        navIcons = new android.widget.ImageView[pages.length];
        navItems = new LinearLayout[pages.length];
        for (int i = 0; i < pages.length; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, ui.dp(6), 0, ui.dp(6));
            item.setBackground(ui.rounded(0x00000000, 12));
            item.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            item.setOnClickListener(v -> {
                // 在组件二级页面时再点「分类」标签，回到组件列表
                if (idx == TAB_CATEGORIES && detailOpen()) closeDetail();
                else showPage(idx);
            });
            android.widget.ImageView ic = ui.icon(pages[i].glyph(), ui.sub, 22);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22));
            ilp.gravity = Gravity.CENTER_HORIZONTAL;
            item.addView(ic, ilp);
            TextView tab = new TextView(this);
            tab.setText(pages[i].title());
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, ui.dp(3), 0, 0);
            item.addView(tab);
            navTabs[i] = tab;
            navIcons[i] = ic;
            navItems[i] = item;
            navBar.addView(item);
        }

        setContentView(outer);
        I18n.apply(outer);
        showPage(current);
        applyBarAppearance();
        if (restoreScrollY > 0 && scroll != null) {
            scroll.post(() -> scroll.scrollTo(0, restoreScrollY));
        }
    }

    private void showPage(int i) {
        if (i < 0 || i >= pages.length) return;
        // 离开分类页就退出组件二级页面，重新进入时回到组件列表
        if (i != TAB_CATEGORIES) detailGroup = null;
        current = i;
        contentRoot.removeAllViews();
        contentRoot.addView(pageContent(i));
        pages[i].onShow();
        I18n.apply(contentRoot);
        updateNavHighlight();
        if (scroll != null) scroll.scrollTo(0, 0);
    }

    /** 分类页在选中某个组件时显示其二级页面，否则显示组件列表 */
    private View pageContent(int i) {
        if (i == TAB_CATEGORIES && detailGroup != null) {
            return new ComponentDetailPage(this, ui, detailGroup).build();
        }
        return pages[i].build();
    }

    /** 状态变化后刷新当前页，保留滚动位置 */
    private void refreshCurrent() {
        if (contentRoot == null) return;
        int y = scroll == null ? 0 : scroll.getScrollY();
        contentRoot.removeAllViews();
        contentRoot.addView(pageContent(current));
        I18n.apply(contentRoot);
        if (scroll != null) scroll.post(() -> scroll.scrollTo(0, y));
    }

    /** 打开组件二级页面（仅限分类页） */
    void openComponent(Catalog.Group g) {
        if (current != TAB_CATEGORIES) showPage(TAB_CATEGORIES);
        detailGroup = g;
        contentRoot.removeAllViews();
        contentRoot.addView(pageContent(current));
        I18n.apply(contentRoot);
        if (scroll != null) scroll.scrollTo(0, 0);
    }

    /** 组件二级页面是否处于打开状态 */
    boolean detailOpen() {
        return detailGroup != null && current == TAB_CATEGORIES;
    }

    /** 返回组件列表；返回 true 表示确实消费了本次返回 */
    boolean closeDetail() {
        if (!detailOpen()) return false;
        detailGroup = null;
        contentRoot.removeAllViews();
        contentRoot.addView(pageContent(current));
        I18n.apply(contentRoot);
        if (scroll != null) scroll.scrollTo(0, 0);
        return true;
    }

    @Override
    public void onBackPressed() {
        // 组件二级页面：先返回组件列表；其余情况交给系统默认（退出 / 上级导航）
        if (closeDetail()) return;
        super.onBackPressed();
    }

    private void updateNavHighlight() {
        if (navTabs == null) return;
        for (int i = 0; i < navTabs.length; i++) {
            boolean sel = i == current;
            navTabs[i].setTextColor(sel ? Ui.ACCENT : ui.sub);
            navTabs[i].setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
            if (navIcons != null && navIcons[i] != null) {
                ((PgIcon) navIcons[i].getDrawable()).setColor(sel ? Ui.ACCENT : ui.sub);
            }
            if (navItems != null && navItems[i] != null) {
                navItems[i].setBackground(ui.rounded(sel ? ui.accentSoft : 0x00000000, 12));
            }
        }
    }

    private void applyBarAppearance() {
        try {
            android.view.WindowInsetsController c =
                    getWindow().getDecorView().getWindowInsetsController();
            if (c != null) {
                int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(ui.night ? 0 : light, light);
            }
        } catch (Throwable ignored) {
        }
    }

    private int statusBarHeight() {
        try {
            int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) return getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
        }
        return ui.dp(24);
    }

    private int navBarHeight() {
        try {
            int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (id > 0) return getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /* ---------------- 供页面调用 ---------------- */

    Ui ui() {
        return ui;
    }

    Dialogs dialogs() {
        return dialogs;
    }

    boolean updatingSwitch() {
        return updatingSwitch;
    }

    void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Throwable t) {
            Toast.makeText(this, "没有可打开链接的应用", Toast.LENGTH_SHORT).show();
        }
    }

    /** 批量操作行里的一个按钮 */
    View bulkButton(final String label, final Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(Ui.ACCENT);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(ui.pressBackground());
        tv.setPadding(0, ui.dp(10), 0, ui.dp(10));
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tv.setOnClickListener(v -> action.run());
        return tv;
    }

    void trackDialog(AlertDialog d) {
        shownDialog = d;
        d.setOnDismissListener(x -> {
            if (shownDialog == d) shownDialog = null;
        });
    }

    /* ---------------- 作用域 ---------------- */

    private void bindServiceHelper() {
        try {
            XposedServiceHelper.registerListener(serviceListener);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "registerListener fail", t);
        }
    }

    private final XposedServiceHelper.OnServiceListener serviceListener =
            new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(XposedService service) {
                    ModuleState.I.xposed = service;
                    Config.bindRemote(ModuleState.I.remotePrefs());
                    bindRetry = 0;
                    android.util.Log.i(TAG, "XposedService bound: "
                            + service.getFrameworkName() + " " + service.getFrameworkVersion());
                    h.post(MainActivity.this::syncFromService);
                    final Runnable pending = pendingAction;
                    pendingAction = null;
                    if (pending != null) h.post(pending);
                }

                @Override
                public void onServiceDied(XposedService service) {
                    ModuleState.I.xposed = null;
                    Config.bindRemote(null);
                    android.util.Log.w(TAG, "XposedService died");
                    bindRetry++;
                    if (bindRetry < 30) h.postDelayed(MainActivity.this::bindServiceHelper, 2000);
                }
            };

    private List<String> safeScope() {
        if (!ModuleState.I.connected()) return null;
        try {
            List<String> sc = ModuleState.I.xposed.getScope();
            android.util.Log.i(TAG, "getScope -> " + sc);
            return sc;
        } catch (Throwable t) {
            android.util.Log.w(TAG, "getScope fail", t);
            return null;
        }
    }

    /** 从 LSPosed 读真实作用域，刷新组开关与配置；这是「开关状态跟着框架走」的唯一入口 */
    private void syncFromService() {
        List<String> scope = safeScope();
        ModuleState.I.setScope(scope);
        for (Catalog.Group g : Catalog.GROUPS) {
            if (scope == null) {
                Config.get(this, g.key, true);
            } else {
                Config.uiSetLocal(this, g.key, scope.contains(g.pkg));
            }
        }
        // 组开关可能与通道里的值不同，统一推一次
        Config.pushAll(this);
        if (scope != null && scope.isEmpty() && !autoRequested) {
            autoRequested = true;
            requestAll();
        }
        refreshCurrent();
        ModuleState.I.notifyChanged();
    }

    private void runWhenBound(final Runnable action) {
        if (ModuleState.I.connected()) {
            action.run();
            return;
        }
        pendingAction = action;
        bindServiceHelper();
        Toast.makeText(this, "正在连接 LSPosed…", Toast.LENGTH_SHORT).show();
        h.postDelayed(() -> {
            if (pendingAction == action && !ModuleState.I.connected()) {
                pendingAction = null;
                Toast.makeText(MainActivity.this,
                        "LSPosed 服务未连接，请确认已在 LSPosed 中启用本模块后重开本界面",
                        Toast.LENGTH_LONG).show();
            }
        }, 4000);
    }

    void requestAdd(final String pkg, final Catalog.Group g) {
        if (!ModuleState.I.connected()) {
            runWhenBound(() -> requestAdd(pkg, g));
            return;
        }
        android.util.Log.i(TAG, "requestScope(" + pkg + ") sending");
        ModuleState.I.requestScope(ModuleState.one(pkg), (ok, msg) -> {
            if (ok) {
                Config.uiSetLocal(this, g.key, true);
                toast("已加入作用域：" + msg);
                syncFromService();
            } else {
                showScopeFailure("「" + g.title + "」加入作用域失败", pkg, msg);
                syncFromService();
            }
        });
    }

    void requestRemove(final String pkg, final Catalog.Group g) {
        if (!ModuleState.I.connected()) return;
        ModuleState.I.removeScope(ModuleState.one(pkg), (ok, msg) -> {
            if (ok) {
                Config.uiSetLocal(this, g.key, false);
                toast("已移出作用域：" + pkg);
                syncFromService();
            } else {
                toast("移除失败：" + msg);
            }
        });
    }

    void requestAll() {
        if (!ModuleState.I.connected()) {
            runWhenBound(this::requestAll);
            return;
        }
        final List<String> targets = new ArrayList<>();
        for (Catalog.Group g : Catalog.GROUPS) if (isInstalled(g.pkg)) targets.add(g.pkg);
        if (targets.isEmpty()) {
            toast("没有可加入的目标应用");
            return;
        }
        ModuleState.I.requestScope(targets, (ok, msg) -> {
            if (ok) {
                for (Catalog.Group g : Catalog.GROUPS) {
                    if (isInstalled(g.pkg)) Config.uiSetLocal(this, g.key, true);
                }
                toast("已请求 " + targets.size() + " 个作用域");
                syncFromService();
            } else {
                showScopeFailure("批量加入作用域失败", "（多个应用）", msg);
            }
        });
    }

    void removeAll() {
        if (!ModuleState.I.connected()) return;
        final List<String> all = new ArrayList<>();
        for (Catalog.Group g : Catalog.GROUPS) all.add(g.pkg);
        ModuleState.I.removeScope(all, (ok, msg) -> {
            if (ok) {
                for (Catalog.Group g : Catalog.GROUPS) Config.uiSetLocal(this, g.key, false);
                toast("已移出全部作用域");
                syncFromService();
            } else {
                toast("移除失败：" + msg);
            }
        });
    }

    /** 子项被打开时，如果该应用还没进作用域，顺手请求加入 */
    void ensureInScope(final Catalog.Group g) {
        if (!ModuleState.I.connected()) {
            runWhenBound(() -> ensureInScope(g));
            return;
        }
        if (ModuleState.I.inScope(g.pkg)) return;
        ModuleState.I.requestScope(ModuleState.one(g.pkg), (ok, msg) -> {
            if (ok) {
                Config.uiSetLocal(this, g.key, true);
                syncFromService();
            } else {
                showScopeFailure("「" + g.title + "」加入作用域失败", g.pkg, msg);
            }
        });
    }

    void restartApp(final String pkg) {
        Toast.makeText(this, "正在重启 " + pkg + "（需 root）", Toast.LENGTH_SHORT).show();
        ModuleState.I.restartApp(pkg, (ok, msg) -> toast(msg));
    }

    void restartAll() {
        List<String> sc = safeScope();
        final List<String> pkgs = new ArrayList<>();
        if (sc != null && !sc.isEmpty()) pkgs.addAll(sc);
        else for (Catalog.Group g : Catalog.GROUPS) pkgs.add(g.pkg);
        if (pkgs.isEmpty()) {
            toast("作用域为空");
            return;
        }
        Toast.makeText(this, "正在重启 " + pkgs.size() + " 个应用（需 root）", Toast.LENGTH_SHORT).show();
        ModuleState.I.restartAll(pkgs, (ok, msg) -> toast(msg));
    }

    private void showScopeFailure(String title, String pkg, String message) {
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("包名：" + pkg
                        + "\n\nLSPosed 返回：" + (message == null || message.isEmpty() ? "（无）" : message)
                        + "\n\n可以在 LSPosed 管理器 → 模块 → fuckPrivacyGuard → 作用域 里手动勾选该应用。"
                        + "\n手动勾选后回到本界面，开关会跟着变成开启。")
                .setPositiveButton(Ui.T("知道了"), null)
                .create();
        d.show();
        trackDialog(d);
        ui.styleDialog(d);
    }

    boolean isInstalled(String pkg) {
        return InstallStatus.isInstalled(this, pkg);
    }

    /** 三态：已安装 / 未安装 / 无法确认（可见性受限时绝不谎报未安装） */
    InstallStatus.State installState(String pkg) {
        return InstallStatus.state(this, pkg);
    }

    /* ---------------- 桌面图标 ---------------- */

    boolean launcherIconEnabled() {
        try {
            int st = getPackageManager().getComponentEnabledSetting(
                    new android.content.ComponentName(this, LAUNCHER_ALIAS));
            return st != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (Throwable t) {
            return true;
        }
    }

    void setLauncherIcon(boolean on) {
        try {
            getPackageManager().setComponentEnabledSetting(
                    new android.content.ComponentName(this, LAUNCHER_ALIAS),
                    on ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                       : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
        } catch (Throwable t) {
            Toast.makeText(this, "切换失败：" + t, Toast.LENGTH_LONG).show();
        }
    }

    /* ---------------- 诊断 / 清理日志 ---------------- */

    void showDiagnostics() {
        Toast.makeText(this, "检测中…（需 root）", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("模块版本：").append(Config.VERSION).append('\n');
            sb.append("LSPosed：").append(!ModuleState.I.connected()
                    ? "未连接（重开本界面会自动重连；已注入进程仍以下面的日志为准）"
                    : ModuleState.I.framework()).append('\n');

            List<String> scope = safeScope();
            sb.append("作用域：")
                    .append(scope == null ? "读取失败" : scope.size() + " 个").append("\n\n");

            for (Catalog.Group g : Catalog.GROUPS) {
                boolean in = scope != null && scope.contains(g.pkg);
                sb.append(in ? "[✓] " : "[ ] ").append(g.pkg);
                InstallStatus.State st = InstallStatus.state(MainActivity.this, g.pkg);
                if (st == InstallStatus.State.NOT_INSTALLED) sb.append("（未安装）");
                else if (st == InstallStatus.State.UNKNOWN) sb.append("（安装状态无法确认）");
                sb.append('\n');
            }

            int local = Config.ui(MainActivity.this).getAll().size();
            int remote = 0;
            SharedPreferences rp = ModuleState.I.remotePrefs();
            try {
                if (rp != null) {
                    String raw = rp.getString(Config.REMOTE_KEY, null);
                    if (raw != null && !raw.isEmpty()) {
                        for (String kv : raw.split("\\|")) if (kv.indexOf('=') > 0) remote++;
                    }
                }
            } catch (Throwable ignored) {
            }
            boolean master = Config.get(MainActivity.this, Config.K_MASTER, true);
            sb.append("\n总开关：").append(master ? "已开启" : "已关闭（所有进程都会跳过安装）");
            sb.append("\n本地开关 ").append(local).append(" 项，LSPosed 通道 ")
                    .append(rp == null ? "未连接" : remote + " 项").append("\n");

            sb.append("\nLSPosed 加载记录：\n");
            String lsp = ModuleState.I.execShell(
                    "f=$(ls -t /data/adb/lspd/log/modules_*.log 2>/dev/null | head -1); "
                            + "[ -n \"$f\" ] && tail -n 4000 \"$f\""
                            + " | grep -oE '\\([a-z0-9_.]+\\)\\[io\\.github\\.zeroedx\\.fuckprivacyguard'"
                            + " | sed 's/^(//; s/).*//' | sort -u");
            sb.append(lsp.trim().isEmpty() ? "（日志里没有本模块的加载记录）" : lsp.trim());

            sb.append("\n安装失败记录：\n");
            String fails = ModuleState.I.execShell(
                    "logcat -d -b all -v time | grep -E 'fuckPrivacyGuard(\\( *[0-9]+\\))?: (stage|dispatch|net install|privacy install)' | tail -n 40");
            sb.append(fails.trim().isEmpty() ? "（没有）" : fails.trim());

            sb.append("\n最近 attach 日志：\n");
            String attach = ModuleState.I.execShell(
                    "logcat -d -b all -v time | grep -E 'fuckPrivacyGuard(\\( *[0-9]+\\))?: attach' | tail -n 15");
            sb.append(attach.trim().isEmpty()
                    ? "（logcat 缓冲里没有，长时间运行后会被冲掉）"
                    : attach.trim());

            final String body = sb.toString();
            h.post(() -> dialogs.text("环境自检", body));
        }, "fpg-diagnostics").start();
    }

    void confirmCleanLog() {
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(Ui.T("清空日志缓冲？"))
                .setMessage("会清空系统 logcat 的全部缓冲，包括其他应用的日志。"
                        + "模块配置、已保存的截图与行为库都不受影响。")
                .setPositiveButton(Ui.T("清空"), (x, w) -> new Thread(() -> {
                    ModuleState.I.execShell("logcat -b all -c");
                    h.post(() -> toast("已清空"));
                }, "fpg-clean-log").start())
                .setNegativeButton(Ui.T("取消"), null)
                .create();
        d.show();
        trackDialog(d);
        ui.styleDialog(d);
    }

    /* ---------------- 免责声明 ---------------- */

    private void maybeShowDisclaimer() {
        try {
            final SharedPreferences sp = Config.ui(this);
            if (sp.getInt("disclaimer_ver", 0) == DISCLAIMER_VER) return;
            AlertDialog.Builder b = new AlertDialog.Builder(this)
                    .setTitle(Ui.T("免责声明"))
                    .setMessage(dialogs.readDisclaimerBrief())
                    .setPositiveButton(Ui.T("我已阅读"), (d, w) ->
                            sp.edit().putInt("disclaimer_ver", DISCLAIMER_VER).apply())
                    .setNeutralButton(Ui.T("查看完整条款"), (d, w) -> {
                        sp.edit().putInt("disclaimer_ver", DISCLAIMER_VER).apply();
                        dialogs.disclaimer();
                    })
                    .setCancelable(false);
            AlertDialog d = b.create();
            d.show();
            trackDialog(d);
            ui.styleDialog(d);
        } catch (Throwable ignored) {
        }
    }
}
