# WeChatMonitor 模块与 API 文档

## 目标

WeChatMonitor 是面向微信进程 `com.tencent.mm` 的 libxposed API 101 模块，负责在微信运行时安装消息、支付、事件总线与界面相关 Hook，并把命中信息输出到 logcat 与本地 UTF-8 日志文件。

## libxposed API 101 接入

- 依赖：`io.github.libxposed:api:101.0.1`，使用 `compileOnly` 引入。
- 最低系统：libxposed API 当前声明 minSdk 26，项目 `minSdkVersion` 同步为 26。
- 入口文件：`app/src/main/resources/META-INF/xposed/java_init.list`。
- 模块属性：`app/src/main/resources/META-INF/xposed/module.prop`。
- 静态作用域：`app/src/main/resources/META-INF/xposed/scope.list`，当前只声明 `com.tencent.mm`。
- 入口类：`com.wechat.monitor.WeChatMonitorHook` 继承 `io.github.libxposed.api.XposedModule`。

## 入口生命周期

### `WeChatMonitorHook.onModuleLoaded(ModuleLoadedParam)`

模块进入目标进程时调用一次，创建 `MonitorContext`，记录进程名、框架名称、框架 API 版本与构建标识。

### `WeChatMonitorHook.onPackageLoaded(PackageLoadedParam)`

当加载包名为 `com.tencent.mm` 时执行，使用 `param.getDefaultClassLoader()` 初始化微信 ClassLoader，并安装所有 Hook 模块。

### `WeChatMonitorHook.onPackageReady(PackageReadyParam)`

当 AppComponentFactory 准备完成后执行，刷新为 `param.getClassLoader()`。当前用于记录最终 ClassLoader，主要 Hook 仍在 `onPackageLoaded` 安装。

## 核心模块

### `core.MonitorContext`

集中保存 Hook 运行态：进程名、微信 ClassLoader、Android Context、配置、日志器、支付解析器、去重指纹和事件总线限流计数。

主要 API：

- `initialize(Context)`：在 `Application.attach(Context)` 命中后初始化配置、文件日志与支付解析器。
- `setClassLoader(ClassLoader)` / `getClassLoader()`：维护微信进程 ClassLoader。
- `isMonitorEnabled()`：读取监听开关。
- `isFileLogEnabled()`：读取文件日志开关。
- `isWcdbTraceEnabled()`：读取 WCDB SQLite 跟踪开关（默认关闭，仅排查发送是否落库时启用）。
- `shouldEmit(String)`：按 1500ms 窗口去重重复消息。
- `canTraceEventBus()`：限制事件总线最多记录 220 次。
- `logMessage(String, String)`：写入 Xposed/logcat 和文件日志。
- `readStringField(Object, String)`：读取混淆类 String 字段。
- `extractMethodValue(Object, String)`：调用混淆类 getter 并转为字符串。
- `trimValue(String)`：清理换行并限制字段长度。
- `classifyMessage(String)`：按内容特征分类红包、转账、支付或普通消息。

### `core.HookInstaller`

所有 Hook 子模块的统一安装接口。

主要 API：

- `install(XposedInterface, MonitorContext)`：安装当前模块 Hook。
- `intercept(XposedInterface, Method, XposedInterface.Hooker)`：统一设置 `setAccessible(true)`，使用 `ExceptionMode.PROTECTIVE` 注册 Hooker。

### `model.HookTargets`

维护微信混淆类名与目标类名常量，包括 `px0.q1`、`px0.r1`、`com.tencent.mm.storage.f8`、`com.tencent.mm.storage.h8`、`xv0.t9`、`xv0.wc`、`IEvent`、红包 UI 与转账 UI、主界面 `com.tencent.mm.ui.LauncherUI`、`com.tencent.wcdb.database.SQLiteDatabase` 及消息表名常量 `MESSAGE_TABLE = "message"`。

## Hook 模块

### `hooks.ApplicationContextHook`

