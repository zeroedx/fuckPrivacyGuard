# Android 17 / HyperOS 4 专项隐私防护审计报告

> 本报告为**只读审计**产物。审计过程中未修改任何源码、构建脚本、版本号、Tag 或 Release；
> 唯一新增的文件就是本报告本身。所有结论都区分「源码确认」「平台签名确认（静态）」与
> 「待真机验证」，**没有真机证据的项目一律标记为待验证，不虚构命中率或上传事实**。

## 0. 审计元信息

| 项 | 值 |
|---|---|
| 仓库 | `zeroedx/fuckPrivacyGuard`（fork of `vivibibi30/hyperpguard`） |
| 本地路径 | `/workspace/project/fuckPrivacyGuard` |
| 当前分支 | `main` |
| HEAD | `e73b9ffa76ecadd93f44ebd4f8c83acc3ca23fa4`（Merge pull request #1） |
| 工作区状态 | 54 项未提交变更（26 modified + 28 untracked），无暂存；均为上一轮 2.0.0 品牌/UI 重构遗留，**hook 实现文件（PrivacyApis/NetGuard/CaptureHooks/Hooks/XsofHooks/MsaHooks/AnalyticsHooks）本轮未改动** |
| 目标设备（声明） | Xiaomi 17 / Android 17 / SDK 37 / HyperOS 4 / arm64-v8a / LSPosed libxposed API 102 |
| 模块声明 API | `minApiVersion=101`、`targetApiVersion=102`、`staticScope=true`（`META-INF/xposed/module.prop`） |
| 编译 / target SDK | 编译用 android-35；`targetSdkVersion=34`、`minSdkVersion=29`（`AndroidManifest.xml:7`） |
| 作用域 | 13 个包（`META-INF/xposed/scope.list`） |

### 0.1 证据来源与分级

| 等级 | 含义 | 本次用到的证据 |
|---|---|---|
| A 源码确认 | 本仓库源码可直接核对 | 逐行阅读 `HookEntry` / `PrivacyApis` / `NetGuard` / `CaptureHooks` / `Hooks` / `XsofHooks` / `MsaHooks` / `AnalyticsHooks` / `Catalog` / `Config` |
| B 平台签名确认 | 用真实 `android.jar`（android-29/32/33/35/36/37）静态核对方法签名 | `/tmp/sdk37/android-37.0/android.jar` 等，`javap` + 反射 `getDeclaredMethod` 探测 |
| C 可重复自查 | 仓库自带脚本 | `sh scripts/check_hooks.sh`（`ANDROID_JAR` 指向 android-37）、`sh tests/run.sh`、`sh scripts/check_structure.sh` |
| D 待真机验证 | 需要目标机型运行时日志/产物 | 本仓库**无**此类证据；本次**未**进行任何真机测试 |

**重要**：B 级只能证明「公开 SDK 里存在该签名」或「公开 SDK 里不存在该签名」，
不能证明厂商框架内部类（`android.app.ApplicationPackageManager`、
`android.content.ContextImpl` 等）的实际覆写、也不能证明运行时行为。
厂商类不在 SDK 平台包内（`javap` 报 class not found），需真机确认。

### 0.2 审计范围

用户指定的三个重点组件：

1. `com.mobiletools.systemhelper`（SystemHelper，运营商工具/诊断）
2. `com.miui.dmregservice`（移动 DM / RegService）
3. `com.miui.guardprovider`（系统安全组件 / 反诈）

附带核对 `com.miui.analytics`（小米统计）在证据出现处。三者均在 `scope.list` 内。

---

## 1. 三个组件现有覆盖矩阵

### 1.1 分组与安装参数（源码确认）

三个组件的安装参数在 `HookEntry.dispatch()` 里写死（不是 UI 可调）：

| 组件 | 分组开关（默认） | 源码位置 | `privacySafe(...)` 实参 | 含义 |
|---|---|---|---|---|
| systemhelper | `K_G_SYSHELPER=true` | `HookEntry.java:292-307` | `appList=false, usage=false, ids=true, sms=false, xmsf=false` | 只做设备标识；**不拦标准应用清单/使用记录** |
| guardprovider | `K_G_GUARD=true` | `HookEntry.java:310-327` | `appList=true, usage=false, ids=true, sms=false, xmsf=false` | 应用清单 + 设备标识 |
| dmregservice | `K_G_DMREG=true` | `HookEntry.java:329-338` | `appList=true, usage=false, ids=true, sms=false, xmsf=false` | 应用清单 + 设备标识 |

`privacySafe` 包装：`HookEntry.java:224-230`；`PrivacyApis.install` 主入口：`PrivacyApis.java:37-131`。

### 1.2 逐组件 × 逐敏感数据覆盖矩阵

图例：**已** = 有 hook 且默认生效；**部** = 有 hook 但默认关闭，或只覆盖部分入口；
**未** = 无对应 hook；**验** = 无法确认（依赖厂商类/运行时不变量）。

#### 1.2.1 `com.mobiletools.systemhelper`

