# 协作约定

这份文件写给所有改这个仓库的人——包括人和 AI 助手。它不是规则手册，只是把「踩过的坑」和「省时间的做法」放在一起，照着做能少返工；你有更合适的写法，也可以在这里讨论后改它。

更细的结构说明在 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 常用命令

```sh
sh build.sh                 # 完整构建，产物 fuckPrivacyGuard.apk
sh scripts/check_structure.sh   # 静态一致性自查，几秒钟
sh scripts/check_hooks.sh       # Hook 注册与默认开关自查；设 ANDROID_JAR 后校验平台签名
sh tests/run.sh                 # 纯 JVM 单元测试（noteHit 去重），不需要设备或 SDK
```

构建只需要 JDK 与 `javac --release 11`、`d8`、`aapt2`、`zipalign`、`apksigner`，没有 Gradle。CI 走的是同一个脚本。

## 写代码时

- **注入的 dex 尽量保持精简**：它会被加载进十几个系统进程，现在只有 libxposed service 一项必需三方组件；界面全部原生自绘，加库前先想想是否值得
- **类名不要写死**：混淆名每个版本都会漂，能按「修饰符 + 参数类型 + 返回类型 + 声明类所在包」判定就别硬编码
- **失败要留痕**：hook 失败打 `hook miss ...`，扫描中断打 `... skipped classes=N`，不要静默吞掉
- **文案与日志统一**：日志 tag 用 `fuckPrivacyGuard`，中文为默认文案，英文在 `I18n.java` 查表
- **界面复用现成的**：卡片 `Ui.card()`、分组行 `Ui.sectionRow()`、开关 `Ui.makeSwitch()` / `PgSwitch`、弹窗走 `Dialogs` + `Ui.styleDialog()`，别再造一套

## 加一个新组件时，这六处通常都要动

1. `META-INF/xposed/scope.list`
2. `Config.java` 的分组与子项常量
3. `HookEntry.dispatch()` 的分支
4. `Catalog.GROUPS` 与 `I18n.java`
5. `docs/RULES.md` 的判据说明
6. `docs/RELEASE-<版本>.md` 的发布文案

`sh scripts/check_structure.sh` 会帮你核对前四项有没有漏。

## 几个容易踩的点

- `getInsetsController()` 要等 `setContentView` 之后调用，否则部分 ROM 会空指针
- 组开关不要自己翻转：用 `PgSwitch.setOnToggleRequest()` 等作用域确认后再切
- `hookBySignature` 必须逐类兜住 `getDeclaredMethods()` 的异常，安全中心里有类会抛 `NoClassDefFoundError`
- 改 `DISCLAIMER.txt` 记得同步 `assets/DISCLAIMER.txt`，两份不一致时应用内显示的会是旧的
- 覆盖安装后 LSPosed 不会自动把新包加进作用域，需要手动确认

## 提交与发版

- 开发在 OpenHands Cloud 沙盒内进行，**不创建开发分支**，改动直接落在 `main` 上
- 提交信息写「改了什么」，不写「为什么我觉得这样更好」
- 开发完成后**先交付审查报告**（改动清单、验证方式、构建与测试结果），**默认不创建 PR**
- 经 ChatGPT 审查并由维护者批准后，才直接 Commit / Push `main`
- **禁止 force-push / rebase / amend** 已经推送的历史
- Tag / Release 需要单独授权，不随日常提交一起发布
- 版本号、tag、Release 由维护者统一处理；日常提交不用改版本号，也不用动 `build.sh` 的签名配置
- 文案、界面微调会并入下一次实质更新，不单独发版

## 关于 AI 助手

如果你是被叫来改这个仓库的助手：先读这份文件和 `docs/ARCHITECTURE.md`，改完跑一次 `sh build.sh` 与 `sh scripts/check_structure.sh`，涉及 hook 的改动尽量说明验证方式（日志片段或离线扫描结果）。不确定的地方直接问，不需要猜。
