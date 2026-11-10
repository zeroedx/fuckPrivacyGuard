# 规则说明

每条规则的判定标准：**能从代码确认用途、不承载业务功能、不在关键路径上**。三条都满足才默认开启。

## 默认开启

| 应用 | 规则 | 依据 |
|---|---|---|
| MSA | `com.miui.systemAdSolution` 方法层 | 广告使用画像按「今天已打开 = true / 最后使用 = -1」返回；OneTrack 的 `track*` / `adTrack*` 返回空。类名跨版本会漂移，按修饰符 + 参数 + 返回类型 + 包名组合判定 |
| SystemHelper | `dm.wo.com.cn` | 唯一已知上报地址 `http://dm.wo.com.cn:18080/register`（明文 HTTP、应用层加密封装），硬编码在 `c.d.a.b.d` 常量中；组件为运营商设备管理 / 网络优化用途，非恶意软件。同时挂标准应用清单查询入口（`getInstalledPackages` / `getInstalledApplications`）与 `c.d.a.b.f` 上报出口，避免只拦出口时被换域名绕过 |
| guardprovider | `flash.sec.miui.com` | 应用特征上传接口 |
| guardprovider | `detection-aigc.engine.miui.com` | AI 通话预警云检测 |
| guardprovider | `fraud-detection.engine.miui.com` | 反诈配置拉取 |
| analytics | `*.miui.com` `*.xiaomi.com` `*.xiaomi.net` | 整域拦截，覆盖 tracking / om.tracking / 区域与备份域 / sdkconfig |
| analytics | `dns.google` `8.8.8.8` `8.8.4.4` | 6.33.0 国际版 DoH + IP 缓存重试通道 |
| securitycenter | `virus-engine.sec.miui.com` | 默认关闭：风险应用云查 |
| securitycenter | `framework.sec.miui.com` | 默认关闭：XSOF 合作方鉴权 |
| securitycenter | `data.sec.miui.com` | 默认关闭：月报、防窥/广告等配置接口 |
| securitycenter | `flash.sec.miui.com/user/cat` | 默认关闭：上传非系统应用包名数组（防窥分类） |
| securitycenter | `api.sec.miui.com/earthquake/volunteer/sensor/upload` | 默认开启：志愿者传感器，OAID + 经纬度 |
| securitycenter | `api.sec.miui.com/LBS/`、`/earthquake/`、`srv.sec.miui.com/earthquake/`、`gateway-papa.cloud.weizhi.com`、`eps.caict.ac.cn` | 默认关闭：地震预警 / 报警定位 / EPS |
| securitycenter | `srv.sec.miui.com/data/adb` | 默认关闭：ADB 授权验证 |
| guardprovider | `virus-engine.sec.miui.com` | 默认关闭：Flamingo 云查上传应用清单 |
| trustservice | `find.api.micloud.xiaomi.net` | 默认关闭：设备证书 / 密钥上报 |
| registration | `typdm.tydevice.com` | 默认开启：明文 HTTP 上传 CTEI / IMEI / MAC |
| registration | `zzhc.vnet.cn` | 默认关闭：设备注册（IMEI / IMSI / ICCID） |
| securitycenter | `fraud-detection.engine.miui.com` | 反诈配置 |
| securitycenter | `tracking.*.miui.com`、`sdkconfig.*.xiaomi.com` 共 10 条 | OneTrack 统计埋点；host 为裸域名（CN/RU/INTL/IN/IN/EU 五套），无路径依赖 |
| securitycenter | `flash.sec.miui.com` + `/detect/` | 检测接口 |
| packageinstaller | `api.installer.xiaomi.com` + `/avl/upload` | 上传接口 |
| packageinstaller | `api.installer.xiaomi.com` + `/statistics/appactive` | 活跃统计埋点 |
| packageinstaller | `api.installer.xiaomi.com` + `/info/layout` | 返回 `AdModel`，广告位配置 |
| market | `sdkconfig.ad.xiaomi.com` | 广告 SDK 配置 |
| xmsf | `adv.sec.miui.com` | 广告接口 |

## 默认关闭（会影响功能）

| 应用 | 规则 | 影响 |
|---|---|---|
| trustservice | `deviceapi.micloud.xiaomi.net` + MRM 屏蔽 | 远程认证、设备证书、查找设备 |
| 翻译组件 | `xiaomixiaoai.com` + `/speech/` | 语音识别；文字翻译不受影响 |

## 明确不拦

| 目标 | 原因 |
|---|---|
| `virus-engine.sec.miui.com` | 病毒库下载，拦了查杀能力下降 |
| `api.installer.xiaomi.com/interceptcheck` | 游戏安装安全拦截，属功能接口 |
| `find.api.micloud.xiaomi.net` | 设备证书（`PubCertRevise`），影响查找设备 |
| `account.*.xiaomi.com` / `account.*.xiaomixiaoai.com` | 登录与账号，拦了会导致应用不可用 |
| 安装器下载域名 | 应用下载安装主流程 |

## 设计约束

1. **域名规则必须使用完整子域**。使用 `xxx.com` 这类通配会连带拦掉同域下的功能接口，`sec.miui.com` 的教训即在此。
2. **路径规则使用精确前缀匹配**（`startsWith`），不做全路径 `contains`，避免跨进程误伤。
3. **方法层与域名层互不依赖**。方法层失配只影响自身，域名层继续工作；反之亦然。
4. **失败时倾向拦截**。读取不到配置、规则为空时按"拦截"处理，避免静默放行。
5. **可观测**。每次拦截、每次 hook 成功/失配都会落一条日志，便于定位是哪一层在起作用。
   「安装成功」与「实际被调用」是两条不同的日志：前者是 `hook ok`，后者是 `hit` 行
   （`Hooks.noteHit`）。方法层（XSOF / MSA / 统计）与 SystemHelper 的拦截回调里也会记 `hit`，
   不得把「装上 hook」当成「已经拦到」。
6. **方法签名按结构判定，不写死重载**。平台方法的重载个数与参数类型会随版本增加
   （如 `SmsManager` 的发送入口、`UsageStatsManager#queryEvents`、`getInstalledPackages` 的
   flags 重载）。写死某一个重载时，签名对不上会抛 `NoSuchMethodException` 并静默失效，
   所以改为按「方法名 + 参数结构 + 返回类型」的谓词匹配，旧版本不存在的新重载用反射取类、
   取不到就跳过。

## 扩展新规则

1. 打开「记录未拦截访问」跑一段时间，收集目标进程实际访问的域名（日志中带 `?` 前缀）
2. 在该 APK 中确认域名的全部子域，排除功能接口
3. 在 `HookEntry` 对应分支增加 `Rule("host")` 或 `Rule("host", "/path")`
4. 在 `Config` 增加开关常量，在 `Catalog` 增加对应条目
5. 重新构建，在目标应用上验证 `X(host)` / `X(host+path)` 日志（grep 用 `grep -F 'X(host'`）

若某接口同时承担上报与功能，优先按路径拆分，不要整域拦截。
