# 兼容性与覆盖范围

这份文档说明本模块**当前**的已知支持范围、已覆盖的拦截能力、以及尚未覆盖或尚未验证的能力。
它区分两类结论：

- **源码确认**：能在本仓库源码 / 构建配置里直接核对的事实。
- **待设备验证**：需要目标机型、目标系统版本的组件产物或运行时日志才能确认，本仓库目前**没有**这类证据。

没有标注来源的推断不作为结论。

## 一、当前已知支持范围（源码确认）

| 项 | 值 | 来源 |
|---|---|---|
| 声明支持的 Android / HyperOS | Android 10 – Android 16 的 HyperOS | `README.md` |
| LSPosed / API | LSPosed 2.2.x（libxposed API 100+） | `README.md` |
| 模块声明的 libxposed API | `minApiVersion=101`、`targetApiVersion=102` | `META-INF/xposed/module.prop` |
| 编译 SDK | android-35（`build.sh` / CI 使用 android-35 的 `android.jar`） | `build.sh`、`.github/workflows/build.yml` |
| `minSdkVersion` / `targetSdkVersion` | 29 / 34 | `AndroidManifest.xml`、`build.sh` |
| 作用域 | `staticScope=true`，13 个包（见 `META-INF/xposed/scope.list`） | `META-INF/xposed/module.prop`、`META-INF/xposed/scope.list` |

## 二、Android 17 / SDK 37 的状态

本节严格区分两件事：

- **源码适配**：本仓库源码 / 构建配置里能直接核对、并已针对 Android 17 做过处理的部分。
  属于「源码确认」。
- **真机验证**：需要目标机型（Xiaomi 17 / HyperOS 4）的运行时日志或组件产物才能确认的部分。
  本仓库目前**没有**这类证据，一律标为「待设备验证」。

**结论：源码适配已做（见下），真机验证未做。** 在 Android 17 / HyperOS 4 上的实际拦截效果仍然未知。

### 2.1 源码适配（源码确认）

- 编译仍使用 android-35 的平台 jar，`targetSdkVersion` 为 34；声明支持范围止于 Android 16。
- 已用**真实的 android-37.0 平台 jar** 静态核对被 hook 的公开方法签名，并把
  旧版本上不存在、新版本新增的重载按「反射取类、取不到就跳过」的方式做了兼容
  （见 2.2、2.3）。这只是「签名存在性」的源码级证据，**不是真机行为验证**。
- 短信发送 hook 不再写死某一个重载，改为按签名谓词匹配（见 2.3），
  Android 10–17 上存在的每个发送重载都会命中。

### 2.2 仍需真机验证的项

- 目标组件在 Android 17 / HyperOS 4 上的实际调用链、类名与方法签名；
- 方法层里按混淆名或按包名前缀定位的 hook（如 `c.d.a.*`）是否仍然命中；
- `MediaProjection#createVirtualDisplay`、`Settings.Secure.getString` 等被 hook 的方法签名是否变化；
- 反射使用的隐藏 API（`dalvik.system.BaseDexClassLoader` 的 `pathList` / `dexElements`、`android.app.ActivityThread` 的相关字段与方法、`android.app.LoadedApk#makeApplication`）在新版本上是否仍可用；
- LSPosed 在 Android 17 上对 libxposed API 102 的支持情况。

**本模块不宣称已经完整兼容 Android 17；源码适配不等于真机验证通过。**

### 应用清单 Hook 的跨版本签名核对（源码确认）

对 `getInstalledPackages` / `getInstalledApplications` 的四组重载，用 Google 官方平台包
静态核对（`sh scripts/check_hooks.sh`，`ANDROID_JAR` 指向对应平台）。

说明：脚本 `javap` 的是**公开基类** `android.content.pm.PackageManager` 的声明。
运行时被 hook 的是框架内部类 `android.app.ApplicationPackageManager`，它不在 SDK
平台包里（`javap` 会报 class not found），因此下表只能确认**基类声明了这些重载**，
不能证明框架类实际覆写；后者需真机日志确认。

| API | `(int)` 重载 | flags 重载（`PackageInfoFlags` / `ApplicationInfoFlags`） |
|---|---|---|
| 29（Android 10） | 存在 | 不存在 |
| 32（Android 12L） | 存在 | 不存在 |
| 33（Android 13） | 存在 | 存在 |
| 35（Android 15） | 存在 | 存在 |
| 36（Android 16） | 存在 | 存在 |
| 37（Android 17） | 存在 | 存在 |

结论：**flags 重载自 API 33 起在基类上存在**；代码用字符串反射取该类，旧系统（< 33）
上该类**本就不存在**（并非「取不到」），只挂 `(int)` 重载，不会触发
`NoClassDefFoundError`（`javap` 已确认编译产物里 flags 类型只作为字符串常量出现，
没有 `.class` 字面量）。

### 短信发送签名的跨版本核对（源码确认）