| 敏感数据 | 状态 | Hook 类 / 方法 | 源码位置 |
|---|---|---|---|
| IMEI / MEID | 已 | `TelephonyManager#getDeviceId/getImei/getMeid`（含 `(int)` 重载）→ `null` | `PrivacyApis.java:70-72, 215-240` |
| IMSI | 已 | `getSubscriberId*` → 真实 MCC+MNC + 固定 MSIN（15 位） | `PrivacyApis.java:222, 242-300` |
| ICCID | 已 | `getSimSerialNumber*` → `8986000000000000000` | `PrivacyApis.java:224, 242` |
| Android ID | 已 | `Settings.Secure.getString(cr,"android_id")` → `null` | `PrivacyApis.java:137-158` |
| 手机号 | 已 | `getLine1Number*` → `""` | `PrivacyApis.java:81-83` |
| MAC | 已 | `WifiInfo#getMacAddress` → `02:00:00:00:00:00` | `PrivacyApis.java:76-78` |
| 硬件序列号 | 已 | `Build#getSerial` → `"unknown"` | `PrivacyApis.java:87-89` |
| 已安装应用清单 | 部 | `appList=false`，标准 `getInstalledPackages/Applications` **未挂**；仅拦 `c.d.a.b.f` 上报出口 | `HookEntry.java:295-298` |
| 应用使用记录 | **未** | `usage=false` | `HookEntry.java:295` |
| 定位 / GPS | 部 | `LocationManager`（默认关 `K_LOC_SH=false`） | `PrivacyApis.java:162-186`，`Catalog.java:61` |
| 基站 | 部 | `TelephonyManager` 小区接口（默认关 `K_CELL_SH=false`） | `PrivacyApis.java:188-213`，`Catalog.java:62` |
| Wi-Fi 扫描 / SSID / BSSID | 部 | `WifiManager#getScanResults`、`WifiInfo#getSSID/getBSSID`（默认关 `K_WIFI_SH=false`） | `PrivacyApis.java:301-329`，`Catalog.java:63` |
| 蓝牙 / 蓝牙 BSSID | **未** | 无蓝牙相关 hook | — |
| 通讯录 / 通话 / 短信读取 | **未** | systemhelper 的 CAPS 无 `social_*`，`hookResolver` 不会安装 | `PrivacyApis.java:25-26, 96-106` |
| 麦克风 | **未** | 无 | — |
| 摄像头 | **未** | 无 | — |
| 屏幕录制 | **未** | `screenCapture` 只在 guardprovider 安装 | `HookEntry.java:322-324` |
| 本地数据库写入 | 部 | 方法层 `c.d.a.b.e#a(String)→void` + 包扫描回退 | `HookEntry.java:299, 174-199` |
| Binder / AIDL 出口 | **未** | 无 `ContentResolver#call` / `IBinder` / `Parcel` hook | — |
| HTTP / HTTPS 上报 | 部 | 域名 `dm.wo.com.cn`；方法层 `c.d.a.b.f#a(String,byte[],byte[])→false` | `HookEntry.java:303, 296-298` |
| 整进程断网 | 部 | `K_SYSHELPER_ALLNET=false`（默认关） | `HookEntry.java:306` |

#### 1.2.2 `com.miui.dmregservice`

| 敏感数据 | 状态 | Hook 类 / 方法 | 源码位置 |
|---|---|---|---|
| IMEI / MEID | 已 | 同 `hookTelephonyIds` | `PrivacyApis.java:70-72` |
| IMSI | 已 | `getSubscriberId*` → 伪 IMSI | `PrivacyApis.java:222` |
| ICCID | 已 | `getSimSerialNumber*` → 伪 ICCID | `PrivacyApis.java:224` |
| Android ID | 已 | `Settings.Secure.getString` | `PrivacyApis.java:137-158` |
| 已安装应用清单 | 已 | `getInstalledPackages/Applications`（`int` + flags 两组重载）→ 空表 | `PrivacyApis.java:44-58` |
| 应用使用记录 | **未** | `usage=false` | `HookEntry.java:333` |
| 定位 / GPS | **未** | CAPS 无 `loc_*`，`hookLocation` 不安装 | `PrivacyApis.java:33-34` |
| 基站 | 部 | 默认关 `K_CELL_DM=false` | `Catalog.java:126`，`PrivacyApis.java:188-213` |
| Wi-Fi 扫描 / SSID / BSSID | 部 | 默认关 `K_WIFI_DM=false` | `Catalog.java:127`，`PrivacyApis.java:301-329` |
| 蓝牙 / 蓝牙 BSSID | **未** | 无 | — |
| 通讯录 / 通话 / 短信 | **未** | CAPS 无 `social_*` | `PrivacyApis.java:33-34` |
| 麦克风 / 摄像头 / 录屏 | **未** | 无 | — |
| 本地数据库写入 | **未** | 无 db hook | — |
| HTTP / HTTPS 上报 | 部 | 域名 `fxltsbl.com`（`K_DMREG=true`） | `HookEntry.java:334-337` |
| Binder / AIDL 出口 | **未** | 无 | — |

> 注：代码注释 `HookEntry.java:330` 称覆盖「App 清单、SIM 标识、基站、Wi-Fi / **蓝牙**扫描、BSSID」，
> 但源码中**没有蓝牙 hook**；「基站 / Wi-Fi」默认关闭。该注释与实现存在口径差。

#### 1.2.3 `com.miui.guardprovider`

