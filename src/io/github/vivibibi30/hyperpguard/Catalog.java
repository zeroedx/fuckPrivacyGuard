package io.github.vivibibi30.hyperpguard;

/**
 * 功能目录：分组与子项的唯一来源。
 *
 * 这里的内容与拦截逻辑一一对应，key / 默认值 / 包名必须与 {@link Config} 和
 * {@code HookEntry.dispatch()} 保持一致，改任意一项都要同步三处。
 */
final class Catalog {

    static final String URL_REPO = "https://github.com/zeroedx/fuckPrivacyGuard";
    static final String URL_UPSTREAM = "https://github.com/vivibibi30/hyperpguard";
    static final String URL_CUSTOM_RULES_DOC =
            URL_REPO + "/blob/main/docs/CUSTOM-RULES.md";
    static final String URL_ISSUES = URL_REPO + "/issues";
    static final String URL_RELEASES = URL_REPO + "/releases";
    static final String URL_MODULE =
            "https://modules.lsposed.org/module/io.github.vivibibi30.hyperpguard";
    static final String API_LATEST =
            "https://api.github.com/repos/zeroedx/fuckPrivacyGuard/releases/latest";

    static final class Item {
        final String name, desc, key;
        final boolean def;

        Item(String name, String desc, String key, boolean def) {
            this.name = name;
            this.desc = desc;
            this.key = key;
            this.def = def;
        }
    }

    static final class Group {
        final String title, pkg, key, note;
        final Item[] items;

        Group(String title, String pkg, String key, Item[] items) {
            this(title, pkg, key, items, null);
        }

        Group(String title, String pkg, String key, Item[] items, String note) {
            this.title = title;
            this.pkg = pkg;
            this.key = key;
            this.items = items;
            this.note = note;
        }
    }

    /** 界面文案：中文原文查表，英文环境返回英文 */
    private static String T(String zh) {
        return I18n.t(zh);
    }

