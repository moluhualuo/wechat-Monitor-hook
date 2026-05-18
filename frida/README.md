# Frida Scripts

WeChat 8.0.68 动态插桩脚本，按用途分为四大类。

---

## 项目概览

**WeChatMonitor/Frida** — 微信 8.0.68 动态插桩工具集，用于消息监听、发送链追踪、支付监控和逆向诊断。

---

## 一、probe/ 探针/诊断 — 10 个脚本

| 脚本 | 核心功能 | Hook 目标 |
|------|----------|-----------|
| `frida_hook_safe.js` | **安全 Hook 验证**（不触发 getter 方法，直接读字段） | `h8.U8/W8/Z8`、`xv0.t9.n/e/C`、`xv0.wc.j`、`px0.r1.a`、`px0.q1.b` |
| `frida_hook_verify.js` | **Hook 验证逻辑** | 验证消息插入/分发链路 |
| `frida_hook_all_h8.js` | **Hook 所有 h8 相关函数** | `com.tencent.mm.storage.h8` 全部方法 |
| `frida_probe_deep.js` | **深度探测** | 消息对象内部字段和调用链 |
| `frida_probe_nickname_sources.js` | **昵称数据来源探测** | 昵称字段在消息流中的出现位置 |
| `frida_extract_test.js` | **提取测试** | 消息字段提取验证 |
| `frida_verify_main.js` | **主验证流程** | 完整的消息接收验证 |
| `frida_verify_push.js` | **Push 验证** | Push 通道消息验证 |
| `frida_minimal_test.js` | **最小化测试** | 最精简的 Hook 验证 |
| `frida_deep_trace.js` | **深度 Trace** | 深度调用链追踪 |

---

## 二、receive/ 消息接收 — 3 个脚本

| 脚本 | 核心功能 | 技术方案 |
|------|----------|----------|
| `frida_plaintext_probe.js` | **明文消息捕获** | Hook `SQLiteDatabase.insert/insertWithOnConflict`，从 `ContentValues` 提取 `talker/content/createTime`，输出 Base64 + Unicode 转义 |
| `frida_parse_lvbuf.js` | **LVBuffer 二进制解析** | 解析 `message.lvbuffer` 字段的 LV 格式结构（含 `msgSource`、`fromUsername`、`toUsername`、`bizChatId` 等 20+ 字段） |
| `frida_message_monitor_test.js` | **消息监听测试** | WCDB SQL 层 + `ChatMsgNotifyEvent` 拦截，抓消息写入所有管道 |

---

## 三、send/ 消息发送 — 2 个脚本

> **集成状态**：以下两个脚本的功能已在 LSPosed 模块中以 Java Hook + 反射调用方式集成，详见 [`docs/api.md`](../docs/api.md) 与 [`docs/主动发送集成说明.md`](../docs/主动发送集成说明.md)。这两个脚本保留作为 frida 诊断备用，可用于绕过模块直接验证微信版本差异。

| 脚本 | 核心功能 | 技术方案 | Java 实现 |
|------|----------|----------|----------|
| `frida_friend_send_chain.js` | **好友消息发送链路监控** | Hook `px0.r1.a`（构造发送请求）→ `px0.q1`（消息对象：`g/e/h/a/b`）→ `fo1.g.j`（分发）→ `px0.r0`（NetSceneSendMsg：`doScene/onGYNetEnd`）→ `SQLiteDatabase.insert`（落库） | `hooks.SendMessageHook` + `hooks.WcdbMessageHook`（默认关，需开启 `wcdb_trace_enabled`） |
| `frida_active_send_rpc.js` | **主动发送 RPC 接口** | 暴露 `rpc.exports.sendtext(talker, content, type)`，通过 `px0.r1.a(talker)` → `q1.e(content)` → `q1.h(type)` → `q1.b()` 构造并发送文本消息 | `sender.ActiveSender.sendText(ctx, talker, content, type)` |

---

## 四、payment/ 支付监控 — 1 个脚本