`SmsManager` 的发送入口**不再写死重载**，改为按「方法名 + 参数结构 + void 返回」的
签名谓词（`Hooks.isSmsSend`）匹配。旧代码写死了三个重载，且参数个数 / 类型与 AOSP 不符
（`getDeclaredMethod` 抛 `NoSuchMethodException`，整段静默失效）；现在每个真实存在的重载
都会被命中。下表用 `javap` 在对应平台 jar 上核对：

| API | `sendTextMessage(String,String,String,PI,PI[,long])` | `sendMultipartTextMessage` | `sendDataMessage(String,String,short,byte[],PI,PI)` |
|---|---|---|---|
| 29（Android 10） | 存在（5 参） | 存在（`ArrayList` 5 参） | 存在 |
| 32（Android 12L） | 存在（5 / 6 参） | 存在（`ArrayList` 5 参、`List` 6 / 7 参） | 存在 |
| 33（Android 13） | 存在（5 / 6 参） | 存在 | 存在 |
| 35（Android 15） | 存在（5 / 6 参） | 存在 | 存在 |
| 36（Android 16） | 存在（5 / 6 参） | 存在 | 存在 |
| 37（Android 17） | 存在（5 / 6 参） | 存在 | 存在 |

`PI` = `android.app.PendingIntent`。谓词还覆盖 `sendTextMessageWithoutPersisting`。
核对项由 `sh scripts/check_hooks.sh`（设置 `ANDROID_JAR`）自动执行。

### 使用记录查询重载的跨版本核对（源码确认）

`UsageStatsManager` 自 **API 35（Android 15）** 起新增 `queryEvents(UsageEventsQuery)` 重载，
走这条入口的调用方会绕过 `queryEvents(long, long)`。代码用反射取 `UsageEventsQuery` 类，
取不到（< 35）就只挂旧重载：

| API | `queryEvents(long,long)` | `queryEvents(UsageEventsQuery)` |
|---|---|---|
| 29 / 32 / 33 | 存在 | 不存在（旧系统） |
| 35 / 36 / 37 | 存在 | 存在 |

## 三、已覆盖的拦截能力（源码确认）

拦截分四层，彼此独立；某一层失配不影响其余层。

### 网络出口（`NetGuard.java`）

- `java.net.URL#openConnection`（支持「域名 + 路径前缀」规则）
- `java.net.InetAddress#getByName` / `getAllByName`
- 实现 `okhttp3.Dns` 接口的自定义 DNS 的 `lookup`
- `java.net.Socket` 的全部构造器与 `connect`

规则为「域名后缀 + 可选路径前缀」，按包分组配置；「整进程断网」为可选项。

### 隐私 API（`PrivacyApis.java`）

- 应用清单：`ApplicationPackageManager#getInstalledPackages` / `getInstalledApplications` 的
  `int` 与 `PackageInfoFlags` / `ApplicationInfoFlags` 两组重载，均返回空表；
  SystemHelper 现已一并挂上标准应用清单入口（此前只拦厂商上报出口）
- 使用记录：`UsageStatsManager#queryEvents`（`(long,long)` 与 API 35+ 的
  `(UsageEventsQuery)` 两个重载）、`queryUsageStats`
- 设备标识：`TelephonyManager`（含 `miui.telephony` 两个扩展类）的 IMEI / MEID / IMSI / ICCID、
  `WifiInfo#getMacAddress`、`getLine1Number*`、`Build#getSerial`、`Settings.Secure` 的 `android_id`
- 短信发送：`SmsManager#sendTextMessage` / `sendTextMessageWithoutPersisting` /
  `sendMultipartTextMessage` / `sendDataMessage` 的**全部重载**（按签名谓词匹配，非写死重载）
- 位置 / 基站 / Wi-Fi：`LocationManager`、`TelephonyManager` 小区接口、`WifiManager#getScanResults`、
  `WifiInfo#getSSID` / `getBSSID`（**默认关闭**，按应用单独开启）
- 通讯录 / 通话 / 短信读取：`ContentResolver#query` 对 `call_log` / `sms` / `mms` / `contacts` 返回空
  （**默认关闭**）
- XMSF 代发广播：`ContextWrapper` / `ContextImpl#sendBroadcast` 过滤特定 action

### 本地存储与录屏（`CaptureHooks.java`）

- 行为库八张表（`screenshot` / `screen_share` / `call_forwarding` / `airplane_mode` /
  `fraud_phone` / `voip_record` / `fraud_url` / `install_app`）的 `SQLiteDatabase` 写入与 `execSQL`
- `MediaProjection#createVirtualDisplay` 不建立（静默录屏拦截）

### 方法层（`XsofHooks.java`、`MsaHooks.java`、`AnalyticsHooks.java`、`HookEntry`）

