# Frida 消息监听测试脚本

## 文件

- `frida/frida_message_monitor_test.js`
- `frida/probe/frida_probe_nickname_sources.js`

## 用法

启动微信并注入：

```bash
frida -U -f com.tencent.mm -l frida/frida_message_monitor_test.js --no-pause
```

如果微信已经启动：

```bash
frida -U -n com.tencent.mm -l frida/frida_message_monitor_test.js
```

如果要监听 push 进程：

```bash
frida -U -n com.tencent.mm:push -l frida/frida_message_monitor_test.js
```

## 昵称定位探针

用于定位 `talker -> 昵称/备注` 的真实来源：

```bash
frida -U -n com.tencent.mm -l frida/probe/frida_probe_nickname_sources.js
```

该脚本现在会：

1. 直接 Hook 已确认的联系人查询入口 `com.tencent.mm.storage.l3.n(String, boolean)`。
2. 输出请求 `talker`、`strict` 参数，以及返回联系人对象 `com.tencent.mm.storage.a3` / `com.tencent.mm.contact.r` 上的候选字段。
3. 重点输出 `e1()`、`u0()`、`h2()`、`O0()` 等 getter，验证最终展示名优先是否来自 `h2()`、回退是否来自 `O0()`。
4. 保留 `com.tencent.mm.storage.f8` 的构造和 `j()` / `getContent()` 探针，用于继续比对消息对象自身是否也携带显示名。
5. 帮助把运行时确认到的真实字段/方法回填到 libxposed 模块。

预期输出示例：

```text
[CONTACT-STORAGE] hooking com.tencent.mm.storage.l3.n(String, boolean) -> com.tencent.mm.storage.a3
[CONTACT-HIT] n talker=wxid_xxx strict=true class=com.tencent.mm.storage.a3 fields=[field_username=..., field_conRemark=张三, field_nickname=张三] methods=[e1()=wxid_xxx, h2()=张三, O0()=张三]
[CONTACT-NAME] h2 username=wxid_xxx value=张三
[MSG-CONTENT] j talker=wxid_xxx content=你好 fields=[field_talker=...] methods=[...]
```

如果 `CONTACT-HIT` 能稳定出现，而 `h2()` 始终非空，就可以把 Xposed 侧联系人显示名优先级切到 `h2()`；如果 `h2()` 为空但 `O0()` 命中，再把 `O0()` 作为回退。

## 当前 Hook 点

### libWCDB.so

- `sqlite3_exec`
- `sqlite3_prepare_v2`
- `sqlite3_prepare_v3`
- `sqlite3_step`
- `sqlite3_finalize`
- `sqlite3_bind_text`
- `sqlite3_bind_int`
- `sqlite3_bind_int64`

用于确认消息是否写入 `message` / `msg` / `notify_msg` / `biz_msg` 相关表。

### libaff_biz.so

基于 IDA base `0x0` 的偏移：

- `0x12980f0`：`sub_12980F0`，包含 `insertNewMsg db fail msgViewType...` 日志字符串
- `0x3f2e00`：`JniAffBizNativeToCppManager.JniinsertNotifyMsgAsync`
- `0x415d8e`：`JniBrandServiceManagerBridge.JniInsertAsync`
- `0x4170e9`：`JniBrandServiceNotiManagerBridge.JniInsertAsync`
- `0x673f6`：`AffBizCppToNativeManager.CallOnNotifyMsgChangeAsync`
- `0x68049`：`AffBizCppToNativeManager.CallOnNotifyMsgChangeAsync` 带 callback 版本

### Java 层辅助确认

- `com.tencent.mm.autogen.events.ChatMsgNotifyEvent`
- `com.tencent.wcdb.core.Database`
- `com.tencent.wcdb.database.SQLiteDatabase`
- `com.tencent.wcdb.database.SQLiteConnection`
- `com.tencent.mm.storage.f8`
- `com.tencent.mm.storage.h8`
- `ok.y7.j()` / `com.tencent.mm.storage.f8.j()`：已确认可拿到解密后的普通文本消息正文
- `ok.y7.getContent()` / `com.tencent.mm.storage.f8.getContent()`：辅助确认消息内容

## 预期输出

如果 WCDB SQL 命中，会看到：

```text
[SQL-PREPARE] insert into ...message...
[BIND-TEXT] stmt=... [n] ...
[SQL-STEP] ret=... write=true sql=...
```

如果 aff_biz native 通知命中，会看到：

```text
[AFF-NOTIFY-CHANGE] ...
[AFF-INSERT-NEW-MSG] ...
[JNI-BRAND-NOTI-INSERT] ...
```

## lvbuffer 解析

`message.lvbuffer` 使用 `com.tencent.mm.sdk.platformtools.e2` 的 LVBuffer 格式，不是 protobuf。格式为：

- 首字节固定 `0x7b` (`{`)
- 中间字段按顺序写入，字符串和 byte[] 使用 2 字节 big-endian 长度前缀
- int 使用 4 字节 big-endian
- 末字节固定 `0x7d` (`}`)

`ok.y7.smali` 的 `convertFrom(Cursor)` 读取顺序为：

1. `E` string
2. `F` int
3. `G` string
4. `H` int
5. `I` int
6. `J` int
7. `K` int
8. `L` int
9. `M` int
10. `N` string
11. `P` string
12. `Q` string，实测这里保存 `<msgsource>...</msgsource>` XML
13. `R` int
14. `S` string
15. `T` byte[]
16. `U` string
17. `V` string
18. `W` int
19. `X` int
20. `Y` int
21. `Z` int
22. `p0` int
23. `x0` string

实测普通文本消息中 `Q/msgSource` 包含来源 XML，例如：

```xml
<msgsource><alnode><fr>1</fr></alnode><signature>...</signature><tmp_node><publisher-id></publisher-id></tmp_node></msgsource>
```

`frida/frida_parse_lvbuf.js` 已改为按该 LVBuffer 顺序解析，并输出 `LVBUF-PARSE`。

## 明文消息

`lvbuffer` 只保存 `<msgsource>` 这类来源 metadata，不保存普通文本正文。Frida 实测普通文本消息正文在 Java 消息对象 getter 中已经是解密后的明文：

- `ok.y7.j()`
- `com.tencent.mm.storage.f8.j()`

终端直接打印中文可能出现乱码，所以测试脚本和 Xposed 日志都输出 UTF-8 Base64 字段辅助确认：

```text
[MSG-GETTER] ok.y7.j => text=... b64=5rWL6K+V5piO5paH
```

其中 `5rWL6K+V5piO5paH` 解码为 `测试明文`。

Xposed 模块已在主流程中接入 `hookPlaintextMessageGetters`，日志类型为 `明文消息`，字段包含 `talker`、`type`、`msgId`、`time`、`content`、`contentB64`。

## 注意

- `libaff_biz.so` 主要偏向品牌/公众号/通知/VOIP 消息，不一定覆盖普通私聊文本消息。
- 普通私聊消息更可能从 `libWCDB.so` 的 SQL 层先被确认。
- 如果 SQL 层完全没有输出，说明目标进程不对，优先尝试 `com.tencent.mm` 主进程和 `com.tencent.mm:push` 进程分别注入。
