# libMMProtocalJni.so 逆向分析

## 1. 文件信息

- 目标文件：`wx8068_mcp_decode/lib/arm64-v8a/libMMProtocalJni.so`
- 目标版本：微信 Android 8.0.68
- IDA 模块名：`libMMProtocalJni.so`
- Base：`0x0`
- Size：`0xecd90`
- MD5：`2ea51bf812807f05e27a667b45ad2e1f`
- SHA256：`55efcfca5ff3823fe7c400c52f69351409dd9b17a5ef66ba619361c2e9495a8d`
- 文件大小：`0xe1140`
- 源码路径日志：`/data/landun/workspace/libprotocaljni/component_repo/protocol/src/main/cpp/MMProtocalJniImpl.cpp`

结论：`libMMProtocalJni.so` 是微信协议打包 / 解包 / sync key / secure notify 解密的 JNI 协议库。它负责网络协议包层面的 `pack`、`unpack`、`NewSyncResponse`、`SyncKey` 合并校验、`SecureNotifyData` 解密和解压，但不直接解析支付 XML，也没有发现 `paymsg` / `sysmsg` 字符串。

也就是说：

- 它是支付通知进入微信后的更底层协议入口之一。
- 它能拿到解包后的 response bytes。
- 它不直接输出 Java 消息对象，也不直接解析 `<sysmsg type="paymsg">`。
- 如果目标是监听支付到账内容，仍应优先 Hook Java 消息对象 `ok.y7.j()` / `f8.j()`。
- 如果目标是研究网络协议层如何把 sync 包解出，则该 so 很关键。

---

## 2. JNI 入口

该 so 暴露的 JNI 入口集中在：

```text
com.tencent.mm.protocal.MMProtocalJni
```

关键 JNI：

| JNI 入口 | 地址 | 作用 |
|---|---:|---|
| `Java_com_tencent_mm_protocal_MMProtocalJni_pack` | `0x7c4ac` | 协议请求打包 |
| `Java_com_tencent_mm_protocal_MMProtocalJni_packHybrid` | - | Hybrid 加密打包 |
| `Java_com_tencent_mm_protocal_MMProtocalJni_packHybridEcdh` | - | ECDH Hybrid 加密打包 |
| `Java_com_tencent_mm_protocal_MMProtocalJni_packDoubleHybrid` | - | Double Hybrid 加密打包 |
| `Java_com_tencent_mm_protocal_MMProtocalJni_unpack` | `0x7c604` | 协议响应解包 |
| `Java_com_tencent_mm_protocal_MMProtocalJni_decodeSecureNotifyData` | `0x7c630` | secure notify 数据解密 / 解压 / 校验 |
| `Java_com_tencent_mm_protocal_MMProtocalJni_mergeSyncKey` | `0x7c468` | 合并 sync key |
| `Java_com_tencent_mm_protocal_MMProtocalJni_verifySyncKey` | `0x7c47c` | 校验 sync key |
| `Java_com_tencent_mm_protocal_MMProtocalJni_aesDecrypt` | - | AES 解密辅助 |
| `Java_com_tencent_mm_protocal_MMProtocalJni_aesEncrypt` | - | AES 加密辅助 |
| `Java_com_tencent_mm_protocal_MMProtocalJni_rsaPublicEncrypt` | - | RSA 公钥加密辅助 |
| `Java_com_tencent_mm_protocal_MMProtocalJni_generateECKey` | - | ECDH key 生成 |
| `Java_com_tencent_mm_protocal_MMProtocalJni_genSignature` | - | 协议签名生成 |
| `Java_com_tencent_mm_protocal_MMProtocalJni_compress` | - | 压缩辅助 |

---

## 3. 支付 / sysmsg 字符串搜索结果

当前在该 so 中搜索：

```text
paymsg
sysmsg
```

结果均未命中。

这说明该 so 不负责按字符串层面解析：

```xml
<sysmsg type="paymsg">
```

它处理的是协议层二进制包，XML 或消息内容一般是在解包完成后，由 Java 层 protobuf / storage / message converter 继续处理。

---

## 4. Sync / Message 相关字符串

该 so 中命中的关键协议结构：

