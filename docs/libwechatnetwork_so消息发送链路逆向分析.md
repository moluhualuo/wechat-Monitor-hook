# libwechatnetwork.so 消息发送链路逆向分析

作者：花落

许可：MIT

## 1. 目标

本文记录微信 Android 8.0.68 中 `libwechatnetwork.so` 在“给好友发送消息”链路里的作用边界。

重点结论：

- `talker`、`content`、`type` 等明文发送参数在 Java/smali 层组装。
- `px0.r0` 是 Java 层真实发送 scene，路径是 `/cgi-bin/micromsg-bin/newsendmsg`。
- `libwechatnetwork.so` 承担 mars STN 网络任务、长短链路、MMTLS、CGI 路由和收发回调，不是普通文本内容的首选组装点。

## 2. 分析对象

本次使用的静态分析对象：

- `wx8068_mcp_decode/lib/arm64-v8a/libwechatnetwork.so`
- `wx8068_mcp_decode/smali_classes9/px0/r0.smali`
- `wx8068_mcp_decode/smali_classes9/px0/q1.smali`
- `wx8068_mcp_decode/smali_classes9/fo1/g.smali`
- `wx8068_mcp_decode/smali_classes9/com/tencent/mm/ui/chatting/gc.smali`
- `wx8068_mcp_decode/smali_classes9/com/tencent/mm/ui/chatting/component/ki.smali`

IDA 会话已经重新打开过：

```text
libwechatnetwork.so -> session: libwechatnetwork
```

当前 IDA MCP 旧工具接口对该会话返回结构解析错误，因此本轮 native 证据以二进制字符串扫描和 smali 交叉验证为准。

## 3. Native 层证据

`libwechatnetwork.so` 中能直接看到 mars 网络栈信息：

```text
WCONAN_BUILD_NAME: mars
JNI_OnLoad
ExportStnManager
ExportMMStnManager
mars::stn::Task
mars::stn::StnManager
mars::stn::LongLinkTaskManager
ShortLinkWithMMTLS
LongLinkStart
MMTLS
```

这说明该 so 是微信 mars 网络层组件，负责网络任务调度与链路传输。

## 4. CGI 配置证据

`libwechatnetwork.so` 内置了 functionlist XML，其中 basic 分组包含：

```xml
<cgilist prefix="/cgi-bin/micromsg-bin/">
  <cgi reqid="237" respid="1000000237" nettype="3" netstrategy="0" netproto="2">newsendmsg</cgi>
  <cgi reqid="2" respid="1000000002" nettype="3" netstrategy="0" netproto="2">sendmsg</cgi>
  <cgi reqid="121" respid="1000000121" nettype="3" netstrategy="0" netproto="2">newsync</cgi>
  <cgi reqid="107" respid="1000000107" nettype="3" netstrategy="0" netproto="2">sendappmsg</cgi>
  <cgi reqid="68" respid="1000000068" nettype="3" netstrategy="0" netproto="2">sendemoji</cgi>
</cgilist>
```

结论：

- `newsendmsg` 在 native 网络配置中存在。
- `newsendmsg` 对应 mars functionlist 的 `reqid=237`。
- `sendmsg` 旧 CGI 也存在，对应 `reqid=2`。
- `nettype=3` 表示该任务可走 long/short 组合策略，具体通道由 mars STN 根据配置和网络状态选择。

## 5. Java Scene 与 Native CGI 的对应关系

`px0.r0.doScene()` 明确设置：

```smali
const-string v2, "/cgi-bin/micromsg-bin/newsendmsg"
iput-object v2, v1, Lcom/tencent/mm/modelbase/l;->c:Ljava/lang/String;

const/16 v2, 0x20a
iput v2, v1, Lcom/tencent/mm/modelbase/l;->d:I

const/16 v2, 0xed
iput v2, v1, Lcom/tencent/mm/modelbase/l;->e:I
```

含义：

| 字段 | 值 | 含义 |
|---|---:|---|
| CGI path | `/cgi-bin/micromsg-bin/newsendmsg` | 发送消息接口 |
| scene type | `0x20a` / `522` | Java modelbase scene 类型 |
| old cmd id | `0xed` / `237` | 与 native functionlist 的 `newsendmsg reqid=237` 对齐 |

关键点是：`0x20a` 是 Java scene type，`0xed` 才和 native functionlist 中的 `reqid=237` 对齐。

## 6. 完整发送边界

当前确认的边界如下：

```text
Chatting UI
  -> px0.r1.a(talker)
  -> px0.q1
     -> g(talker)
     -> e(content)
     -> h(type)
     -> b() / a()
  -> fo1.g.j(q1)
  -> px0.r0 (MicroMsg.NetSceneSendMsg)
     -> doScene()
     -> path=/cgi-bin/micromsg-bin/newsendmsg
     -> type=0x20a
     -> oldCmdId=0xed
  -> modelbase/network 层提交 Task
  -> libwechatnetwork.so / mars STN
     -> cgi=newsendmsg
     -> reqid=237
     -> longlink/shortlink/MMTLS
  -> WeChat server
```