- XSOF：Provider / AIDL / 写库 / 评分四层，以及 MRM 服务屏蔽（MRM 默认关闭）
- MSA：广告使用画像与 OneTrack 埋点
- 统计方法层与签名扫描
- SystemHelper：上报与写库方法层
- 安装器：OAID、广告缓存、活跃统计、AVL 上报等方法层

方法层按「修饰符 + 参数类型 + 返回类型 + 声明类所在包」组合判定，尽量不写死混淆类名；
无法避免时保留签名扫描回退，失配时记 `hook miss` 日志。

### 日志含义（源码确认）

| 前缀 | 含义 |
|---|---|
| `attach <包名>` | 模块注入该进程成功 |
| `hook ok` / `hook miss` | 某个方法 hook **安装**成功 / 类名或签名不匹配 |
| `类#方法(参数) hit` | 某个 hook **实际被调用**（首次命中记一条，之后静默）；键含类名、方法名与参数签名，只记方法身份，不记实参内容 |
| `X(host)` / `X(host+path)` | 域名规则命中（路径规则带 `+path`） |
| `X(strict)` | 整进程断网生效 |
| `? <tag> <host>` | 「记录未拦截访问」记录：访问未被拦截 |

`hook ok` 只表示 hook 已安装，**不代表该进程实际走到了该调用点**；是否命中看 `hit` 行。
`hit` 行按「类名#方法名(参数类型)」去重（可选带语义标签前缀，如 `applist`、`db write`、
`sms`、`xsof query`、`msa track`、`analytics usage`），同一方法的不同重载各记一条，
高频调用不会刷屏。方法层（XSOF / MSA / 统计）与 SystemHelper 的拦截回调里也会调用
`noteHit`，因此「安装成功」与「实际被调用」在日志里是两条不同的行。

### 可重复执行的自查（源码确认）

- `sh tests/run.sh`：纯 JVM 单元测试，覆盖 `noteHit` 的首次命中去重、去重键含类名/方法名/
  参数签名、不记录实参内容，以及多线程并发去重；另含 `Hooks.isSmsSend` 的签名谓词测试
  （覆盖 Android 10–17 上存在的每个短信发送重载，并验证旧代码写错的签名被正确排除）。
  不需要设备或 SDK。
- `sh scripts/check_hooks.sh`：静态核对 Hook 注册与默认开关；设置 `ANDROID_JAR` 后
  额外用 `javap` 校验签名在目标平台 jar 里真实存在（含应用清单、短信发送、使用记录三类）。
  无法判定的项（未设 `ANDROID_JAR`、拿不到真实 API 级别、找不到 `javap`）记为 `SKIP`，
  不计入通过。


## 四、尚未覆盖或尚未验证的能力

以下能力**本模块目前不覆盖**，或**没有调用链证据**，因此不做保证。列在这里是为了避免把「四层拦截」误读成「全覆盖」。

### 未覆盖（源码确认不存在对应 hook）

- **Native / HAL 数据路径**：所有 hook 都在 Java 层。使用 native 网络栈（Cronet、WebView 等）、
  直接读 `/proc` / sysfs / HAL 的路径不受影响。
- **NIO / SocketChannel 出口**：网络层只 hook 了 `java.net.Socket` 与 `java.net.URL`；
  `java.nio.channels.SocketChannel#connect` 等 NIO 出口未覆盖（默认走阻塞 `Socket` 的调用不受影响）。
- **Binder / AIDL 数据出口**：没有针对 `ContentResolver#call`、`IBinder`、`Parcel` 或
  `IPackageManager` 的通用 hook。跨进程代发（组件把数据交给另一个进程再统一上报）不在覆盖内。
- **麦克风**：没有 `AudioRecord` / `MediaRecorder` / `AudioManager` 相关 hook。
- **摄像头**：没有 `Camera` / `CameraManager` / `ImageReader` 相关 hook。
- **蓝牙**：没有蓝牙相关 hook（含蓝牙侧 BSSID / MAC）。

### 待设备验证（无调用链证据）

- 作用域外组件的行为：只有 `scope.list` 里的 13 个包会被注入。
- 各组件内部通过未识别通道采集或上报的数据（例如尚未记录到域名的接口、经配置下发的新地址）。
- Android 17 / HyperOS 4 上被 hook 方法是否存在、签名是否变化（见第二节）。

### 已知的设计取舍（源码确认）

- 会影响系统功能的规则一律**默认关闭**，需要按应用单独开启（如定位、基站、Wi-Fi、通讯录、
  设备标识拦截、云查上传等）；默认关闭即默认不拦。
- 域名规则是「已知域名」的白名单式拦截，未列入的域名不会拦；「整进程断网」默认只对小米统计开启。
- 方法层依赖组件版本；类名变化时网络层与隐私 API 层不受影响。

## 五、相关文档

- 拦截分层的结构说明：`docs/ARCHITECTURE.md`
- 各条规则的判据与默认开关理由：`docs/RULES.md`
- 免责声明与使用边界：`DISCLAIMER.txt`、`README.md`
