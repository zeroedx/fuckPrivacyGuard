package io.github.zeroedx.fuckprivacyguard;

import android.view.View;

/**
 * 页面基类。每个页面只负责构建自己的内容视图，导航、滚动、Insets 由宿主负责。
 */
abstract class Page {

    final MainActivity host;
    final Ui ui;

    Page(MainActivity host, Ui ui) {
        this.host = host;
        this.ui = ui;
    }

    /** 页面标题，用于导航与无障碍 */
    abstract String title();

    /** 底部导航图标 */
    abstract PgIcon.Glyph glyph();

    /** 构建页面内容（放进宿主的 ScrollView） */
    abstract View build();

    /** 每次显示时调用，用于刷新状态 */
    void onShow() {
    }
}