| 敏感数据 | 状态 | Hook 类 / 方法 | 源码位置 |
|---|---|---|---|
| IMEI / MEID / IMSI / ICCID | 已 | 同 `hookTelephonyIds` | `PrivacyApis.java:70-72, 215-240` |
| Android ID | 已 | `Settings.Secure.getString` | `PrivacyApis.java:137-158` |
| 已安装应用清单 | 已 | `getInstalledPackages/Applications`（`int`+flags）→ 空表 | `PrivacyApis.java:44-58` |
| 应用使用记录 | **未** | `usage=false` | `HookEntry.java:321` |
| 定位 / GPS | 部 | 默认关 `K_LOC_GP=false` | `Catalog.java:72`，`PrivacyApis.java:162-186` |
| 基站 | **未** | CAPS 无 `cell_*`（guardprovider 无基站键） | `PrivacyApis.java:29-30` |
| Wi-Fi 扫描 / SSID / BSSID | 部 | 默认关 `K_WIFI_GP=false` | `Catalog.java:73`，`PrivacyApis.java:301-329` |
| 蓝牙 / 蓝牙 BSSID | **未** | 无 | — |
| 通讯录 / 通话读取 | 部 | `ContentResolver#query` 拦 `call_log/sms/mms/contacts`（默认关 `K_SOCIAL_GP=false`） | `Catalog.java:74`，`PrivacyApis.java:331-357` |
| 麦克风 / 摄像头 | **未** | 无 | — |
| 屏幕录制 | 已 | `MediaProjection#createVirtualDisplay` → `null`（`K_GP_SCREENGUARD=true`） | `HookEntry.java:322-324`，`CaptureHooks.java:80-95` |
| 本地数据库写入 | **未** | guardprovider 无 db hook（XSOF/行为库在 securitycenter） | — |
| HTTP / HTTPS 上报 | 部 | `flash.sec.miui.com`、`detection-aigc.engine.miui.com`、`fraud-detection.engine.miui.com`、`logupdate.avlyun.sec.miui.com`（`virus-engine.sec.miui.com` 默认关） | `HookEntry.java:313-319` |
| Binder / AIDL 出口 | **未** | 无 | — |

---

## 2. 每个敏感数据类型对应的 Hook 类、方法与源码位置（汇总表）

| 数据类型 | 拦截点 | 类#方法 | 返回值 | 源码位置 | 默认 |
|---|---|---|---|---|---|
| IMEI/MEID | 数据源 | `TelephonyManager#getDeviceId/getImei/getMeid`（+`(int)`） | `null` | `PrivacyApis.java:215-240` | 对三组件恒开 |
| IMSI | 数据源 | `#getSubscriberId*` | 真实 MCC+MNC + 伪 MSIN | `PrivacyApis.java:222,242-300` | 恒开 |
| ICCID | 数据源 | `#getSimSerialNumber*` | 固定 19 位 | `PrivacyApis.java:224` | 恒开 |
| Android ID | 数据源 | `Settings$Secure#getString`（2 参） | `null` | `PrivacyApis.java:137-158` | 恒开 |
| 手机号 | 数据源 | `#getLine1Number*` | `""` | `PrivacyApis.java:81-83` | 恒开 |
| MAC | 数据源 | `WifiInfo#getMacAddress` | `02:00:00:00:00:00` | `PrivacyApis.java:76-78` | 恒开 |
| 序列号 | 数据源 | `Build#getSerial` | `"unknown"` | `PrivacyApis.java:87-89` | 恒开 |
| 应用清单 | 数据源 | `ApplicationPackageManager#getInstalledPackages/Applications`（`int`+flags） | 空表 | `PrivacyApis.java:44-58` | 按组件（见 §1.1） |
| 使用记录 | 数据源 | `UsageStatsManager#queryEvents(long,long)` / `#queryUsageStats(int,long,long)` | `null` / 空表 | `PrivacyApis.java:60-66` | 仅 analytics |
| GPS | 数据源 | `LocationManager#getLastKnownLocation/requestLocationUpdates/getCurrentLocation` | `null` | `PrivacyApis.java:162-186` | 默认关 |
| 基站 | 数据源 | `TelephonyManager#getCellLocation/getAllCellInfo/getNeighboringCellInfo` | `null` / 空表 | `PrivacyApis.java:188-213` | 默认关 |
| Wi-Fi 扫描 | 数据源 | `WifiManager#getScanResults` | 空表 | `PrivacyApis.java:306-312` | 默认关 |
| SSID/BSSID | 数据源 | `WifiInfo#getSSID/getBSSID` | `"<unknown ssid>"` / `02:00:…` | `PrivacyApis.java:313-325` | 默认关 |
| 通讯录/通话/短信读取 | 数据源 | `ContentResolver#query`（按 URI 前缀） | `null` | `PrivacyApis.java:331-357` | 默认关 |
| 短信发送 | 出口 | `SmsManager#sendTextMessage/sendMultipartTextMessage/sendDataMessage` | 见 §7-P0 | `PrivacyApis.java:109-120` | **签名错误，未生效** |
| XMSF 代发广播 | 出口 | `ContextWrapper/ContextImpl#sendBroadcast` | 过滤特定 action | `PrivacyApis.java:359-380` | 仅 analytics |
| 行为库写入 | 本地 | `SQLiteDatabase#insert/update/delete/execSQL` 等 8 表 | `-1/0` | `CaptureHooks.java:22-78` | 仅 securitycenter |
| 静默录屏 | 设备 | `MediaProjection#createVirtualDisplay` | `null` | `CaptureHooks.java:80-95` | guardprovider 开 |
| 网络出口 | 出口 | `URL#openConnection`、`InetAddress#getByName/getAllByName`、`okhttp3.Dns#lookup`、`Socket` 构造器/`connect` | 抛异常 | `NetGuard.java:39-134` | 按域名规则 |
| 统计方法层 | 出口 | analytics 固定混淆名 + 签名扫描 | `null`/0 | `AnalyticsHooks.java:16-97` | analytics 开 |
| MSA 画像/埋点 | 出口 | 结构谓词 + `OneTrack#track*` | 见源码 | `MsaHooks.java:31-113` | MSA 开 |
| XSOF 四层 | 出口 | Provider/AIDL/写库/评分 | 见源码 | `XsofHooks.java:21-244` | securitycenter 开 |
| SystemHelper 上报/写库 | 出口 | `c.d.a.b.f#a`、`c.d.a.b.e#a` + 包扫描回退 | `false`/`null` | `HookEntry.java:296-302,155-199` | 恒开 |

