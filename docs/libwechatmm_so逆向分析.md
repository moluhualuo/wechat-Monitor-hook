# libwechatmm.so 逆向分析

## 1. 文件信息

- 目标文件：`wx8068_mcp_decode/lib/arm64-v8a/libwechatmm.so`
- 目标版本：微信 Android 8.0.68
- IDA 模块名：`libwechatmm.so`
- Base：`0x0`
- Size：`0x4d0600`
- MD5：`88571927fd85a0caa19c87884dd32aeb`
- SHA256：`4f3a44f881bacb81a78dbfb0eea75e27e5dc65dee4751323e9444b7a9658c7f6`
- CRC32：`0xaa5d4727`
- 文件大小：`0x4b8940`

结论：`libwechatmm.so` 是 `mars-wechat / mars-private` 网络、CDN、mmcrypto、JNI 工具集合库，不是支付通知 XML 解析库。

也就是说：

- 它包含微信通用加密工具 JNI（ECDH / ECDSA / HKDF / AES-GCM / Hybrid ECDH / AX ECDH）。
- 它包含头像资源解密能力。
- 它包含 Mars CDN 上传下载、C2C 文件传输、SNS 图片视频下载。
- 它包含 PCDN / NAT / STUN 协议 handler。
- 它不包含 `paymsg`、`sysmsg`、`appmsg`、`NewSyncResponse`、`PayMsgType`、`WalletType` 等支付通知相关字符串。
- 支付到账监听不应依赖该 so。

---

## 2. JNI 类映射

该 so 暴露的 JNI 入口主要对应以下 Java 类：

| Java 类 | native 作用 |
|---|---|
| `com.tencent.mm.jni.utils.UtilsJni` | 通用加密、解密、ECDH、ECDSA、HKDF、AES-GCM、头像解密、Hybrid/AX ECDH 引擎 |

源码路径日志：

```
/root/.wconan2/mmnet/1db359be_1768189315/mars-wechat/mars/mm-ext/jni/com_tencent_mm_jni_utils_UtilsJni.cc
/root/.wconan2/mmnet/1db359be_1768189315/mars-private/mars/cdn/src/protocol/c2c_request.cc
/root/.wconan2/mmnet/1db359be_1768189315/mars-private/mars/cdn/src/protocol/c2c_response.h
/root/.wconan2/mmnet/1db359be_1768189315/mars-private/mars/cdn/src/task/c2c_download_task.cc
/root/.wconan2/mmnet/1db359be_1768189315/mars-private/mars/cdn/src/task/snsimage_download.cc
/data/landun/workspace/pcdn_sdk_v3/src/stun/StunProtocolHandler.cpp
/data/landun/workspace/pcdn_sdk_v3/src/nat/NatProtocolHandler.cpp
```

---

## 3. 支付 / sysmsg 字符串搜索结果

当前在该 so 中搜索：

```
paymsg
sysmsg
appmsg
NewSyncResponse
PayMsgType
WalletType
wxpay
tenpay
wallet
xml
```

结果均未命中。

搜索 `pay` 只命中 payload 相关，如 `biz_req_payload`、`biz_rsp_payload`、`bizRspPayLoad`、`bizReqPayLoad`、`PayloadBuffer`，这些是 CDN / PCDN 协议 payload 字段，不是微信支付相关。

搜索 `AddMsg` 只命中 `addMsgHandler`，实际来自 PCDN / NAT / STUN handler 注册，不是微信协议 `NewSyncResponse.AddMsg`。

---

## 4. 核心 JNI 入口

| JNI 入口 | 地址 | 作用 |
|---|---:|---|
| `Java_com_tencent_mm_jni_utils_UtilsJni_cryptGenRandom` | `0xdaed8` | 生成随机字节 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_doEcdsaVerify` | `0xdaf64` | ECDSA 验签 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_doEcdsaSHAVerify` | `0xdb12c` | ECDSA + SHA 验签 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_GenEcdhKeyPair` | `0xdb2f4` | 生成 ECDH key pair |
| `Java_com_tencent_mm_jni_utils_UtilsJni_GenEcdsaKeyPair` | `0xdb584` | 生成 ECDSA key pair |
| `Java_com_tencent_mm_jni_utils_UtilsJni_Ecdh` | `0xdb814` | ECDH shared secret |
| `Java_com_tencent_mm_jni_utils_UtilsJni_EcdsaSign` | `0xdbb8c` | ECDSA 签名 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_EcdsaVerify` | `0xdbf04` | ECDSA 验签 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_HKDF` | `0xdca8c` | HKDF 密钥派生 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_AesGcmEncrypt` | `0xdcec0` | AES-GCM 加密 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_AesGcmDecrypt` | `0xdd238` | AES-GCM 解密 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_AesGcmEncryptWithCompress` | `0xdd5b0` | 压缩后 AES-GCM 加密 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_AesGcmDecryptWithUncompress` | `0xdd928` | AES-GCM 解密后解压 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_CreateHybridEcdhCryptoEngine` | `0xddca0` | Hybrid ECDH 引擎创建 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_HybridEcdhEncrypt` | `0xde2b8` | Hybrid ECDH 加密 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_HybridEcdhDecrypt` | `0xde608` | Hybrid ECDH 解密 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_DecryptAvatar` | `0xdec50` | 头像数据解密 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_CreateAxEcdhCryptoEngine` | `0xdf2f8` | AX ECDH 引擎创建 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_AxEcdhEncrypt` | `0xdf70c` | AX ECDH 加密 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_AxEcdhDecrypt` | `0xdfa5c` | AX ECDH 解密 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_AesGcmEncryptWithNonce` | `0xe00a4` | 带 nonce AES-GCM 加密 |
| `Java_com_tencent_mm_jni_utils_UtilsJni_AesGcmDecryptWithNonce` | `0xe072c` | 带 nonce AES-GCM 解密 |

---

## 5. `JNI_OnLoad`：初始化

地址：`0xdad10`

返回值：`65542`

核心流程：

```
ScopeJEnv 初始化
  -> VarCache::SetJvm
  -> LoadClass
  -> LoadStaticMethod / LoadMethod
  -> 遍历注册 callback
  -> jnicat::jcache 初始化