    /** 组总开关默认开启：状态由 LSPosed 真实作用域回填，不看本地默认值 */
    static final Group[] GROUPS = {
            new Group(T("SystemHelper"), "com.mobiletools.systemhelper", Config.K_G_SYSHELPER, new Item[]{
                    new Item(T("拦截上报"), T("dm.wo.com.cn，设备 / SIM / 基站 / 应用列表"), Config.K_SYSHELPER, true),
                    new Item(T("整进程断网"), T("默认关：只拦上报域名与方法层。开启后该进程所有外网请求一并拦截，只留本机回环"), Config.K_SYSHELPER_ALLNET, false),
                    new Item(T("定位（GPS）"), T("只对本应用：位置查询返回空"), Config.K_LOC_SH, false),
                    new Item(T("基站信息"), T("只对本应用：基站查询返回空，不影响信号显示"), Config.K_CELL_SH, false),
                    new Item(T("Wi-Fi 扫描"), T("只对本应用：扫描列表置空，SSID / BSSID 清空"), Config.K_WIFI_SH, false),
            }, T("权限在开机激活时确认；本模块只拦上报，不回收已授予的权限")),
            new Group(T("系统安全组件"), "com.miui.guardprovider", Config.K_G_GUARD, new Item[]{
                    new Item(T("应用列表云查"), T("仅拦 flash.sec.miui.com，不动病毒库"), Config.K_GUARD_APPLIST, true),
                    new Item(T("AI 通话预警云检测"), T("detection-aigc.engine.miui.com"), Config.K_GUARD_AIGC, true),
                    new Item(T("反诈配置拉取"), T("fraud-detection.engine.miui.com"), Config.K_GUARD_FRAUD, true),
                    new Item(T("云查上传（Flamingo）"), T("virus-engine.sec.miui.com；关闭后不再上传应用清单，本地病毒库照常"), Config.K_GP_CLOUDSCAN, false),
                    new Item(T("静默录屏拦截"), T("阻止 createVirtualDisplay 整屏录制，不影响正常截屏"), Config.K_GP_SCREENGUARD, true),
                    new Item(T("引擎日志上传"), T("logupdate.avlyun.sec.miui.com；只影响日志回传，不影响本地查毒与病毒库更新"), Config.K_GP_ENGINELOG, true),
                    new Item(T("定位（GPS）"), T("只对本应用：位置查询返回空"), Config.K_LOC_GP, false),
                    new Item(T("Wi-Fi 扫描"), T("只对本应用：扫描列表置空，SSID / BSSID 清空"), Config.K_WIFI_GP, false),
                    new Item(T("通讯录 / 通话读取"), T("只对本应用：查询返回空；来电识别会受影响"), Config.K_SOCIAL_GP, false),
            }, T("AI 反诈逻辑在本组件的 AiGuardService 内，用于通话场景；SystemHelper 只做运营商侧上报")),
            new Group(T("安全中心"), "com.miui.securitycenter", Config.K_G_SECCENTER, new Item[]{
                    new Item(T("XSOF 行为查询拦截"), T("第三方查不到截图 / 通话 / 屏幕共享记录"), Config.K_XSOF, true),
                    new Item(T("反诈配置拉取"), T("fraud-detection.engine.miui.com"), Config.K_SECCENTER, true),
                    new Item(T("病毒库云查"), T("virus-engine.sec.miui.com，关闭后不再云端查毒"), Config.K_SC_VIRUS, false),
                    new Item(T("XSOF 合作方鉴权"), T("framework.sec.miui.com，可能影响第三方调用"), Config.K_SC_FRAMEWORK, false),
                    new Item(T("月报与配置上报"), T("data.sec.miui.com，含防窥/广告等接口"), Config.K_SC_DATA, false),
                    new Item(T("防窥分类上报"), T("上传非系统应用包名数组；关闭后防窥识别可能变差"), Config.K_SC_APPCLASS, false),
                    new Item(T("志愿者传感器上报"), T("OAID + 经纬度 + 时间；非志愿者无影响"), Config.K_SC_VOLUNTEER, true),
                    new Item(T("地震/报警/EPS 定位"), T("关闭后地震预警、紧急报警定位可能失效"), Config.K_SC_LOCATION, false),
                    new Item(T("ADB 授权验证"), T("srv.sec.miui.com/data/adb；关闭后 ADB 安装授权可能失败"), Config.K_SC_ADB, false),
                    new Item(T("行为库写入拦截"), T("拦截 screenshot / screen_share / voip_record 等 8 张表的写入"), Config.K_SC_DBGUARD, true),
                    new Item(T("设备标识拦截"), T("IMSI / ICCID / 手机号 / IMEI；打开后剩余流量、套餐查询会失效"), Config.K_SC_IDS, false),
                    new Item(T("广告位与推送下发"), T("adv 广告位、网络助手推送、满意度问卷；关闭后这几处不再拉取配置"), Config.K_SC_MISC, true),
                    new Item(T("统计埋点上报"), T("tracking.*.miui.com / sdkconfig.*.xiaomi.com；只影响统计，不影响功能"), Config.K_SC_TRACKING, true),
                    new Item(T("定位（GPS）"), T("只对本应用：位置查询返回空"), Config.K_LOC_SC, false),
                    new Item(T("通讯录 / 通话 / 短信读取"), T("只对本应用：查询返回空；来电识别会受影响"), Config.K_SOCIAL_SC, false),
            }),
            new Group(T("小米统计"), "com.miui.analytics", Config.K_G_ANALYTICS, new Item[]{
                    new Item(T("拦截上报"), T("tracking / om.tracking / sdkconfig"), Config.K_ANALYTICS, true),
                    new Item(T("阻断 DoH 旁路"), T("dns.google 与 8.8.8.8，防解析绕过域名规则"), Config.K_ANALYTICS_DOH, true),
                    new Item(T("全断网（推荐）"), T("该进程所有外网请求一并拦截；云端换域名也不用管"), Config.K_ANALYTICS_ALLNET, true),
            }),
            new Group(T("应用安装器"), "com.miui.packageinstaller", Config.K_G_PKG, new Item[]{
                    new Item(T("AVL 上报"), T("网络层 + 方法层；关闭后安装信息不再上传，病毒扫描结果可能缺失"), Config.K_PKG_AVL, true),
                    new Item(T("安装活跃统计"), T("网络层 + 方法层；关闭后不再上报安装活跃数据，无功能影响"), Config.K_PKG_STAT, true),
                    new Item(T("安装页广告位"), T("网络层 + 方法层；关闭后安装页不再拉取推荐位配置"), Config.K_AD_PKG, true),
                    new Item(T("设备标识（OAID）"), T("MSA SDK 返回空串；只影响广告归因，不影响安装"), Config.K_PKG_OAID, true),
                    new Item(T("云端配置下发"), T("阻断安装器云端参数，含「未查询到备案」提示；关闭后按内置默认值展示"), Config.K_PKG_CLOUD, true),
                    new Item(T("风险校验查询"), T("verify / tz.sec.xiaomi.com；打开后安装风险检测失效，包名与哈希不再外发"), Config.K_PKG_RISK, false),
                    new Item(T("游戏防篡改检查"), T("game/interceptcheck；打开后游戏安装时的完整性校验失效"), Config.K_PKG_GAME, false),
            }),
            new Group(T("应用商店"), "com.xiaomi.market", Config.K_G_AD_MARKET, new Item[]{
                    new Item(T("广告配置下发"), T("sdkconfig.ad.xiaomi.com"), Config.K_AD_MARKET, true),
                    new Item(T("使用统计上报"), T("tracking.miui.com/track；只影响统计，不影响下载与安装"), Config.K_MARKET_TRACK, true),
            }),
            new Group(T("小米服务框架"), "com.xiaomi.xmsf", Config.K_G_AD_XMSF, new Item[]{
                    new Item(T("广告接口"), T("adv.sec.miui.com"), Config.K_AD_XMSF, true),
                    new Item(T("使用统计上报"), T("tracking.miui.com/track；只影响统计，不影响推送与登录"), Config.K_XMSF_TRACK, true),
            }),
            new Group(T("广告组件（MSA）"), "com.miui.systemAdSolution", Config.K_G_MSA, new Item[]{
                    new Item(T("广告使用画像"), T("读取应用今日是否打开、最后使用时间；按少推广告的一侧返回"), Config.K_MSA_USAGE, true),
                    new Item(T("广告埋点上报"), T("OneTrack 的 track / adTrack 方法直接返回空"), Config.K_MSA_TRACK, true),
            }),
            new Group(T("运营商服务"), "com.xiaomi.registration", Config.K_G_REGISTRATION, new Item[]{
                    new Item(T("设备注册（zzhc）"), T("IMEI/IMSI/ICCID；关闭后 VoLTE / 运营商功能可能异常"), Config.K_REG_ZZHC, false),
                    new Item(T("明文 CTEI 上报"), T("http://typdm.tydevice.com，IMEI / MAC / BSSID 明文 GET"), Config.K_REG_TYPDM, true),
                    new Item(T("定位（GPS）"), T("只对本应用：位置查询返回空"), Config.K_LOC_REG, false),
                    new Item(T("基站信息"), T("只对本应用：基站查询返回空，不影响信号显示"), Config.K_CELL_REG, false),
            }),
            new Group(T("移动 DM（RegService）"), "com.miui.dmregservice", Config.K_G_DMREG, new Item[]{
                    new Item(T("拦截上报"), T("fxltsbl.com；App 清单 / SIM 标识"), Config.K_DMREG, true),
                    new Item(T("基站信息"), T("只对本应用：基站查询返回空，不影响信号显示"), Config.K_CELL_DM, false),
                    new Item(T("Wi-Fi 扫描"), T("只对本应用：扫描列表置空，SSID / BSSID 清空"), Config.K_WIFI_DM, false),
            }),
            new Group(T("系统诊断（MiSight）"), "com.miui.misightservice", Config.K_G_MISIGHT, new Item[]{
                    new Item(T("日志上传"), T("feedback.miui.com/misight/upload；关闭后主动反馈传不了日志包"), Config.K_MISIGHT_UPLOAD, true),
                    new Item(T("售后检测"), T("port.sec.miui.com/mqsas；关闭后售后远程检测拿不到日志"), Config.K_MISIGHT_MQSAS, true),
                    new Item(T("客服诊断"), T("chat.kefu.mi.com/openapi/misight/diagnostic；关闭后在线客服远程诊断不可用"), Config.K_MISIGHT_KEFU, true),
                    new Item(T("事件日志上传"), T("event-log.cnbj1.mi-fds.com；诊断事件不再回传，本地记录不受影响"), Config.K_MISIGHT_EVENTLOG, true),
            }, T("触发条件是用户主动反馈或售后远程诊断；本地记录不等于已经上传")),
            new Group(T("小米可信服务"), "com.xiaomi.trustservice", Config.K_G_TRUST, new Item[]{
                    new Item(T("屏蔽 MRM 认证链"), T("会影响设备证书 / 查找设备"), Config.K_TRUST, false),
                    new Item(T("设备证书/密钥上报"), T("find.api.micloud.xiaomi.net；关闭后查找设备、设备证书可能异常"), Config.K_TRUST_FIND, false),
            }),
            new Group(T("翻译组件"), "com.xiaomi.aiasst.vision", Config.K_G_AIASST, new Item[]{
                    new Item(T("语音识别上传"), T("只断 /speech/ 路径，保留文字翻译"), Config.K_AIASST, false),
            }),
    };

    private Catalog() {
    }
}
