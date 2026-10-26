# 1.12.0 简易导航与仪表歌词通道（widgetd）

## 根因与实现

1. 实车验证过的「简易导航+歌词」链路在仪表侧的接收端原是固件反编译提取的 `overlay-service` 二进制，仓库纪律禁止提交提取 ELF/固件原件，且去鉴权需要仪表端配合改动。因此仪表端以可移植 C99 源码重写独立守护进程 `widgetd`。
2. app 侧采集/中继层逐行移植实车实现：导航广播（高德/百度/WeCar）、定制地图 autosdk 服务 1 秒轮询、歌词广播+MediaSession 轮询+NotificationListenerService 通知采集、在线歌词/封面拉取；组包 OP 字符串与 JSON 字段名与实车实现逐字符一致。协议零鉴权：三条通道一律不带 token、不带 KEY、不做任何校验，只考虑稳定性。
3. 传输换底到本仓库 NCM 用户态栈：`NcmSessionHub` 引用计数共享单一 `UsbNcmSession`（USB 独占打开，widget 通道与视频 DCV1 通道共享同一会话），`WidgetService` 前台服务持有会话使导航/歌词在未投屏时可独立工作。三通道端口定版 31770（hmi）/31771（navigation）/31772（music），仪表侧监听 192.168.77.1。
4. `widgetd` 并入现有 `dip-instrument` 模块启动链（`startup.ksh` 校验+拷贝，与 instrument-receiver 同等流程），owner wrapper 有界重启+单实例锁；JSON 行帧、OP 路由、`ttl_ms` 租约过期自动让位、配置原子持久化；渲染层为注入式 port 接口，主机测试用 fake port，Screen/字体后端在目标阶段接入。
5. 产品 UI：投屏设置页新增「简易导航」「仪表歌词」两个开关，主开关只跟随用户操作；上电按记忆意图恢复；投屏开始/结束按 castHide 配置联动临时隐藏/恢复，投屏互斥由租约与意图状态闭环。`MusicListener` 以 NLS 注册，未授予通知权限时歌词可能为空。

## 变化范围

APK code51；新增 `phase1/android` widget 包（采集/中继/协议/链路）、`NcmSessionHub`、`WidgetService`、`WidgetApp` 引导，`ProductActivity` 开关接线与 `AndroidManifest` 服务注册；`ContinuousProjectionTask` 仅在 castHide 配置开启时联动隐藏。新增 `phase1/qnx/widgetd` 源码、主机测试脚本与 `ref2-noauth` 启动脚本。视频 DCV1 协议、RWV7 仪表 App 其余成员、MCU/参考 OS/Boot/USB 角色不改。

## 升级与检查

成对更新 APK 与仪表 App（widgetd 随 dip-instrument 启动链部署）。台架：APK 开启开关后对 31771/31772 抓包与实车基准逐字段对照（仅端口差异，无 token 字段）；检查仪表出框、日夜主题、歌词/封面/进度、投屏互斥、上电意图恢复、断流恢复。基础投屏/主屏操作按旧流程回归，不增加拔线/时长门槛。

## 证据边界

JVM 单测与 debug 构建全绿；widgetd 主机协议测试 53/53（AddressSanitizer/UBSan，fake 渲染端口），是离线协议回归。`lintDebug` 因 lint 工具自身 JavaDoc 解析缺陷崩溃，基线同样复现，与本次改动无关。台架抓包对照、仪表端 Screen/字体真实出框、实车 gate（导航显示、歌词/封面/进度、投屏互斥、上电意图恢复、断流恢复）均未执行；离线结果不作为实车验收。
