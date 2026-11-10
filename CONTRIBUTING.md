# 参与与反馈

[简体中文](CONTRIBUTING.md) · [English](CONTRIBUTING.en.md)

## 提交 issue 前

- 先搜索[已有 issue](https://github.com/zeroedx/fuckPrivacyGuard/issues?q=is%3Aissue)，同一问题请在原 issue 下补充，不要重复开单
- 先在模块里点「自检」，确认总开关、作用域和已注入进程
- 日志要脱敏：IMEI、IMSI、ICCID、手机号、账号、文件路径里的用户名
- 只贴与本问题相关的行，不要上传整份 logcat

## 不要上传的内容

- 厂商组件的 APK 及其反编译产物（SystemHelper、GuardProvider、安全中心、移动 DM 等）
  这类文件是厂商的复制件，传到本仓库涉及版权问题；需要分析时请在自己设备上用 `pm path` 取
- 设备数据库、备份、完整应用列表、通话与短信内容

## 代码贡献

- 先看 [AGENTS.md](AGENTS.md) 与 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，改完跑一次 `sh scripts/check_structure.sh`

- 改动后请确认 `sh build.sh` 仍可复现构建
- 只改与问题相关的部分，不要夹带格式化
- 新增拦截目标时写明判据（域名、方法签名、表名）；类名与方法签名属事实，可以写，
  但不要贴方法体的 smali、反编译源码或解包后的资源文件

## 商业化

本仓库不接受以本项目名义的付费分发、捆绑安装或「赞助解锁功能」。第三方 fork 按 GPL-3.0 自行处理，但不要使用本项目的名称与图标。

## 更新节奏

- 只有「拦截失效、崩溃、发现新的上报通道、系统大版本适配」才会发版
- 文案、界面措辞、日志格式这类改动会并入下一次实质更新，不单独出包
- 更新频率已主动下调：开发与验证主要依赖 AI 辅助与自费真机测试，长期成本由作者个人承担
- issue 会看，但不承诺响应时间与实现排期；未经确认的功能请求可能长期不做，或被合并到相近改动