因此，如果目标是“主动给好友发文本消息”，优先操作 Java 层：

```text
px0.r1.a(talker)
  -> q1.e(content)
  -> q1.h(type)
  -> q1.b()
```

`libwechatnetwork.so` 更适合用于验证网络任务是否发出、任务通道、MMTLS 和 CGI 分发，而不是直接构造文本发送内容。

## 7. 关键 Hook 点

### 7.1 发送参数 Hook

优先 Hook Java/smali 层：

| 目标 | Hook 点 | 能看到什么 |
|---|---|---|
| UI 发起 | `com.tencent.mm.ui.chatting.gc` | `talker`、`content`、`type` |
| 复杂发送入口 | `com.tencent.mm.ui.chatting.component.ki` | `talker`、`content`、`type`、`q1.f`、`q1.h`、`q1.n`、`q1.i` |
| 请求对象 | `px0.q1.e(String)` | 文本内容 |
| 请求对象 | `px0.q1.g(String)` | 好友 wxid / talker |
| 请求对象 | `px0.q1.h(int)` | 消息类型 |
| Scene 创建 | `fo1.g.j(px0.q1)` | `q1 -> px0.r0` 映射 |
| 发送完成 | `px0.r0.onGYNetEnd(...)` | 成功/失败、服务端返回 |

### 7.2 Native 网络 Hook

native 层更适合 Hook 这些方向：

| 目标 | 关键词 |
|---|---|
| 任务提交 | `mars::stn::Task`、`Submit`、`StartTask` |
| 请求组包 | `req2buf`、`Req2Buf` |
| 响应处理 | `OnTaskEnd`、`task end callback` |
| 长链发送 | `LongLinkTaskManager`、`OnSend` |
| 短链请求 | `ShortLinkWithMMTLS`、`__RunRequestWithHttp` |
| MMTLS | `MMTLS`、`mmtls2`、`HandShake` |

本轮没有把 native 函数地址固化进文档，原因是 IDA MCP 旧接口当前对会话返回结构解析错误。后续如果需要 native 地址级 Hook，应在 IDA GUI 或修复后的 MCP 中以 `newsendmsg` 字符串、`reqid=237`、`oldCmdId=0xed` 和 `task end callback` 交叉引用继续定位。

## 8. 与消息监控、支付监控的关系

现有 Frida 脚本按用途分层：

| 脚本 | 方向 | 说明 |
|---|---|---|
| `frida/frida_friend_send_chain.js` | 主动发送链路 | Hook `px0.q1`、`fo1.g.j`、`px0.r0`，验证好友文本发送请求构造、scene 提交和回调 |
| `frida/frida_plaintext_probe.js` | 消息明文 | Hook WCDB `ContentValues`，抓 `message.content` |
| `frida/frida_message_monitor_test.js` | 消息写库 | Hook `SQLiteDatabase` insert/update/replace 等，观察消息落库 |
| `frida/frida_parse_lvbuf.js` | 消息 metadata | 解析 `lvbuffer` 二进制字段 |
| `frida/frida_kinda_pay_monitor.js` | 支付 native | Hook `libkinda_android.so` 离线支付、收款、收银台命令 |

这些脚本主要覆盖“接收/落库/支付通知”。主动发送好友消息的链路应新增或调整为 Hook：

```text
px0.q1.g(String)
px0.q1.e(String)
px0.q1.h(int)
fo1.g.j(px0.q1)
px0.r0.doScene(...)
px0.r0.onGYNetEnd(...)
```

## 9. 后续逆向路线

如果继续深入 native：

1. 用 IDA 从 `newsendmsg` 字符串交叉引用定位 functionlist 解析逻辑。
2. 从 `oldCmdId=0xed` 和 `reqid=237` 找 Task 初始化点。
3. 跟 `Req2Buf` 回调，确认 Java 请求体如何序列化为 native `AutoBuffer`。
4. 跟 `LongLinkTaskManager` / `ShortLinkWithMMTLS`，确认实际走长链还是短链。
5. 在 `OnTaskEnd` 回调处验证 `px0.r0.onGYNetEnd(...)` 的回调时机。

当前阶段不建议优先在 native 层伪造消息发送，因为文本发送所需字段已经在 Java 层明确，native 层只会增加协议、加密和任务状态成本。

## 10. 当前结论

`libwechatnetwork.so` 已确认承载 `newsendmsg` 的 mars 网络发送能力：

- `newsendmsg reqid=237`
- `sendmsg reqid=2`
- `long.weixin.qq.com` / `short.weixin.qq.com` / `wechat.com` 域名组
- `LongLinkTaskManager`
- `ShortLinkWithMMTLS`
- `MMTLS`
- `Task` 调度与回调日志

但“给好友发送什么内容、发给谁、消息类型是什么”这些关键参数已经在 Java 层落地。发送链路的实操入口仍是：

```text
px0.q1 -> fo1.g.j -> px0.r0 -> /cgi-bin/micromsg-bin/newsendmsg -> libwechatnetwork.so
```