---

## 3. 覆盖状态汇总（已覆盖 / 部分覆盖 / 未覆盖 / 无法确认）

### 3.1 已覆盖（源码确认）

- 设备标识（IMEI/MEID/IMSI/ICCID/Android ID/手机号/MAC/序列号）：三组件均恒开，见 §2。
- 应用清单：guardprovider、dmregservice 恒开（`appList=true`）。
- 静默录屏：guardprovider（默认开）。
- 域名层上报：三组件均有对应域名规则（部分默认关）。
- 方法层上报/写库：systemhelper（`c.d.a.*`）。

### 3.2 部分覆盖（源码确认）

| 项 | 说明 | 位置 |
|---|---|---|
| **短信发送** | 三个 hook 签名全部错误，`getDeclaredMethod` 抛 `NoSuchMethodException` → 记 `hook miss` 且返回 `false`，**实际 0 拦截**（且仅 `registration` 走此路径） | `PrivacyApis.java:109-120` |
| 应用清单 | 只拦 `getInstalledPackages/Applications` 两个家族；`queryIntent*`、`getPackagesForUid`、`LauncherApps`、`IPackageManager` 直连未覆盖 | `PrivacyApis.java:44-58` |
| 使用记录 | 只拦 `queryEvents(long,long)`；Android 15+ 新增的 `queryEvents(UsageEventsQuery)` 未覆盖 | `PrivacyApis.java:60-63` |
| systemhelper 应用清单 | 标准 API 未挂（`appList=false`）；仅 `c.d.a.*` 上报出口 + `dm.wo.com.cn` 被拦 | `HookEntry.java:295-303` |
| dmregservice 定位 | CAPS 无 `loc_*`，定位入口不安装（文档未宣称，但矩阵上无覆盖） | `PrivacyApis.java:33-34` |
| guardprovider 基站 | CAPS 无 `cell_*` | `PrivacyApis.java:29-30` |
| 网络出口 | `java.net.Socket`/`URL`/`InetAddress` 已覆盖；**`java.nio.channels.SocketChannel`、Cronet、WebView、DoH-over-自实现**未覆盖 | `NetGuard.java:39-134` |
| 方法层可观测性 | XSOF/MSA/Analytics 的 hook **不调用 `noteHit`**，只记安装数量，无「首次命中」日志 → 无法从日志判断是否真拦截 | `XsofHooks.java`、`MsaHooks.java`、`AnalyticsHooks.java` |
| 手机号 | 代码调用 `getLine1NumberForSubscriber`，该类在公开 SDK 中不存在（SystemApi），`retAllByName` 静默跳过 | `PrivacyApis.java:81-83` |

### 3.3 未覆盖（源码确认不存在对应 hook）

- 麦克风（`AudioRecord` / `MediaRecorder` / `AudioManager`）——三组件均无。
- 摄像头（`Camera` / `CameraManager` / `ImageReader`）——无。
- 蓝牙（`BluetoothAdapter` / `BluetoothDevice`）——无（含蓝牙侧 BSSID/MAC）。
- Binder / AIDL / ContentProvider 通用出口（`ContentResolver#call`、`IBinder`、`Parcel`）——无。
- Native / HAL / `/proc` / sysfs 数据路径——无（模块纯 Java，APK 内无 `.so`）。
- 跨进程代发（A 进程采集、B 进程上报）——无。
- guardprovider 本地数据库写入——无。
- dmregservice 本地数据库写入——无。

### 3.4 无法确认（依赖厂商类或运行时，需真机）

- 被 hook 的框架内部类是否真的覆写/调用这些方法：
  `android.app.ApplicationPackageManager`、`android.content.ContextImpl`、`android.app.LoadedApk#makeApplication`。
- 反射使用的隐藏 API 在 Android 17 上是否仍可访问：
  `BaseDexClassLoader.pathList/dexElements/dexFile`、`ActivityThread.mInitialApplication`。
- 各组件在 HyperOS 4 上的实际调用链、混淆名（`c.d.a.*`、`com.miui.analytics.onetrack.*` 等）。
- LSPosed 在 Android 17 上对 libxposed API 102 的支持与 staticScope 行为。

---

## 4. 默认关闭的规则及其影响

三个重点组件相关的默认关闭项（来自 `Catalog.java`，共 27 项全局默认关闭）：