```

---

## 6. `cryptGenRandom`：随机字节生成

地址：`0xdaed8`

核心流程：

```
operator new[] 分配 buffer
  -> sub_E109C 生成随机
  -> NewByteArray + SetByteArrayRegion
  -> 返回 Java byte[]
```

---

## 7. `AesGcmEncrypt`：AES-GCM 加密

地址：`0xdcec0`

核心流程：

```
JNU_JbyteArray2Buffer 读 key / plaintext
  -> 转 std::string
  -> sub_E4CE0 执行 AES-GCM encrypt
  -> 成功返回密文 byte[]
```

失败日志：`mmcrypto::AesGcmEncrypt rv %_`

---

## 8. `DecryptAvatar`：头像解密

地址：`0xdec50`

核心流程：

```
JNU_JbyteArray2Buffer 读头像加密数据
  -> sub_E7B10 解密
  -> 如果 ret == -4，进行第二阶段重试
  -> 成功返回解密后 byte[]
```

关键日志：

```
decrypt ret %_ input.len %_ output.len %_ msg %_
decrypt stage 2 ret %_ output.len %_ msg %_
input buffer invalid.
```

---

## 9. PCDN / NAT / STUN Handler

| 地址 | 函数 | 源码路径 | 作用 |
|---:|---|---|---|
| `0x397cbc` | `sub_397CBC` | `pcdn_sdk_v3/src/stun/StunProtocolHandler.cpp` | 注册 STUN cmd handler |
| `0x3b6a08` | `sub_3B6A08` | `pcdn_sdk_v3/src/nat/NatProtocolHandler.cpp` | 注册 NAT cmd handler |
| `0x3bf684` | `sub_3BF684` | PCDN handler | PCDN cmd handler |
| `0x3c4f28` | `sub_3C4F28` | PCDN handler | PCDN cmd handler |

---

## 10. CDN 相关能力

| 能力 | 源码路径 |
|---|---|
| C2C 文件 / 图片 / 视频传输 | `c2c_request.cc` / `c2c_download_task.cc` |
| SNS 图片 / 视频下载 | `snsimage_download.cc` |
| PCDN / NAT / STUN | `pcdn_sdk_v3` |
| Mars 网络消息队列 | `mars::comm::MessageQueue` |

---

## 11. 核心函数地址表

| 地址 | 函数 | 作用 |
|---:|---|---|
| `0xdad10` | `JNI_OnLoad` | JNI 初始化、VarCache、类/方法缓存 |
| `0xdaed8` | `cryptGenRandom` | 随机字节生成 |
| `0xdaf64` | `doEcdsaVerify` | ECDSA 验签 |
| `0xdb12c` | `doEcdsaSHAVerify` | ECDSA + SHA 验签 |
| `0xdb2f4` | `GenEcdhKeyPair` | ECDH 密钥对生成 |
| `0xdb584` | `GenEcdsaKeyPair` | ECDSA 密钥对生成 |
| `0xdb814` | `Ecdh` | ECDH 共享密钥 |
| `0xdbb8c` | `EcdsaSign` | ECDSA 签名 |
| `0xdbf04` | `EcdsaVerify` | ECDSA 验签 |
| `0xdca8c` | `HKDF` | HKDF 密钥派生 |
| `0xdcec0` | `AesGcmEncrypt` | AES-GCM 加密 |
| `0xdd238` | `AesGcmDecrypt` | AES-GCM 解密 |
| `0xdd5b0` | `AesGcmEncryptWithCompress` | 压缩 + AES-GCM 加密 |
| `0xdd928` | `AesGcmDecryptWithUncompress` | AES-GCM 解密 + 解压 |
| `0xddca0` | `CreateHybridEcdhCryptoEngine` | Hybrid ECDH 引擎创建 |
| `0xde2b8` | `HybridEcdhEncrypt` | Hybrid ECDH 加密 |
| `0xde608` | `HybridEcdhDecrypt` | Hybrid ECDH 解密 |
| `0xdec50` | `DecryptAvatar` | 头像解密 |
| `0xdf2f8` | `CreateAxEcdhCryptoEngine` | AX ECDH 引擎创建 |
| `0xdf70c` | `AxEcdhEncrypt` | AX ECDH 加密 |
| `0xdfa5c` | `AxEcdhDecrypt` | AX ECDH 解密 |
| `0xe00a4` | `AesGcmEncryptWithNonce` | 带 nonce AES-GCM 加密 |
| `0xe072c` | `AesGcmDecryptWithNonce` | 带 nonce AES-GCM 解密 |
| `0x397cbc` | `sub_397CBC` | STUN handler 注册 |
| `0x3b6a08` | `sub_3B6A08` | NAT handler 注册 |
| `0x3bf684` | `sub_3BF684` | PCDN handler 注册 |
| `0x3c4f28` | `sub_3C4F28` | PCDN handler 注册 |

---

## 12. 和支付监听的关系

### 12.1 已确认不是支付通知 XML 解析库

在该 so 中搜索以下关键字符串均未命中：`paymsg`、`sysmsg`、`appmsg`、`NewSyncResponse`、`PayMsgType`、`WalletType`、`wxpay`、`tenpay`、`wallet`。

### 12.2 定位

| 能力领域 | 是否属于该 so |
|---|---|
| ECDH / ECDSA / HKDF / AES-GCM 加密 | 是 |
| Hybrid ECDH / AX ECDH 引擎 | 是 |
| 头像资源解密 | 是 |
| Mars CDN 上传下载 | 是 |
| C2C 文件传输 | 是 |
| SNS 图片 / 视频下载 | 是 |
| PCDN / NAT / STUN | 是 |
| 支付通知 XML 解析 | 不是 |
| 聊天消息明文获取 | 不需要 |

### 12.3 和已分析 so 的对比

| so 文件 | 定位 | 是否支付监听主线 |
|---|---|---|
| `libtenpay_utils.so` | 支付安全能力库（RSA/AES/SM4/证书/签名/OTP/密码加密） | 不是，但可用于支付密码分析 |
| `libaff_biz.so` | AffBiz / BrandService / XML Push 通知库 | 可能覆盖服务通知支付卡片 |
| `libMMProtocalJni.so` | 协议层 JNI（pack/unpack/sync/secure notify） | 不直接解析 paymsg XML |
| `libwechatmm.so` | mars 网络 / mmcrypto / CDN / PCDN 工具库 | 不是 |
| `libwechatnetwork.so` | （待分析）网络分发层 | 可能接近 |

---

## 13. 推荐 Hook 策略

### 13.1 支付到账仍优先 Java 消息对象

```
ok.y7.j() / com.tencent.mm.storage.f8.j()
type == 49
content contains "<sysmsg"
com.tencent.mm.sdk.platformtools.aa.d(content, "sysmsg", null)
.sysmsg.paymsg.PayMsgType
.sysmsg.paymsg.WalletType
```

### 13.2 Frida 验证示例

```javascript
const base = Module.findBaseAddress('libwechatmm.so');