| 字符串 | 说明 |
|---|---|
| `SyncRequest` | 普通 sync 请求 |
| `SyncResponse` | 普通 sync 响应 |
| `NewSyncRequest` | 新版 sync 请求 |
| `NewSyncResponse` | 新版 sync 响应，消息同步重点结构 |
| `SyncKey` | 同步 key |
| `CurrentSynckey` | 当前 sync key |
| `MaxSyncKey` / `MaxSynckey` | 最大 sync key |
| `AddMsg` | sync 响应里的新增消息字段 |
| `MsgType` | 消息类型字段 |
| `NotifySyncCount` | notify sync 数量 |
| `NotifyCount` | notify 数量 |
| `PushSyncCount` | push sync 数量 |
| `ChatRoomNotify` | 群通知 |
| `StatusNotifyRequest` | 状态通知请求 |
| `StatusNotifyResponse` | 状态通知响应 |
| `ModNotifyStatus` | 修改通知状态 |
| `ModChatRoomNotify` | 修改群通知 |

重点：该 so 能看到 `NewSyncResponse`、`AddMsg`、`MsgType` 等字段，说明协议元数据中确实包含消息同步结构定义。但这仍是协议结构层，不是最终 Java 消息对象层。

---

## 5. 核心函数地址表

| 地址 | 函数 | 作用 |
|---:|---|---|
| `0x7c604` | `Java_com_tencent_mm_protocal_MMProtocalJni_unpack` | JNI wrapper，转入 `sub_79838` |
| `0x79838` | `protocal_unpack` / `sub_79838` | 协议响应解包主逻辑 |
| `0x7c630` | `Java_com_tencent_mm_protocal_MMProtocalJni_decodeSecureNotifyData` | JNI wrapper，转入 `sub_79F14` |
| `0x79f14` | `protocal_decodeSecureNotifyData` / `sub_79F14` | secure notify 解密、解压、CRC 校验 |
| `0x7c468` | `Java_com_tencent_mm_protocal_MMProtocalJni_mergeSyncKey` | JNI wrapper，转入 `sub_771D8` |
| `0x771d8` | `merge_synckey_impl` / `sub_771D8` | 合并旧 / 新 sync key |
| `0x7c47c` | `Java_com_tencent_mm_protocal_MMProtocalJni_verifySyncKey` | JNI wrapper，转入 `sub_77598` |
| `0x77598` | `verify_synckey_impl` / `sub_77598` | 校验 sync key |
| `0x6062c` | `sub_6062C` | 大型协议结构 / protobuf 元信息初始化或注册函数，包含 `NewSyncResponse`、`AddMsg` 字符串引用 |

---

## 6. `protocal_unpack`：协议响应解包

JNI 入口：`Java_com_tencent_mm_protocal_MMProtocalJni_unpack @ 0x7c604`

实际实现：`sub_79838 @ 0x79838`

JNI wrapper 很薄：

```cpp
Java_com_tencent_mm_protocal_MMProtocalJni_unpack(...) {
    return sub_79838(...);
}
```

`sub_79838` 的日志函数名：

```text
protocal_unpack
```

源码签名字符串：

```text
jboolean protocal_unpack(
  JNIEnv *, jclass,
  jobject,
  jbyteArray,
  jbyteArray,
  jobject,
  jobject,
  jobject,
  jobject,
  jobject,
  jobject,
  jobject,
  jobject
)
```

### 6.1 输入 / 输出逻辑

反编译流程显示：

1. 读取输入包 byte array 长度：

```text
byteArrayLength = %d
```

2. 读取 session byte array。

3. 调用核心解包函数：

```text
sub_5E4EC(inputBuf, responseBuf, sessionBuf, cookieBuf, ...)
```

4. 输出多个字段到 Java object holder：

```text
noticeId
headExtFlags
compressAlgo
compressVer
sequence
respObj
cookie
ret
```

日志：

```text
noticeId=%d, headExtFlags=%d, compressAlgo=%d, compressVer=%d sequence=%d
cookie []=%s, Length=%d noticeId=%d
```

5. 解包失败日志：

```text
DecodePack failed
unpack failed, ret=%d
unpack return code:%d func=%d
```

### 6.2 作用判断

`protocal_unpack` 只负责协议包层面：

```text
网络响应密文 / 压缩包
  -> 解密 / 解压 / 校验
  -> response bytes
  -> cookie bytes
  -> noticeId / sequence / compress info
```

它不会直接解析支付 XML，也不会直接输出 `talker/content/type`。

如果要在 native 层抓“刚解包后的 NewSyncResponse 原始 bytes”，可以 Hook：

```text
libMMProtocalJni.so + 0x79838
```

但后续还需要 protobuf 解析 `NewSyncResponse -> AddMsg -> Content`，复杂度高于 Java 层 Hook。

---