Hook `android.app.Application.attach(Context)`，在微信 Application 绑定 Context 后初始化 `MonitorContext` 的配置、日志与支付解析组件。

### `hooks.SendMessageHook`

负责发送链路 Hook，对应 `frida/send/frida_friend_send_chain.js` 中 Java 层链路的全部观察点。

- `px0.q1.b()`：读取 `b` 字段作为 talker、`d` 字段作为 content，记录发送请求。
- `px0.q1.a()`：同上，覆盖另一条发送请求路径。
- `px0.q1.g(String)`：talker 字段写入，日志类型 `发送构造`，按 1500ms 指纹去重。
- `px0.q1.e(String)`：content 字段写入，日志类型 `发送构造`。
- `px0.q1.h(int)`：消息 type 字段写入，日志类型 `发送构造`。
- `px0.r1.a(String)`：原方法执行后记录发送 talker。
- `px0.r0` 全部构造方法 (`<init>`)：记录构造参数列表，日志类型 `发送Scene`。
- `px0.r0.getType()`：记录 scene type（预期 `0x20a / 522`），日志类型 `发送Scene`。
- `px0.r0.doScene(...)`：记录 scene 提交，日志类型 `发送网络`。
- `px0.r0.onGYNetEnd(...)`：记录发送回调，日志类型 `发送回调`。
- `fo1.g.j(q1)`：发送分发入口，记录 q1 状态。

### `hooks.ActiveSenderHook`

`sender.ActiveSender` 的预热器。

- 在 `WeChatMonitorHook.installHooks(...)` 中紧随 `SendMessageHook` 之后执行。
- 调用 `ActiveSender.warmUp(MonitorContext)`，加载并缓存 `px0.r1.a(String)`、`px0.q1.e(String)`、`px0.q1.h(int)`、`px0.q1.b()` 的 `Method` 引用。
- 成功后日志输出 `ActiveSender 就绪 (R1.a=... Q1.e=... Q1.h=... Q1.b=...)`。
- 失败时不阻断后续 Hook 安装，sendText 调用时会再次尝试懒加载。

### `hooks.WcdbMessageHook`

对应 `frida_friend_send_chain.js` 末段对 `com.tencent.wcdb.database.SQLiteDatabase` 的跟踪。

- 仅在 `MonitorContext.isWcdbTraceEnabled()` 为 `true` 时启用（默认关闭）。
- Hook `insert / insertWithOnConflict / replace / update` 的全部 overload。
- 只在第一个参数 `String table` 等于 `message` 时记录，避免性能爆炸。
- `update` 的 `ContentValues` 在参数索引 1，其他三种在索引 2。
- 日志类型 `WCDB写入`，包含 `ContentValues.toString()` 和返回值。

### `hooks.SendTriggerHook`

主动发送的命令文件触发器，Hook `Activity.onResume` 并按类名 `com.tencent.mm.ui.LauncherUI` 过滤。

- 每次微信主界面恢复（启动或从后台切回）触发一次。
- 回调内调用 `SendCommandFile.pollAndSend(MonitorContext)`，由 SendCommandFile 内部 5 秒节流去重。
- 不依赖 `LauncherUI` 自身是否 override `onResume`，通过过滤父类方法的 `this` 类名实现。

### `hooks.StorageMessageHook`

负责消息入库、分发与消息对象字段探针。

- `com.tencent.mm.storage.h8.U8(f8)`：插入成功后解析消息对象并记录。
- `com.tencent.mm.storage.h8.W8(f8, boolean)`：插入成功后解析消息对象并记录。
- `xv0.t9.n(f8, p0)`：记录消息入库链路。
- `xv0.wc.j(p0)`：记录消息分发链路。
- 当前会优先从消息对象的 `field_displayName`、`field_nickname`、`field_remark`、`field_conRemark`、`field_digestUser` 及同名 getter 候选中提取昵称；未命中时回退到 `MonitorContext` 中缓存的 `talker -> displayName` 映射。