| 组件 | 默认关闭项 | Config 键 | 影响 |
|---|---|---|---|
| systemhelper | 整进程断网 | `K_SYSHELPER_ALLNET` | 只拦 `dm.wo.com.cn` 与 `c.d.a.*` 方法层；未知域名/新地址不拦 |
| systemhelper | 定位（GPS） | `K_LOC_SH` | 位置查询返回真实值 |
| systemhelper | 基站信息 | `K_CELL_SH` | 小区信息返回真实值 |
| systemhelper | Wi-Fi 扫描 | `K_WIFI_SH` | 扫描列表/SSID/BSSID 为真实值 |
| guardprovider | 云查上传（Flamingo） | `K_GP_CLOUDSCAN` | 应用清单可上传 `virus-engine.sec.miui.com` |
| guardprovider | 定位（GPS） | `K_LOC_GP` | 位置真实 |
| guardprovider | Wi-Fi 扫描 | `K_WIFI_GP` | SSID/BSSID 真实 |
| guardprovider | 通讯录 / 通话读取 | `K_SOCIAL_GP` | `ContentResolver#query` 返回真实通讯录/通话 |
| dmregservice | 基站信息 | `K_CELL_DM` | 小区信息真实 |
| dmregservice | Wi-Fi 扫描 | `K_WIFI_DM` | SSID/BSSID 真实 |

**影响面**：以上默认关闭意味着——**在不手动开启的前提下，三个组件仍能拿到真实的
定位、基站、Wi-Fi/BSSID、通讯录/通话，以及 guardprovider 可上传应用清单**。
只有设备标识（IMEI/IMSI/ICCID/Android ID/手机号/MAC/序列号）与
guardprovider/dmregservice 的应用清单是默认拦截的。

> 设计取舍（`docs/COMPATIBILITY.md` §四）：会影响系统功能的规则一律默认关闭。
> 这是有意为之，但审计结论应明确：**默认配置下隐私保护是「部分」而非「完整」**。

---

## 5. Native / Binder / 网络出口盲区

### 5.1 Native 层

- 模块为纯 Java，APK 内无 `.so`（`unzip -l` 确认无 `lib/`）；所有 hook 都在 Java 层。
- **未覆盖**：组件用 native 网络栈（Cronet、WebView、自研 C++ socket）或直接读
  `/proc`、sysfs、HAL 的路径。三组件若走这些路径，拦截完全失效。

### 5.2 Binder / AIDL / ContentProvider

- **未覆盖**：没有 `ContentResolver#call`、`IBinder#transact`、`Parcel` 或
  `IPackageManager` 的通用 hook。
- 现有 ContentProvider 相关 hook 仅限：`ContentResolver#query`（按 URI 前缀，默认关）、
  XSOF 的 `XSOFProvider`（securitycenter）。跨进程代发（组件把数据交给另一进程统一上报）
  不在覆盖内。

### 5.3 网络出口

`NetGuard.install`（`NetGuard.java:39-134`）覆盖：

| 入口 | 状态 | 位置 |
|---|---|---|
| `java.net.URL#openConnection` | 已 | `NetGuard.java:45-53` |
| `java.net.InetAddress#getByName/getAllByName` | 已 | `NetGuard.java:58-71` |
| `okhttp3.Dns#lookup`（自实现 DNS，扫描类名含 "dns"） | 已 | `NetGuard.java:76-104` |
| `java.net.Socket` 全部构造器 + `connect` | 已 | `NetGuard.java:109-134` |
| **`java.nio.channels.SocketChannel` / NIO** | **未** | — |
| **Cronet（native）** | **未** | — |
| **WebView 网络栈** | **未** | — |
| **DoH 到 IP 直连（如 `dns.google` 之外的解析器）** | **部** | 仅域名规则，analytics 有 `dns.google`/`8.8.8.8` 规则 |
| 严格模式（整进程断网） | 已实现，按组件默认 | `NetGuard.java:148-183`，`isLocal` `195-250` |

> 结论：域名规则是「已知域名白名单式」拦截；**未列入的域名、NIO/Cronet/WebView 出口、
> 直连 IP 都会绕过**。systemhelper/guardprovider/dmregservice 的严格模式默认**关闭**。

---

## 6. Android 17 兼容性风险

### 6.1 平台签名核对结果（B 级：android-29/32/33/35/36/37 静态核对）

| 被 hook 的签名 | 29 | 35 | 36 | 37 | 判定 |
|---|---|---|---|---|---|
| `getInstalledPackages(int)` / `getInstalledApplications(int)` | 有 | 有 | 有 | 有 | 兼容 |
| flags 重载（`PackageInfoFlags`/`ApplicationInfoFlags`） | 无 | 有 | 有 | 有 | 兼容（≥33） |
| `LocationManager#getLastKnownLocation(String)` | 有 | 有 | 有 | 有 | 兼容 |
| `LocationManager#getCurrentLocation(...)` | 无 | 有 | 有 | 有 | 兼容（≥30） |
| `LocationManager#requestLocationUpdates(...)`（多重重载） | 有 | 有 | 有 | 有 | 兼容 |
| `TelephonyManager#getDeviceId/getImei/getMeid/getSubscriberId/getSimSerialNumber` | 有 | 有 | 有 | 有 | 兼容 |
| `TelephonyManager#getCellLocation` / `getAllCellInfo` | 有 | 有 | 有 | 有 | 兼容 |
| `TelephonyManager#getNeighboringCellInfo` | **无** | **无** | **无** | **无** | 死代码（无害） |
| `TelephonyManager#getLine1NumberForSubscriber` | 无 | 无 | 无 | 无 | 静默跳过（无害） |
| `WifiManager#getScanResults` / `WifiInfo#getSSID/getBSSID` | 有 | 有 | 有 | 有 | 兼容 |
| `Settings$Secure#getString(cr,String)` | 有 | 有 | 有 | 有 | 兼容 |
| `UsageStatsManager#queryEvents(long,long)` / `queryUsageStats(int,long,long)` | 有 | 有 | 有 | 有 | 兼容 |
| `UsageStatsManager#queryEvents(UsageEventsQuery)` | 无 | 有 | 有 | 有 | **未 hook（新重载）** |
| `MediaProjection#createVirtualDisplay(9 参)` | 有 | 有 | 有 | 有 | 兼容 |
| `ContextWrapper#sendBroadcast(Intent)` | 有 | 有 | 有 | 有 | 兼容 |
| `Build#getSerial` | 有 | 有 | 有 | 有 | 兼容 |
| **`SmsManager#sendTextMessage`** | **无** | **无** | **无** | **无** | **签名错误（见 P0）** |
| **`SmsManager#sendMultipartTextMessage`** | **无** | **无** | **无** | **无** | **签名错误（见 P0）** |
| **`SmsManager#sendDataMessage`** | **无** | **无** | **无** | **无** | **签名错误（见 P0）** |