## 7. `decodeSecureNotifyData`：SecureNotify 解密

JNI 入口：`Java_com_tencent_mm_protocal_MMProtocalJni_decodeSecureNotifyData @ 0x7c630`

实际实现：`sub_79F14 @ 0x79f14`

源码签名字符串：

```text
jbyteArray protocal_decodeSecureNotifyData(
  JNIEnv *, jclass,
  jbyteArray,
  jint,
  jint,
  jint,
  jint,
  jint,
  jint,
  jint,
  jbyteArray
)
```

### 7.1 核心流程

函数流程：

```text
输入 secure notify bytes
  -> 如果 encrypt type == 5
      -> 从 session / salt / key 派生 AES key
      -> 解密 secure notify
  -> 如果需要解压
      -> MicroMsg decompress
  -> crc32 校验
  -> 输出解密 / 解压后的 byte[]
```

关键日志：

```text
securenotify sessionLen:%d, saltLen:%d, keyLen:%d
securenotify decrypt failed
securenotify MicroMsg decompress failed
securenotify decompressLen:%d
securenotify checksum failed checksum[%d], jcheckSum[%d]
```

### 7.2 加密和解压行为

反编译中看到：

```text
sub_68E88(...)       // key 派生或 hash
sub_67578(...)       // 解密
sub_5FCE4(...)       // MicroMsg decompress
crc32(...)           // CRC 校验
```

这说明 secure notify 是一个独立的安全通知载荷，native 层负责解密、解压和校验，然后返回 Java byte array。

### 7.3 和支付通知关系

如果微信支付到账以 secure notify 的形式先到达，该函数可能能看到解密后的 notify bytes。

但当前该 so 内未出现 `paymsg` / `sysmsg`，说明 secure notify 解出后仍然由 Java 或其他模块继续解析为具体业务消息。

可作为高级 Hook 点：

```text
libMMProtocalJni.so + 0x79f14
```

重点抓返回值 byte[]，再尝试 protobuf / XML / UTF-8 扫描。

---

## 8. `mergeSyncKey`：合并同步 key

JNI 入口：`Java_com_tencent_mm_protocal_MMProtocalJni_mergeSyncKey @ 0x7c468`

实际实现：`sub_771D8 @ 0x771d8`

日志函数名：

```text
merge_synckey_impl
```

核心流程：

```text
oldSyncKey byte[]
newSyncKey byte[]
  -> 如果 old key 为空，直接返回 new key
  -> 否则调用 sync key merge
  -> 输出 mergedSyncKey byte[]
```

关键日志：

```text
oldKeyBuf is null, not need to merge, return newKeyBuf
leftKey buf = %s, len = %d
rightKey buf = %s, len = %d
merge key failed
charsToJByteArray failed
```

该函数用于维护微信同步游标，决定下一轮 sync 从哪里继续拉消息。

---

## 9. `verifySyncKey`：校验同步 key

JNI 入口：`Java_com_tencent_mm_protocal_MMProtocalJni_verifySyncKey @ 0x7c47c`

实际实现：`sub_77598 @ 0x77598`

日志函数名：

```text
verify_synckey_impl
```

核心流程：

```text
syncKey byte[]
  -> 解析 / 校验 sync key 格式
  -> 返回 boolean
```

关键日志：

```text
synckey len = 0
synckey verify result = %d
```

该函数只做同步 key 合法性校验，不解析消息内容。

---

## 10. 协议结构注册 / 元信息

`sub_6062C @ 0x6062c` 是一个非常大的函数，反编译体超过 170KB，内部引用大量协议结构字符串，包括：

```text
NewSyncResponse
AddMsg
MsgType
SyncKey
CurrentSynckey
MaxSyncKey
NotifyCount
PushSyncCount
```

该函数更像协议结构元信息、字段描述、pickle/unpickle 规则初始化或注册函数。

它说明 `libMMProtocalJni.so` 内部确实知道 `NewSyncResponse` 和 `AddMsg` 结构，但具体业务消息内容仍在解包后交给上层继续处理。

---

## 11. 和支付监听的关系

### 11.1 该 so 处于更底层

支付通知可能经过的完整链路大致是：

```text
网络层收到包
  -> libMMProtocalJni.so unpack / decodeSecureNotifyData
    -> NewSyncResponse / notify bytes
      -> Java protobuf / storage 层解析 AddMsg
        -> com.tencent.mm.storage.f8 / ok.y7 消息对象
          -> content = <sysmsg type="paymsg">...</sysmsg>
```