支付消息逻辑：当消息类型为 `49` 且内容包含 `<sysmsg` 时，调用 `PayMessageMonitor.parsePaymentXml`，输出 PayMsgType、WalletType 和 paymsg 字段摘要。

### `hooks.ContactNicknameHook`

负责联系人昵称解析。

- `com.tencent.mm.storage.l3.n(String, boolean)`：查询联系人对象。
- 返回的 `com.tencent.mm.storage.a3` / `com.tencent.mm.contact.r` 上优先读取 `h2()` 作为展示名。
- 若 `h2()` 为空，再回退 `O0()`、`u0()` 与字段候选。
- 命中后把 `talker -> displayName` 写入 `MonitorContext`，供消息预览和聊天页回退使用。

### `hooks.EventBusHook`

Hook `com.tencent.mm.sdk.event.IEvent.e()`，对 `com.tencent.mm.autogen.events.*` 下包含 `chatmsg`、`sendmsg`、`message`、`notify` 的事件记录命中，并使用 `MonitorContext.canTraceEventBus()` 限流。

### `hooks.UiEventHook`

负责界面行为 Hook。

- `Activity.onResume()`：记录微信 Activity 页面恢复。
- `LuckyMoneyNewReceiveUI.onCreate(Bundle)`：记录打开红包领取页。
- `FMessageTransferUI.onCreate(Bundle)`：记录打开转账页面。

## 主动发送模块

对应 `frida/send/frida_active_send_rpc.js`。把 Frida 中通过 `rpc.exports.sendtext(...)` 的主动文本发送能力以 Java 反射 + 主线程异步执行的方式集成进 LSPosed 模块，不引入 Broadcast / Socket / HTTP 等外部 IPC。

### `sender.ActiveSender`

线程安全的静态工具类。

主要 API：

- `warmUp(MonitorContext)`：加载并缓存 `px0.r1.a(String)`、`px0.q1.e(String)`、`px0.q1.h(int)`、`px0.q1.b()` 的 `Method` 引用。幂等，重复调用直接返回 true。
- `isReady()`：判断反射缓存是否就绪。
- `sendText(MonitorContext, String talker, String content, int type)`：主动发送一条文本消息。

调用流程：

1. 参数校验：`talker`、`content` 非空；`type` 必须等于 `1`（与 Frida 脚本一致的安全护栏）。
2. 若反射缓存未就绪，则尝试 `warmUp`，失败时返回 `SendResult.fail`。
3. 通过 `MonitorContext.logMessage("主动发送", ...)` 记录入队。
4. `Handler(Looper.getMainLooper()).post(...)` 切到 WeChat 主线程异步执行：
   - `r1A.invoke(null, talker)`（R1.a 是 static 方法）得到 q1 实例
   - `q1E.invoke(q1, content)` 写入内容
   - `q1H.invoke(q1, type)` 写入类型
   - `q1B.invoke(q1)` 触发实际发送
5. 每一步都通过 `logMessage("主动发送", ...)` 输出阶段日志。
6. 反射调用会重新触发 `SendMessageHook` 的拦截器，按 1500ms 指纹去重后输出 chain 日志，主动发送的实际链路因此被双重记录。

### `sender.SendResult`

简单值对象。

- `isOk() / getMessage() / getError()`
- 静态工厂：`SendResult.ok(message) / SendResult.fail(message) / SendResult.fail(message, throwable)`

### `sender.SendCommandFile`

文件触发器,被 `CommandPoller` 和 `SendTriggerHook` 共同调用。

- 命令文件路径：`/sdcard/WeChatMonitor/send.json`（UTF-8,可由 SettingsActivity 或 adb push 写入）。
- 格式：`{"talker":"wxid_xxx", "content":"hello", "type":1}`。
- `pollAndSend(MonitorContext)`：200ms 防抖 → 读文件 → JSONObject 解析 → `ActiveSender.sendText` → 重命名为 `send.json.done.<ts>` 或 `.failed.<ts>`。
- 真正去重靠 rename 机制(文件处理完就消失),防抖只是避免高频触发器重复 IO。