> 「SmsManager 无」指**按代码里写的签名**在公开 SDK 中不存在；正确签名在所有版本都存在，
> 因此这是**跨版本**的代码 bug，不是 Android 17 特有。

### 6.2 Android 17 专项风险（待真机验证）

1. **隐藏 API 收紧**：`BaseDexClassLoader.pathList/dexElements/dexFile`、
   `ActivityThread.mInitialApplication`、`LoadedApk#makeApplication` 均为隐藏 API。
   Android 17 若进一步收紧，类名扫描（`Hooks.classNames`）与配置读取会整体失效。
   LSPosed 通常对模块豁免，但**未在 Android 17 上验证**。
2. **框架内部类覆写不确定**：`ApplicationPackageManager`、`ContextImpl` 不在 SDK 内，
   无法静态确认其在 HyperOS 4 上的实际方法集；`getInstalledPackages` 的实际覆写需真机日志。
3. **混淆名漂移**：`AnalyticsHooks` 的固定目标（`com.miui.analytics.onetrack.p.u#w` 等）
   与 `XsofHooks` 的 `Ra.k`/`Qa.*` 在版本间会变；`c.d.a.*` 亦然。方法层在 HyperOS 4 上
   可能整体 `hook miss`（有日志、不崩溃）。
4. **新重载盲区**：`UsageStatsManager#queryEvents(UsageEventsQuery)`（API 35+）未覆盖。
5. **staticScope**：升级后 LSPosed 不会自动把新包加进作用域，需手动确认
   （`AGENTS.md` 已记录；`MainActivity.syncFromService` 会以框架真实作用域为准）。
6. **编译/target SDK 落后**：编译用 android-35、`targetSdk=34`；未用 android-37 编译，
   也未声明支持 Android 17（`README.md:29`、`docs/COMPATIBILITY.md:15`）。

### 6.3 仓库自查脚本结果（C 级）

- `ANDROID_JAR=/tmp/sdk37/android-37.0/android.jar sh scripts/check_hooks.sh`：**21 项全过，0 失败，0 跳过**。
  （该脚本只核对 flags 重载与默认开关，**不覆盖 SMS 签名**，故未发现 P0。）
- `sh tests/run.sh`：**ALL PASS**（`noteHit` 去重、并发去重）。
- `sh scripts/check_structure.sh`：**1 处提示** —— `DISCLAIMER.txt` 与 `assets/DISCLAIMER.txt` 不一致。

---

## 7. 修复建议（按 P0 / P1 / P2 排序）

> 本节仅为**建议**，本轮未实施任何修改。每项给出源码文件与具体修改位置。

### P0 —— 正确性缺陷（已确认失效 / 虚假覆盖）

**P0-1 短信发送 hook 签名全部错误，实际 0 拦截**
- 现状（`PrivacyApis.java:109-120`）：
  - `sendTextMessage` 写成 `(String,String,String,String,PendingIntent)`，
    实际为 `(String,String,String,PendingIntent,PendingIntent)`；
  - `sendMultipartTextMessage` 写成 `(String,String,List,List,List)`，
    实际为 `(String,String,ArrayList,ArrayList,ArrayList)`；
  - `sendDataMessage` 写成 5 参，实际为 6 参 `(String,String,short,byte[],PendingIntent,PendingIntent)`。
- 证据：对 android-29/35/37 用 `getDeclaredMethod` 探测，代码签名全部 `NoSuchMethodException`；
  正确签名全部命中（见 §6.1、附录 A.3）。
- 影响：`Hooks.ret` 记 `hook miss` 并返回 `false`；`sms=true` 的唯一调用方是
  `com.xiaomi.registration`（`HookEntry.java:477`），因此**三重点组件的短信出口、以及
  registration 的短信出口，均无任何拦截**。UI 无对应开关，故属「静默失效」。
- 建议修改位置：`PrivacyApis.java:109-120`，把三处 `Class<?>[]` 改为：
  `sendTextMessage` → `{String,String,String,PendingIntent,PendingIntent}`；
  `sendMultipartTextMessage` → `{String,String,ArrayList,ArrayList,ArrayList}`；
  `sendDataMessage` → `{String,String,short,byte[],PendingIntent,PendingIntent}`。
  并考虑对 6 参 `sendTextMessage(...,long)` 重载一并覆盖。