`libMMProtocalJni.so` 处在“网络协议包 -> response bytes”的位置，不处在“消息对象 -> content 字段”的位置。

### 11.2 为什么没看到 paymsg

原因是该 so 处理的是二进制协议框架：

```text
TLV / protobuf / encrypted pack / sync key / secure notify
```

支付 XML 是业务层内容，通常在 AddMsg 的 Content 字段里，只有反序列化到业务消息后才会以字符串形式出现。

因此在该 so 中搜索不到：

```text
paymsg
sysmsg
```

是合理的。

### 11.3 可用但不优先的 Hook 点

如果一定要从 native 协议层抓，可以 Hook：

| Hook 点 | 能拿到什么 | 缺点 |
|---|---|---|
| `libMMProtocalJni.so + 0x79838` | unpack 后 response bytes / cookie / noticeId | 仍需解析 NewSyncResponse / AddMsg |
| `libMMProtocalJni.so + 0x79f14` | secure notify 解密解压后 bytes | 仍需识别协议结构 |
| `Java MMProtocalJni.unpack` | Java 层 wrapper 输出 byte[] holder | 需要知道 holder 对象字段 |
| `Java MMProtocalJni.decodeSecureNotifyData` | 解密后的 notify byte[] | 返回值可能仍是 protobuf / 压缩业务结构 |

相比之下，Java 消息对象 getter 已经是最终明文：

```text
ok.y7.j()
f8.j()
```

---

## 12. 推荐 Frida 验证方向

### 12.1 Java 层 Hook MMProtocalJni

可以先 Hook Java 方法，而不是 native 偏移：

```javascript
Java.perform(function () {
  const Jni = Java.use('com.tencent.mm.protocal.MMProtocalJni');

  Jni.decodeSecureNotifyData.implementation = function () {
    const ret = this.decodeSecureNotifyData.apply(this, arguments);
    send({ type: 'secure_notify_ret', len: ret ? ret.length : 0 });
    return ret;
  };
});
```

如果返回 byte[] 中能搜到 `<sysmsg` 或 `paymsg`，说明支付通知可能从 secure notify 入口进入。

### 12.2 Native Hook unpack

```javascript
const base = Module.findBaseAddress('libMMProtocalJni.so');
Interceptor.attach(base.add(0x79838), {
  onEnter(args) {
    console.log('[MMProtocal] protocal_unpack enter');
  },
  onLeave(retval) {
    console.log('[MMProtocal] protocal_unpack ret=' + retval);
  }
});
```

### 12.3 Native Hook secure notify

```javascript
const base = Module.findBaseAddress('libMMProtocalJni.so');
Interceptor.attach(base.add(0x79f14), {
  onEnter(args) {
    console.log('[MMProtocal] decodeSecureNotifyData enter');
  },
  onLeave(retval) {
    console.log('[MMProtocal] decodeSecureNotifyData ret=' + retval);
  }
});
```

注意：native 返回的是 JNI `jbyteArray`，要读取内容建议优先 Hook Java 层 `MMProtocalJni.decodeSecureNotifyData`，更容易拿 byte[]。

---

## 13. 当前结论

`libMMProtocalJni.so` 是协议层关键库，主要能力包括：

- 请求打包。
- 响应解包。
- Hybrid / ECDH / DoubleHybrid 加密协议。
- AES / RSA / EC key 辅助。
- SyncKey 合并和校验。
- SecureNotifyData 解密、解压、CRC 校验。
- `NewSyncResponse`、`AddMsg`、`MsgType` 等协议结构元信息。

但它不是支付 XML 解析库。

支付监听优先级判断：

| 目标 | 优先 Hook |
|---|---|
| 支付到账 / 收款通知明文 | `ok.y7.j()` / `f8.j()` |
| 判断支付 XML 类型 | `type=49 + <sysmsg type="paymsg">` |
| 研究 sync response 原始协议 | `MMProtocalJni.unpack` / `libMMProtocalJni.so + 0x79838` |
| 研究 secure notify 解密数据 | `MMProtocalJni.decodeSecureNotifyData` / `libMMProtocalJni.so + 0x79f14` |
| 支付密码 / 签名 / 证书 | `libtenpay_utils.so` |
| 服务通知 / 品牌服务支付卡片 | `libaff_biz.so` |

最终建议：

```text
如果目标是实际监听支付到账，不建议继续在 libMMProtocalJni.so 深挖内容解析。
它适合作为协议层验证点，但最终字段仍应从 Java 消息对象拿。
```
