感谢您使用 fuckPrivacyGuard。

2.0.0 是一次品牌与界面的整体升级：项目更名为 fuckPrivacyGuard，设置界面由单页改为底部导航的六个页面，并换用新的品牌图标。同时，本项目改为**完全独立**的应用与 LSPosed 模块，不再与上游 HyperPGuard 共用包名与签名。**拦截逻辑、76 个配置键、默认值与作用域行为均未改动**，但因包名与签名变化，**无法覆盖安装上游 HyperPGuard**：从上游迁移需先卸载上游，作用域不会自动迁移。

## 品牌

- 应用名、日志标签、APK 产物名统一为 `fuckPrivacyGuard`
- 新增品牌图标：由提供的原始 PNG 按比例生成各密度 `mipmap` 与自适应图标前景，未重绘图案
- **独立包名**：applicationId / Java 包名改为 `io.github.zeroedx.fuckprivacyguard`
- **独立签名**：改用新的正式签名证书（非上游 `debug.keystore`），证书 SHA-256 与上游不同
- `Settings.Global` 配置通道键由 `hyperpguard_conf` 改为 `fuckprivacyguard_conf`（避免与旧模块冲突）
- 76 个 `K_*` 配置键名、默认值与配置格式保持不变；升级到新包名后需重新设置一次开关

## 界面

- 设置界面拆分为宿主 + 页面：首页、分类、搜索、诊断、设置、关于，底部导航切换
- 首页：品牌头图、模块版本、运行状态（LSPosed / 框架 / 作用域 / 分组数）、总开关与批量操作
- 分类：13 组 59 项完整保留，组开关仍与 LSPosed 作用域联动
- 搜索：按名称、说明、包名与配置 Key 过滤
- 诊断：作用域清单、运行记录、自检与日志导出
- 设置：通用开关、外观模式（跟随系统 / 浅色 / 深色）、自定义规则、日志管理
- 关于：项目链接、上游致谢与许可证说明
- 深浅色改为整页重建，跟随系统或手动固定；弹窗随主题就地换色

## 说明

- 全部界面仍为原生 Java View 自绘，未引入任何 UI 库或第三方依赖
- 未改动版本号协议之外的任何 hook、规则或默认值
- 上游 HyperPGuard（GPL-3.0）的版权与出处致谢一并保留，见 README 与 NOTICE

全部提交：https://github.com/zeroedx/fuckPrivacyGuard/compare/v1.6.9...v2.0.0
