# 项目结构与扩展指引

给后续维护和协作用。写完新功能后请顺手更新 `docs/RULES.md` 与 `docs/RELEASE-<版本>.md`。

## 一、拦截分层

模块在四个位置拦截，彼此独立：

| 层 | 文件 | 说明 |
|---|---|---|
| 网络出口 | `NetGuard.java` | `URL#openConnection`、`InetAddress`、`Socket` 构造/`connect`、okhttp `Dns`；规则为「域名后缀 + 可选路径前缀」 |
| 隐私 API | `PrivacyApis.java` | 应用列表、使用记录、设备标识、短信、XMSF 代发广播 |
| 本地存储 | `CaptureHooks.java` | 行为库八张表的 `SQLiteDatabase` 写入、静默录屏 |
| 方法层 | `XsofHooks.java`、`MsaHooks.java`、`AnalyticsHooks.java` | 按结构或签名定位方法后直接返回常量 |

`Hooks.java` 是公共工具：`ret`、`retAllByName`、`classNames`、`hookBySignature`、`customMethods`。
`hookBySignature` 逐类兜住 `getDeclaredMethods()` 的异常——安全中心里有类的方法签名引用 `javax.lang.model.*`，不兜会整轮中断。

## 二、文件职责

```
HookEntry.java     入口；onPackageReady 后按包名分发到各层
Config.java        开关常量、配置通道（LSPosed RemotePreferences，无 root）
ConfigWire.java    配置编解码（`k=v|k=v`，与旧格式兼容）
I18n.java          界面英文查表（中文是唯一来源，查不到原样返回）
NetGuard.java      网络层与规则匹配
PrivacyApis.java   隐私 API 层
CaptureHooks.java  行为库写入与静默录屏
XsofHooks.java     XSOF 四层 + MRM
MsaHooks.java      MSA 使用画像与 OneTrack
AnalyticsHooks.java 统计方法层
Hooks.java         公共 hook 工具
PgIcon.java        自绘矢量图标与字符徽标（导航 / 组件图标，无图片资源）
PgSwitch.java      设置页开关（自绘，点击可由外部接管）
Logs.java          统一日志标签 fuckPrivacyGuard
Ui.java            自绘界面件（色板 / 卡片 / 图标 / chip / 开关行 / 弹窗着色）
Catalog.java       分组与子项的唯一来源（13 组 / 59 项）
Page.java          页面基类
HomePage.java      首页：状态 / 总开关 / 批量操作
CategoriesPage.java 分类：按系统组件列出名称 / 包名 / 安装状态
ComponentDetailPage.java 组件二级页：单个组件的全部开关 / 重启 / 返回
DiagnosticsPage.java 诊断：作用域 / 运行记录 / 自检
SettingsPage.java  设置：通用 / 外观 / 规则 / 日志
AboutPage.java     关于：品牌 / 链接 / 致谢 / 许可
Dialogs.java       自定义规则 / 行为库 / 日志 / 免责声明 / 更新
ModuleState.java   LSPosed 连接、作用域与 root 命令
MainActivity.java  宿主：导航 / 滚动 / Insets / 深浅色 / LSPosed 连接（无 layout 资源）
```

## 三、新增一个组件分组

以新增「组件 X」为例，六处改动：

1. **`META-INF/xposed/scope.list`**：加包名。staticScope 模块，覆盖安装后需要在 LSPosed 里重新确认作用域，旧进程不会自动补注入
2. **`Config.java`**：加分组开关 `K_G_X` 与子项常量；默认值写在使用处（`Config.on(K, true/false)`），不落盘
3. **`HookEntry.dispatch()`**：加 `if ("包名".equals(pkg)) { ... }` 分支，先按分组开关 return，再调各层
4. **`Catalog.GROUPS`**：加 `Group` 与 `Item`，文案过 `T()`；同时在 `I18n.java` 补英文
5. **`docs/RULES.md`**：写清判据（域名 / 方法签名 / 表名）与默认开关的理由
6. **验证**：`sh build.sh` → 装到设备 → 在 LSPosed 确认作用域 → 看 `attach <包>` 与对应 `hook ok` / `X(host)` 日志

方法层如果代码量超过 80 行，单独建 `XxxHooks.java`，不要继续堆进 `HookEntry`。

## 四、规则判据的写法

- 网络规则优先用完整子域，不要用 `xxx.com` 这种宽泛后缀
- 路径规则是 `startsWith`，只对 `URL#openConnection` 生效
- 类名会随版本漂移：能按「修饰符 + 参数类型 + 返回类型 + 声明类所在包」判定时，不要写死混淆名
- 每次 hook 失败都要留日志（`hook miss ...`），不要静默失败

## 五、界面约定

- 色板集中在 `Ui.palette()`：浅色 `#F4F5F7/#FFFFFF/#1A1A1A/#8A8A8E`，深色 `#000000/#1A1A1C/#FFFFFF/#9A9AA0`；卡片带极浅描边（浅色 `#0A000000`、深色 `#14FFFFFF`）用于区分抬升面；页面用 `new XxxPage(host, ui)` 取色，深浅色切换时随 `MainActivity.buildUi()` 一起重建
- 图标全部自绘（`PgIcon`，24×24 网格描边）：底部导航、返回箭头、组件徽标；组件行优先取真实应用图标，取不到时用字符徽标兜底，不引入任何图片资源或第三方图标库
- 底部导航为「图标 + 文字」竖排，选中项用强调色 + 浅色底高亮（`navItems` / `navIcons` / `navTabs`）
- 每个页面用 `ui.pageTitle()` 起一个统一大标题（首页以品牌头图代标题）
- 状态用 `ui.chip()`：小圆角底 + 彩色文字；未安装组件整行置灰
- 可点条目统一用 `pressBackground()`，文字保持强调色
- 弹窗统一走 `Dialogs` + `Ui.styleDialog()` / `MainActivity.trackDialog()`，保证深色与切换主题时就地换色
- 开关用 `PgSwitch`；需要「等外部确认再切换」时用 `setOnToggleRequest()`，自身不翻转
- 行规格：标题 16sp、副标题 12sp、行高 56dp、卡片圆角 20dp、分组标题 14sp/35dp

## 六、构建与发布

```sh
sh build.sh          # javac --release 11 → d8 → aapt2 link → zipalign → apksigner
git tag -a vX.Y.Z -m "fuckPrivacyGuard X.Y.Z"
git push origin main vX.Y.Z
```

CI 在 tag 上做三件事：构建 APK、源码仓建 Release、同步到分发仓（tag 为 `<versionCode>-<versionName>`）。发版前先在 `docs/RELEASE-<版本>.md` 写好正文，否则 CI 会失败。

界面全部自绘（native Java View），不引入 UI 库；注入的 dex 现在只含 libxposed service 一项必需组件，加库会直接放大 13 个进程的注入开销。引入新依赖前先评估这份开销。
