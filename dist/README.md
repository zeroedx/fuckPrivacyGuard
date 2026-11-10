<div align="center">

# fuckPrivacyGuard

![license](https://img.shields.io/badge/license-GPL--3.0-blue)
![language](https://img.shields.io/badge/language-Java-orange)
![release](https://img.shields.io/github/v/release/zeroedx/fuckPrivacyGuard?label=release&color=green)

在采集入口、本地存储、对外接口、网络出口四层拦截 HyperOS 预装组件的隐私上报

</div>

> 源码与构建说明：[fuckPrivacyGuard](https://github.com/zeroedx/fuckPrivacyGuard)（上游：[vivibibi30/hyperpguard](https://github.com/vivibibi30/hyperpguard)）
>
> 本项目源码

> **独立应用**：自 2.0.0 起本项目使用独立包名 `io.github.zeroedx.fuckprivacyguard` 与独立签名证书，**无法覆盖安装上游 HyperPGuard**，反之亦然。从上游迁移需先卸载上游，LSPosed 作用域不会自动迁移，需重新勾选目标组件。

## 当前支持的版本

Android 10 – Android 16 的 HyperOS，LSPosed 2.2.x（libxposed API 100+）。

> Android 17 / SDK 37 / HyperOS 4 已完成源码级适配，但尚未在真机验证；详见
> [兼容性与覆盖范围](https://github.com/zeroedx/fuckPrivacyGuard/blob/main/docs/COMPATIBILITY.md)。

## 使用前说明

1. 在 LSPosed 中启用「fuckPrivacyGuard」
2. 打开应用，首次会向 LSPosed 请求全部作用域，允许即可；也可在 LSPosed 里手动勾选
3. 在模块里对目标应用点一次「重启」，或点批量行的「全部重启」，让应用重新加载
4. 「重启」「查看记录」需要 root，首次使用请允许 KernelSU / Magisk 授权

作用域外的应用不会被注入。

## 功能

| 层 | 内容 |
|---|---|
| 网络出口 | `URL#openConnection`、`InetAddress`、`Socket` 三处拦截，规则为「域名后缀 + 可选路径」 |
| 隐私 API | 应用列表、使用记录、设备标识、短信发送、XMSF 代发广播 |
| 行为库 | 按 `screenshot` / `screen_share` / `voip_record` 等 8 张表名拦截 `SQLiteDatabase` 写入 |
| 静默录屏 | `MediaProjection#createVirtualDisplay` 不再建立 |
| XSOF | Provider / AIDL / 写库 / 评分四层 |
| 整进程断网 | SystemHelper、小米统计 |

覆盖组件：SystemHelper、系统安全组件、安全中心、小米统计、应用安装器、应用商店、小米服务框架、运营商服务、小米可信服务、翻译组件。

会影响系统功能的规则一律默认关闭（云查、XSOF 合作方鉴权、月报、防窥分类、地震报警定位、ADB 验证、设备证书、运营商注册），副标题写明代价。

## 界面

| 总开关 / 批量操作 | 分组卡片 |
|---|---|
| ![主界面](https://raw.githubusercontent.com/zeroedx/fuckPrivacyGuard/main/docs/images/ui-main.png) | ![分组卡片](https://raw.githubusercontent.com/zeroedx/fuckPrivacyGuard/main/docs/images/ui-cards.png) |

## 常见问题

- **某个应用开关打不开**：该包可能不存在、被冻结或与系统共享 UID，LSPosed 无法把它加入作用域
- **日志为空**：`logcat` 环形缓冲已覆盖，或目标应用还没重启过
- **功能异常**：对应的默认关闭子项被打开了，按需关掉即可
- **方法层出现 `hook miss`**：组件版本变化，网络层与隐私 API 层不受影响

## 致谢

[HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) · [XSOF Privacy](https://github.com/yunyizea/xsof-privacy) · [HyperOS4-XSOF-Analysis](https://github.com/FAQQAQ/HyperOS4-XSOF-Analysis) · [酷安 @OSAKOo](https://www.coolapk.com/feed/74882729?s=MzNlYzk4NWUxNWZhMjRnNmFlZTFjMDJ6a1664) · [Android OS Privacy Under the Loupe](https://ar5iv.labs.arxiv.org/html/2302.01890)（ACM WiSec '23）· [酷安 RooyCo](https://www.coolapk.com/feed/74911090) · [frauddb](https://github.com/acnekot/frauddb) · [misight-viewer](https://github.com/Artriai/misight-viewer) · [libxposed API](https://github.com/libxposed/api) · LSPosed

## 免责声明

仅供在本人拥有完全控制权的设备上学习研究使用，不做商业分发。完整条款见 [DISCLAIMER.txt](https://github.com/zeroedx/fuckPrivacyGuard/blob/main/DISCLAIMER.txt)。

[GPL-3.0](https://github.com/zeroedx/fuckPrivacyGuard/blob/main/LICENSE)；第三方组件的许可见 [NOTICE](https://github.com/zeroedx/fuckPrivacyGuard/blob/main/NOTICE)。