| 脚本 | 核心功能 | Hook 目标 |
|------|----------|-----------|
| `frida_kinda_pay_monitor.js` | **libkinda_android.so 支付消息监听** | Native: `notifyHKOfflineNewXml`(0x77DF34)、`OfflineUseCase dispatch`(0xAB0834)、`paymsg handler`(0xBE9628)、`handleShowCashierCmd`(0xA70B24)、`notifyPayerMsgListUpdate`(0x8519F4/0x833828)、`handleShowCashierWithCashierMsgInfo`(0xA77EA0)；Java: `KCollectPayerMsgCallback.call`、`TenpayCgiCallback.onSuccess/onError` |

---

## 五、关键类/函数速查表

| 类名 | 角色 | 关键方法 |
|------|------|----------|
| `com.tencent.mm.storage.h8` | 消息存储管理 | `U8(f8)` → long、`W8(f8, boolean)` → long、`Z8(f8)` → long |
| `com.tencent.mm.storage.f8` | 消息对象 | 被 h8、t9 等操作的数据载体 |
| `xv0.t9` | 消息分发器 | `n(f8, p0)`、`e(f8, boolean)`、`C(f8)` |
| `xv0.wc` | 消息分发入口 | `j(p0)` → `q0` |
| `px0.r1` | 发送请求工厂 | `a(String talker)` → `q1` |
| `px0.q1` | 发送消息对象 | `g(talker)`、`e(content)`、`h(type)`、`a()`、`b()` |
| `px0.r0` | NetSceneSendMsg | `$init`、`doScene`、`getType`、`onGYNetEnd` |
| `fo1.g` | 发送分发器 | `j(q1)` |
| `com.tencent.wcdb.database.SQLiteDatabase` | WCDB 数据库 | `insert`、`insertWithOnConflict`、`replace`、`update` |
| `com.tencent.wcdb.core.Database` | WCDB 核心 | `executeSQL`、`execSQL`、`rawQuery` |

---

## 六、用法

所有脚本都通过 Frida CLI 注入微信进程：

```bash
# 热附加指定主进程 PID（推荐，适合已登录的微信）
adb shell pidof com.tencent.mm
frida -U -p <PID> -l frida/<script>.js

# 冷启动注入（Frida 17.x 不需要 --no-pause）
frida -U -f com.tencent.mm -l frida/<script>.js

# 热附加主进程
frida -U -n com.tencent.mm -l frida/<script>.js

# 附加 push 进程（部分消息只走 push）
frida -U -n com.tencent.mm:push -l frida/<script>.js
```

---

## 七、使用流程

```
1. frida_list_modules.js        → 确认 .so 模块已加载
2. frida_list_exports.js        → 确认导出函数地址
3. frida_trace_jni.js           → 扫描 WCDB JNI 方法签名
4. frida_hook_safe.js           → 安全验证 Hook 点是否存活
5. frida_plaintext_probe.js     → 捕获明文消息
6. frida_parse_lvbuf.js         → 解析 lvbuffer 附加数据
7. frida_friend_send_chain.js   → 追踪发送链路
8. frida_active_send_rpc.js     → 主动发送消息（RPC）
9. frida_kinda_pay_monitor.js   → 监控支付消息
```

---

## 八、核心数据流

```
接收: 网络 → xv0.wc.j() → xv0.t9.n/e/C() → h8.U8/W8/Z8() → SQLiteDatabase.insert('message') → ChatMsgNotifyEvent
发送: px0.r1.a(talker) → q1.e(content).h(type).b() → fo1.g.j(q1) → px0.r0.doScene() → SQLiteDatabase.insert('message')
支付: libkinda.so → notifyHKOfflineNewXml → OfflineUseCase dispatch → paymsg handler → KCollectPayerMsgCallback.call
```

---

## 九、详细文档

- [Frida 消息监听测试](../docs/frida消息监听测试.md)
- [微信消息明文获取指南](../docs/微信消息明文获取指南.md)