**P0-2 systemhelper 的应用列表只有「上报出口」被拦，采集入口未挂**
- 现状：`HookEntry.java:295` 传 `appList=false`，标准
  `getInstalledPackages/Applications` **未 hook**。当前拦截依赖
  `c.d.a.b.f#a(String,byte[],byte[])`（`HookEntry.java:296-298`）与域名 `dm.wo.com.cn`
  （`HookEntry.java:303`）。
- 影响：`Catalog.java:59` 把「应用列表」列为该组拦截内容。若 systemhelper 通过**其它域名**
  或**其它方法**上报应用清单（网络规则只列了 `dm.wo.com.cn`，严格模式默认关），则不覆盖。
  即：应用清单的**采集入口**未拦，只在**已知上报出口**拦。
- 建议：若确认 systemhelper 只从该出口上报，保持现状但修正文案口径；
  否则 `HookEntry.java:295` 改为 `appList=true`。

### P1 —— 覆盖缺口 / 可观测性

**P1-1 使用记录只覆盖旧重载**
- 位置：`PrivacyApis.java:60-63`。建议补 `queryEvents(android.app.usage.UsageEventsQuery)`
  （反射取类，取不到则跳过，写法可参照 flags 重载的 `findClass`）。

**P1-2 应用清单只覆盖两个方法家族**
- 位置：`PrivacyApis.java:44-58`。建议评估是否补 `queryIntentActivities/Services/Providers`、
  `getPackagesForUid`（注意返回值契约：空表）。

**P1-3 方法层缺少首次命中日志**
- 位置：`XsofHooks.java:21-244`、`MsaHooks.java:31-113`、`AnalyticsHooks.java:16-97`。
  这些 hook 的 `intercept` 未调用 `Hooks.noteHit`，只有安装计数。
- 影响：无法从日志区分「装上但没走到」与「真的拦住了」，与
  `docs/COMPATIBILITY.md` 的 `hook ok` vs `hit` 约定不一致。
- 建议：在关键出口（XSOF Provider/AIDL/写库/评分、MSA track、analytics 扫描）补
  `Hooks.noteHit(chain, "<layer>")`。

**P1-4 NIO / SocketChannel 出口未覆盖**
- 位置：`NetGuard.java:109-134` 只 hook 了 `java.net.Socket`。
- 建议：评估补 `java.nio.channels.SocketChannel#connect`（或至少文档标注为已知缺口）。

**P1-5 手机号 hook 含不存在的方法名**
- 位置：`PrivacyApis.java:81-83` 的 `getLine1NumberForSubscriber` 在公开 SDK 中不存在
  （SystemApi）。`retAllByName` 静默跳过，无害但属噪声。
- 建议：保留（运行时若存在则有效），或在 `docs/RULES.md` 注明。

**P1-6 作用域未知时 UI 误判为「未加入」**
- 位置：`DiagnosticsPage.java:45-51`（scope 为 null 时显示「未加入」）、
  `CategoriesPage.java:38-42,66-69`（scope 为 null 时行变暗、显示「未在作用域」）、
  `ModuleState.java:73-76`（`inScope` 在 `scope==null` 时返回 false）。
- 影响：LSPosed 未连接（`safeScope` 返回 null，`MainActivity.java:419-429`）时，
  已加入的组件会被显示为「未加入」并降透明度，误导用户。
- 建议：区分「未知（未连接）」与「未加入」两态。

### P2 —— 健壮性 / 契约 / 一致性

**P2-1 返回值契约风险**
- `PrivacyApis.java:162-186`：`rt == void.class || rt == boolean.class ? null : null` 恒为 `null`，
  分支无意义；若将来出现返回 `boolean` 的同名方法会 NPE（拆箱）。建议显式返回 `false`。
- `CaptureHooks.java:80-95`：`createVirtualDisplay` 返回 `null`，调用方未判空会崩溃；
  属有意阻断，建议在 `docs/RULES.md` 注明该契约取舍。
- `PrivacyApis.java:137-158`：Android ID 返回 `null`；AOSP 在无权限时亦返回 `null`，
  契约合法，但拼接型调用方可能 NPE。可考虑返回固定 16 位十六进制值。

**P2-2 `hookBySignature` 静默吞掉单个 hook 失败**
- 位置：`Hooks.java:172-202`。`m.hook(method)` 失败被 `catch (Throwable ignored)` 吞掉，
  仅统计总数；只有整类失败才记 `skipped classes=N`。
- 建议：对失败的方法记 `hook fail 类#方法` 一行，便于真机定位。

**P2-3 文档与实现口径不一致**
- `HookEntry.java:330`（dmreg 注释含「蓝牙」但无蓝牙 hook）、
  `Catalog.java:59`（systemhelper 描述含「应用列表」但 `appList=false`）。
- `dist/README.md:19` 称支持到 Android 17，而 `README.md:29`/`README.en.md:18` 止于 16，
  `docs/COMPATIBILITY.md:15` 也止于 16 —— **三处口径不一致**，建议统一为「止于 16，17 待验证」。

**P2-4 `DISCLAIMER.txt` 与 `assets/DISCLAIMER.txt` 不一致**
- `scripts/check_structure.sh` 已报出；应用内显示的是 `assets/` 那份。

**P2-5 I18n 英文缺 3 条**
- 上轮记录：`清空 logcat…`、`读安全中心 fraud_detection_data.db…`、`需要 root` 缺 EN 映射
  （`I18n.java`）。与本次隐私覆盖无关，列为一致性项。