### `sender.CommandPoller`

Hook 进程内的命令轮询线程。

- `start(MonitorContext)`：启动 HandlerThread "WeChatMonitor-CommandPoller",每 1000ms 调用一次 `SendCommandFile.pollAndSend`。
- `stop()`：停止线程并清理回调(目前没有调用方,模块生命周期内常驻)。
- 由 `hooks.ActiveSenderHook.install(...)` 在 ActiveSender 预热成功后启动。
- 与 `SendTriggerHook` 并存:Poller 提供"任意时刻立即发送"体验,onResume Hook 作为兜底。

### 与 Frida 脚本的对应关系

| Frida 行为 | LSPosed 等价 |
|---|---|
| `Java.use('px0.r1')` + 拦截 | `XposedInterface.hook(Method).intercept(...)` |
| `Java.use('px0.r1').a(talker)` | `r1A.invoke(null, talker)` |
| `send({type, line})` 推日志 | `MonitorContext.logMessage(type, content)` |
| `rpc.exports.sendtext(...)` | `ActiveSender.sendText(...)` |
| `rpc.exports.ping()` | `ActiveSender.isReady()` |

详细使用说明见 [主动发送集成说明.md](主动发送集成说明.md)。

## 数据与辅助模块

### `ConfigManager`

基于 `SharedPreferences` 管理监听开关、文件日志开关、日志保存目录与 WCDB 跟踪开关。默认优先使用 `/sdcard/WeChatMonitor/logs`，并兼容历史配置目录迁移。

- `isMonitorEnabled() / setMonitorEnabled(boolean)`：消息监听总开关，默认开。
- `isLogEnabled() / setLogEnabled(boolean)`：文件日志开关，默认开。
- `isWcdbTraceEnabled() / setWcdbTraceEnabled(boolean)`：WCDB SQLite message 表写入跟踪开关，默认关。SharedPreferences key 为 `wcdb_trace_enabled`。
- `getSaveLocation() / setSaveLocation(String)`：日志保存目录。

### `MessageLogger`

按日期创建 `wechat_monitor_yyyyMMdd.txt`，使用 UTF-8 无 BOM 追加写入日志。支持列出日志、清空日志、导出占位接口与恢复日志目录。

### `PayMessageMonitor`

通过微信 XML 工具 `com.tencent.mm.sdk.platformtools.aa.d(String, String, String)` 解析支付 XML，提取 paymsg 相关字段并提供支付类型描述。

### `SettingsActivity`

模块配置界面，分区卡片化布局，包含以下区块：

- **监听**：监听开关、消息日志开关。
- **主动发送**：talker 输入框（默认 `filehelper`）、content 多行输入框、`写入命令并提示切回微信` 按钮。点击后将参数序列化为 JSON 写入 `/sdcard/WeChatMonitor/send.json`（UTF-8 无 BOM），由微信进程内的 `hooks.SendTriggerHook` 在下次 LauncherUI.onResume 时读取并执行。
- **日志位置**：当前写入目录展示。
- **状态**：监听运行状态。
- **接收消息预览**：从数据库和日志文件汇总的最近 8 条消息。
- **操作按钮**：导出日志、清空日志。

UI 资源：`activity_settings.xml`、`styles.xml` 提供 `SectionHeader` 文本样式、`drawable/card_background.xml` 与 `drawable/input_background.xml` 提供圆角描边卡片背景。

## 构建与验证

```bash
./gradlew assembleRelease
```

验证点：

1. APK 内存在 `META-INF/xposed/java_init.list`、`META-INF/xposed/module.prop`、`META-INF/xposed/scope.list`。
2. 模块管理器识别 API 101 入口类 `com.wechat.monitor.WeChatMonitorHook`。
3. 微信进程 logcat 出现 `onModuleLoaded`、`onPackageLoaded`、`Hook installation complete`。
4. 发送/接收普通消息、红包/转账/支付消息后，`/sdcard/WeChatMonitor/logs` 生成 UTF-8 日志。