Interceptor.attach(base.add(0xdcec0), {
  onEnter(args) {
    console.log('[mmcrypto] AesGcmEncrypt enter');
  },
  onLeave(retval) {
    console.log('[mmcrypto] AesGcmEncrypt ret=' + retval);
  }
});

Interceptor.attach(base.add(0xdec50), {
  onEnter(args) {
    console.log('[mmcrypto] DecryptAvatar enter');
  },
  onLeave(retval) {
    console.log('[mmcrypto] DecryptAvatar ret=' + retval);
  }
});
```

---

## 14. 当前结论

`libwechatmm.so` 是微信 8.0.68 中 mars 网络框架和 mmcrypto 加密工具的 native 库，核心能力包括：

- ECDH / ECDSA 密钥对生成、签名、验签
- HKDF 密钥派生
- AES-GCM / AES-GCM + 压缩 / AES-GCM + nonce 加解密
- Hybrid ECDH / AX ECDH 加密引擎
- 头像资源解密
- Mars CDN C2C 文件传输
- SNS 图片 / 视频下载
- PCDN / NAT / STUN 协议 handler

它对"消息传输加密"和"CDN 资源传输"很关键，但对"支付消息监听"不是 Hook 点。

最终建议：

```
支付到账 / 收款通知：优先 Java 层 type=49 + sysmsg/paymsg
支付安全算法：分析 libtenpay_utils.so
服务通知 / 公众号支付卡片：分析 libaff_biz.so
协议原始包：分析 libMMProtocalJni.so
网络分发层：下一步可看 libwechatnetwork.so
libwechatmm.so 不建议作为支付监听主线继续深挖
```