---

## 8. 结论与待验证清单

### 8.1 结论

1. 三个重点组件的**设备标识类**（IMEI/MEID/IMSI/ICCID/Android ID/手机号/MAC/序列号）
   拦截在源码层是**完整且默认生效**的（`PrivacyApis.java:70-158`）。
2. **应用清单**拦截对 guardprovider/dmregservice 默认生效；对 systemhelper **未挂标准 API**（P0-2）。
3. **定位/基站/Wi-Fi/BSSID/通讯录**在三个组件上**默认全部关闭**（§4），默认配置下不拦。
4. **短信发送 hook 全部失效**（P0-1，跨版本 bug，非 Android 17 特有）。
5. **麦克风、摄像头、蓝牙、Binder/AIDL、Native、NIO/Cronet/WebView** 均无覆盖（§5）。
6. Android 17 / SDK 37 上，被 hook 的**公开 SDK 签名**经静态核对基本存在；
   但**厂商内部类、隐藏 API、混淆名**全部属「无法确认」，需真机验证（§6.2）。
7. 仓库自查脚本（`check_hooks.sh` 21 项）未覆盖 SMS 签名，因此**未能发现 P0-1**。

### 8.2 必须真机验证才能下结论的项（不得以静态分析代替）

- [ ] 三组件在 HyperOS 4 上是否真的走到被 hook 的方法（看 `hit` 日志）。
- [ ] `ApplicationPackageManager` 是否覆写并调用 `getInstalledPackages(int/flags)`。
- [ ] `ContextImpl#sendBroadcast` 在 Android 17 上的类名/方法是否存在（XMSF 路径）。
- [ ] `BaseDexClassLoader.pathList/dexElements/dexFile` 反射在 Android 17 上是否可用
      （决定类名扫描与方法层是否整体失效）。
- [ ] 方法层混淆名（`c.d.a.*`、`com.miui.analytics.onetrack.*`、XSOF 行为库 helper）
      在 HyperOS 4 上的实际形态。
- [ ] LSPosed 在 Android 17 上对 libxposed API 102 / staticScope 的支持。
- [ ] 三组件是否存在 NIO/Cronet/WebView/Native 上报路径。

> 以上均**未**验证。本报告不声称任何数据已发生上传，也不声称任何 hook 的真实命中率。

---

## 附录 A：本次审计使用的证据命令与结果（只读）

### A.1 仓库状态

```
git rev-parse --abbrev-ref HEAD   -> main
git rev-parse HEAD                -> e73b9ffa76ecadd93f44ebd4f8c83acc3ca23fa4
git status --porcelain | wc -l    -> 54   (26 ' M', 28 '??')
```

### A.2 仓库自查脚本

```
ANDROID_JAR=/tmp/sdk37/android-37.0/android.jar sh scripts/check_hooks.sh
  -> 共 21 项，失败 0 项，跳过 0 项
sh tests/run.sh
  -> ALL PASS (7 unique hit lines) / ALL PASS (concurrent dedup: 2 scenarios)
sh scripts/check_structure.sh
  -> 发现 1 处可能需要确认的地方：DISCLAIMER.txt 与 assets/DISCLAIMER.txt 不一致
```

### A.3 SMS 签名探测（反射，android-29/35/37）

```
--- as currently coded ---
MISS  sendTextMessage(String,String,String,String,PI)            (NoSuchMethodException)
MISS  sendMultipartTextMessage(String,String,List,List,List)     (NoSuchMethodException)
MISS  sendDataMessage(String,String,short,byte[],PI)             (NoSuchMethodException)
--- actual AOSP signature ---
FOUND sendTextMessage(String,String,String,PI,PI)
FOUND sendMultipartTextMessage(String,String,ArrayList,ArrayList,ArrayList)
FOUND sendDataMessage(String,String,short,byte[],PI,PI)
（API 29 / 35 / 37 结果一致）
```

### A.4 其他平台签名核对（`javap`，android-37）

```
android.telephony.TelephonyManager#getNeighboringCellInfo        -> 不存在（29/35/37 皆无）
android.telephony.TelephonyManager#getLine1NumberForSubscriber   -> 不存在（SystemApi）
android.app.usage.UsageStatsManager#queryEvents(UsageEventsQuery)-> 存在（35+），代码未 hook
android.content.pm.PackageManager$PackageInfoFlags/$ApplicationInfoFlags -> 存在（33+）
android.app.ApplicationPackageManager                            -> SDK 中不存在（class not found）
android.content.ContextImpl                                      -> SDK 中不存在（class not found）
```

### A.5 其他源码事实

```
APK 内无 lib/、无 .so（纯 Java）；classes.dex 222068 B
src/ 中无 loadLibrary / native 方法声明
src/ 中无 AudioRecord/MediaRecorder/CameraManager/Bluetooth* 相关 hook
src/ 中无 ContentResolver#call / IBinder / Parcel 相关 hook
AndroidManifest.xml:7  minSdkVersion=29  targetSdkVersion=34
META-INF/xposed/module.prop  minApiVersion=101 targetApiVersion=102 staticScope=true
```

---

*报告生成：2026-11-10。本次为只读审计，未修改任何源码 / 构建脚本 / 版本号 / Tag / Release，
未操作 main，未合并，未发布，未进行真机测试。等待人工审核，不进入修复阶段。*
