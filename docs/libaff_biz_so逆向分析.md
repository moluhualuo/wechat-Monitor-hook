# libaff_biz.so 逆向分析

## 1. 文件信息

- 目标文件：`wx8068_mcp_decode/lib/arm64-v8a/libaff_biz.so`
- 目标版本：微信 Android 8.0.68
- IDA 模块名：`libaff_biz.so`
- Base：`0x0`
- Size：`0x20c2e50`
- MD5：`b104596fa9e7ea969184180153af828e`
- SHA256：`1e70cf5fe98a27926a92e728d9fd9a75fa434070d91527e08f04e87d7ec0266a`
- 文件大小：`0x207bfd0`

结论：`libaff_biz.so` 不是财付通密码、证书、签名类安全库，而是微信 `AffBiz / BrandService / Biz Notify / XML Push` 相关 native 业务库。它更接近公众号、服务通知、品牌服务消息、推荐卡片、通知卡片的 native 解析与分发链路。

对于“支付到账 / 支付通知监听”，该 so 可能覆盖部分服务通知或品牌服务展示链路，但目前未发现直接的 `paymsg` XML 解析入口。最稳定的支付监听入口仍然是 Java 消息对象：

```text
ok.y7.j()
com.tencent.mm.storage.f8.j()
type == 49
content contains "<sysmsg"
```

---

## 2. so 功能定位

从字符串、函数名、日志 tag 和反编译结果看，`libaff_biz.so` 主要负责：

1. `AffBiz` 业务消息管理。
2. `BrandService` 品牌服务 / 服务通知消息管理。
3. XML Push 解析。
4. `BizRecommendCard` / `BrandServiceRecommendCard` 卡片解析。
5. `CardBuffer` Base64 解码。
6. protobuf 反序列化。
7. Notify / Notify_103 通知卡片生成和插入。
8. C++ native 层向 Java / Dart / ZIDL 层回调。
9. Biz message 数据库插入、更新和通知刷新。

可概括为：

```text
服务端 XML push
  -> libaff_biz.so XML parser
    -> BizRecommendCard / BrandServiceRecommendCard
      -> CardName / CardBuffer
        -> base64 decode
          -> protobuf ParsePartialFromString
            -> BizMsgInfo / Notify protobuf
              -> Insert / Notify manager
                -> ZIDL / Dart / Java bridge callback
```

---

## 3. 支付相关字符串

该 so 中存在一些支付相关字符串：

| 字符串 | 说明 |
|---|---|
| `weixin://wxpay/bizpayurl` | 微信支付业务 URL scheme |
| `weixin://wxpay/bindurl` | 支付绑定 URL scheme |
| `wxPayAuthorization` | 支付授权相关字段 / 能力名 |
| `jniwxPayAuthorization` | JNI 支付授权桥接名 |
| `JniwxPayAutho` | JNI 支付授权相关名称 |
| `https://payapp.weixin.qq.com/sjt/qr` | 微信支付二维码 URL |
| `https://payapp.weixin.qq.com/qr/` | 微信支付二维码 URL |
| `https://payapp.wechatpay.cn/qr/` | 微信支付二维码 URL |
| `https://payapp.wechatpay.cn/sjt/qr/` | 微信支付二维码 URL |
| `https://wx.tenpay.com/f2f` | 面对面支付 URL |
| `PaySubscribeInfo` | 支付订阅信息 protobuf 类型 |
| `isPaySubscribe` | 是否支付订阅字段 |
| `FinderLiveNoticeInfo_PayInfo` | 视频号直播通知支付信息 |
| `GetRecommendFeedsResponse_RecommendCardMsg_RecommendItemMsg_AppMsgPayInfo` | 推荐卡片 appmsg 支付信息 |

这些字符串说明 `libaff_biz.so` 会处理带支付跳转、支付订阅、支付卡片信息的业务消息，但这不等价于它负责解析微信支付通知 `<sysmsg type="paymsg">`。

当前 IDA 搜索结果：

```text
paymsg: 未命中
wxPayAuthorization: 命中
PaySubscribeInfo: 命中
```

因此，该 so 和“支付内容展示 / 支付跳转 / 支付订阅卡片”有关，但不是已经确认的支付消息 XML 主解析点。

---

## 4. 通知 / 插入 / BrandService 关键字符串

| 字符串 | 说明 |
|---|---|
| `GetAffBizNotifyMsgApi` | AffBiz 通知消息 API |
| `GetIamBizNotifyMsgManager` | Biz Notify manager 获取入口 |
| `JniinsertNotifyMsgAsync` | Java -> native 异步插入通知消息入口 |
| `insertNotifyMsgAsync` | 插入通知消息异步接口 |
| `JniCallOnInsertNoti` | native / bridge 插入通知回调 |
| `BrandServiceNotiManagerBridge::CallOnInsertNoti` | BrandService 通知插入回调 |
| `AffBizCppToNativeManager::CallOnNotifyMsgChangeAsync` | native 向上层通知消息变化 |
| `ZidlDart_BrandServiceNotiManagerBridge_CallOnInsertNoti` | Dart/ZIDL bridge 通知插入回调 |
| `insertNewMsg db fail msgViewType %_, msgType %_, localId %_` | 新消息入库失败日志 |

这些字符串明确说明 `libaff_biz.so` 维护一套 Biz/BrandService 通知消息插入和变更回调链路。

---

## 5. XML Push 关键字符串

| 字符串 | 说明 |
|---|---|
| `xml_push_handler.cpp` | Biz XML push 处理器 |
| `brand_service_xml_push_handler.cpp` | BrandService XML push 处理器 |
| `xml_push_notify_mgr.cpp` | XML push notify manager |
| `brand_service_xml_push_notify_mgr.cpp` | BrandService XML push notify manager |
| `BizRecommendCard` | Biz 推荐卡片 XML 根节点 |
| `BrandServiceRecommendCard` | BrandService 推荐卡片 XML 根节点 |
| `CardName` | 卡片类型字段 |
| `CardBuffer` | Base64 编码 protobuf 载荷字段 |
| `BizColumn` | BizColumn 卡片类型 |
| `Notify` | Notify 卡片类型 |
| `Notify_103` | BrandService Notify 卡片类型 |

核心结构：

```xml
<BizRecommendCard>
  <CardName>Notify</CardName>
  <CardBuffer>base64(protobuf)</CardBuffer>
</BizRecommendCard>
```

或：

```xml
<BrandServiceRecommendCard>
  <CardName>Notify_103</CardName>
  <CardBuffer>base64(protobuf)</CardBuffer>
</BrandServiceRecommendCard>
```

---

## 6. 关键函数地址表

| 地址 | 函数 / 符号 | 作用 |
|---|---|---|
| `0x141bb24` | `sub_141BB24` | `brand_service_xml_push_handler.cpp`，解析 `BrandServiceRecommendCard` / `Notify_103` |
| `0x12d633c` | `sub_12D633C` | `xml_push_handler.cpp`，解析 `BizRecommendCard` / `BizColumn` / `Notify` |
| `0x12f9a3c` | `AffBizCppToNativeManager::CallOnNotifyMsgChangeAsync` | AffBiz 通知消息变化 native -> 上层回调封装 |
| `0x143ad30` | `BrandServiceNotiManagerBridge::CallOnInsertNoti` | BrandService 通知插入后序列化 protobuf 并回调上层 |
| `0x12980f0` | `sub_12980F0` | Biz 新消息插入逻辑，包含 `insertNewMsg db fail...` 日志 |
| `0x3f2e00` | `JniAffBizNativeToCppManager::JniinsertNotifyMsgAsync` 字符串 | Java -> native 插入通知异步入口相关符号字符串 |

---

## 7. `sub_141BB24`：BrandService XML Push Notify 解析

地址：`0x141bb24`

源码路径日志：`brand_service_xml_push_handler.cpp`

日志函数名：`parseXmlAndCreateNotification`

该函数负责解析 BrandService XML Push，关键流程如下：

```text
输入 XML string
  -> XML parse
  -> root
  -> BrandServiceRecommendCard
  -> CardName
  -> 必须等于 Notify_103
  -> CardBuffer
  -> base64_decode(CardBuffer)
  -> protobuf ParsePartialFromString
  -> 检查 notify_type
  -> BrandServiceListSnapshotMrg::onNewUIRequestNotify
  -> BrandServiceXMLPushResortNotifyMgr::InsertRequestNotifyData
```

反编译中可确认的关键点：

1. XML 解析失败会输出：

```text
[BRS]xml parse failed. res=%_
[BRS]xml parse failed. root is null
[BRS]xml parse failed. biz_recommend_expt is null
```

2. 查找节点：

```text
BrandServiceRecommendCard
CardName
CardBuffer
```

3. `CardName` 必须为：

```text
Notify_103
```

否则输出：

```text
[BRS]card name mismatch. current %_ expected %_
```

4. `CardBuffer` 处理方式：

```text
CardBuffer string
  -> owl::v9::base64_decode
  -> google::protobuf::MessageLite::ParsePartialFromString
```

5. protobuf 解析后检查 `notify_type`：

```text
[BRS]pb card has no notify_type
```

6. 插入通知数据：

```text
BrandServiceXMLPushResortNotifyMgr
InsertRequestNotifyData
```

7. 成功日志：

```text
[BRS][Rec] onNewUIRequestNotify.
[BRS][%s] Insert notification completed, current count=%d
[BRS]handle newUI notify. type=%_  expire_timestamp=%_, xml=%_
```

该函数是 `libaff_biz.so` 中最接近“服务通知卡片解析”的核心入口之一。

---

## 8. `sub_12D633C`：Biz XML Push 解析

地址：`0x12d633c`

源码路径日志：`xml_push_handler.cpp`

该函数处理普通 Biz XML Push，和 `sub_141BB24` 类似，但节点是 `BizRecommendCard`。

关键流程：

```text
输入 XML string
  -> XML parse
  -> root
  -> BizRecommendCard
  -> CardName
      -> BizColumn
      -> Notify
  -> CardBuffer
  -> base64_decode(CardBuffer)
  -> protobuf ParsePartialFromString
  -> 根据 CardName 分发
      -> new card msg
      -> XMLPushResortNotifyMgr
  -> AffBizCppToNativeManager callback
```

该函数说明普通公众号 / Biz 推荐卡片和 Notify 卡片也走 native XML push + protobuf 卡片载荷链路。

重点判断：

| CardName | 推测作用 |
|---|---|
| `BizColumn` | Biz 列表 / 栏目卡片 |
| `Notify` | Biz 通知卡片 |

---

## 9. `AffBizCppToNativeManager::CallOnNotifyMsgChangeAsync`

地址：`0x12f9a3c`

该函数是 C++ 层通知 Java / Dart / Native bridge 的封装入口。

函数签名近似：

```cpp
AffBizCppToNativeManager::CallOnNotifyMsgChangeAsync(
    this,
    uint32_t notifyType,
    payload,
    outSharedPtr
)
```

反编译关键行为：

1. 获取 manager / shared object：

```text
sub_F60BC0(&v14)
```

2. 构造 closure / function object：

```text
off_1E6FBB0
```

3. 调用真正的异步通知：

```text
biz::zidl_export::AffBizCppToNativeManager::CallOnNotifyMsgChangeAsync(a1, a2, a3, &v16)
```

4. 处理 shared_ptr 引用计数和释放。

该函数本身不是 XML parser，而是 notify 变更事件从 native 分发到上层框架的桥。

---

## 10. `BrandServiceNotiManagerBridge::CallOnInsertNoti`

地址：`0x143ad30`

该函数负责 BrandService 通知插入后的上层回调。

反编译核心逻辑：

```cpp
google::protobuf::MessageLite::SerializeAsString(v9, bizMsgInfo);
dispatcher = zidl::DispatcherBase::Stub_(bridge);
dispatcher->vtable[128](dispatcher, serializedBizMsgInfo);
```

作用链路：

```text
BizMsgInfo protobuf
  -> SerializeAsString
  -> zidl::DispatcherBase::Stub_
  -> vtable + 128 callback
  -> Java / Dart / 上层业务
```

这说明 BrandService 通知不是直接传 Java 对象，而是先序列化 protobuf，再通过 ZIDL/Dart bridge 传递。

---

## 11. `sub_12980F0`：Biz 新消息插入

地址：`0x12980f0`

关键日志：

```text
insertNewMsg db fail msgViewType %_, msgType %_, localId %_
```

该函数体很大，属于 Biz 新消息插入 / 落库链路。结合日志和上下文，它不是 XML 解析入口，而是解析后的消息结构写入 Biz 数据库或消息管理器的核心路径之一。

可作为 Frida/IDA 后续验证点：

```text
libaff_biz.so + 0x12980f0
```

如果服务通知或公众号通知进入该 so，插入失败或插入成功附近都可能经过此函数。

---

## 12. JNI / ZIDL 桥接链路

已确认相关桥接符号：

```text
JniAffBizNativeToCppManager::JniinsertNotifyMsgAsync
JniinsertNotifyMsgAsync
JniCallOnInsertNoti
ZidlDart_BrandServiceNotiManagerBridge_CallOnInsertNoti
AffBizCppToNativeManager::CallOnNotifyMsgChangeAsync
BrandServiceNotiManagerBridge::CallOnInsertNoti
```

整体方向有两类：

### 12.1 Java -> Native

```text
Java / Kotlin 层
  -> JniAffBizNativeToCppManager.JniinsertNotifyMsgAsync
    -> libaff_biz.so
      -> native notify manager
        -> insert notify data
```

### 12.2 Native -> Java / Dart / ZIDL

```text
libaff_biz.so
  -> protobuf SerializeAsString
    -> zidl::DispatcherBase::Stub_
      -> ZidlDart_* callback
        -> Java / Flutter / UI 层刷新
```

---

## 13. 和支付监听的关系

### 13.1 已确认不是主支付安全库

支付安全能力已经在 `libtenpay_utils.so` 中确认：

```text
RSA / AES / SM4 / DES / SHA / HMAC / 证书 / 签名 / OTP / 支付密码加密
```

`libaff_biz.so` 不负责这些加密和签名能力。

### 13.2 未发现 `paymsg` 主解析入口

当前在 `libaff_biz.so` 中搜索：

```text
paymsg
```

未命中。

而微信支付通知 XML 的稳定格式是：

```xml
<sysmsg type="paymsg">
  <paymsg>
    <PayMsgType>...</PayMsgType>
    <WalletType>...</WalletType>
  </paymsg>
</sysmsg>
```

因此该 so 目前不像是 `<sysmsg type="paymsg">` 的主解析模块。

### 13.3 可能覆盖的支付相关场景

`libaff_biz.so` 可能覆盖以下支付相关展示场景：

| 场景 | 是否可能经过 libaff_biz.so |
|---|---|
| 服务通知里的支付卡片 | 可能 |
| 公众号 / 商家通知里的支付跳转卡片 | 可能 |
| 支付订阅提醒卡片 | 可能 |
| 视频号 / 推荐流里的付费卡片 | 可能 |
| 普通 C2C 收款到账 `<sysmsg type="paymsg">` | 未确认，不应优先依赖 |
| 支付密码加密 / 签名 / 证书 | 不属于该 so |

---

## 14. 推荐 Hook 策略

### 14.1 支付到账 / 支付通知优先 Hook Java 消息对象

当前最稳定方式：

```text
ok.y7.j()
com.tencent.mm.storage.f8.j()
```

判断条件：

```text
type == 49
content contains "<sysmsg"
content contains "paymsg"
```

解析：

```text
com.tencent.mm.sdk.platformtools.aa.d(content, "sysmsg", null)
```

字段：

```text
.sysmsg.paymsg.PayMsgType
.sysmsg.paymsg.WalletType
.sysmsg.paymsg.*
```

### 14.2 服务通知 / 品牌服务卡片可 Hook libaff_biz.so

如果目标是服务通知、公众号通知、品牌服务通知卡片，可考虑 Hook：

| Hook 点 | 用途 |
|---|---|
| `libaff_biz.so + 0x141bb24` | BrandService `Notify_103` XML push 解析 |
| `libaff_biz.so + 0x12d633c` | Biz `Notify` / `BizColumn` XML push 解析 |
| `libaff_biz.so + 0x143ad30` | BrandService 通知插入后回调 |
| `libaff_biz.so + 0x12f9a3c` | AffBiz 通知变化回调 |
| `libaff_biz.so + 0x12980f0` | Biz 新消息插入链路 |

Hook 时需要注意 ASLR：

```javascript
const base = Module.findBaseAddress('libaff_biz.so');
Interceptor.attach(base.add(0x141bb24), { ... });
```

### 14.3 Frida 验证方向

建议验证两条链路：

1. 支付到账消息：

```text
Java getter 是否输出 type=49 + <sysmsg type="paymsg">
```

2. 服务通知 / 公众号通知：

```text
libaff_biz.so + 0x141bb24 是否触发
libaff_biz.so + 0x12d633c 是否触发
libaff_biz.so + 0x143ad30 是否触发
```

如果支付到账只触发 Java getter 而不触发 `libaff_biz.so`，说明支付到账主链路不在该 so。

如果服务通知支付卡片触发 `Notify_103` 或 `Notify`，说明 `libaff_biz.so` 可作为服务通知支付卡片监听辅助点。

---

## 15. 当前结论

`libaff_biz.so` 是微信 8.0.68 中 AffBiz / BrandService / Biz Notify / XML Push 的 native 业务库。

它的核心链路是：

```text
XML push
  -> BizRecommendCard / BrandServiceRecommendCard
  -> CardName = BizColumn / Notify / Notify_103
  -> CardBuffer
  -> Base64 decode
  -> protobuf ParsePartialFromString
  -> Notify manager insert
  -> protobuf SerializeAsString
  -> ZIDL / Dart / Java callback
```

对于支付监听：

1. 它包含支付 URL、支付订阅、支付卡片信息相关字符串。
2. 它可能承载服务通知或品牌服务中的支付类卡片。
3. 它没有直接命中 `paymsg` 字符串。
4. 它不应替代 Java 层 `type=49 + <sysmsg type="paymsg">` 的支付通知监听。
5. 当前支付到账监听仍应优先使用 `ok.y7.j()` / `f8.j()` 的消息对象链路。

最终建议：

```text
支付到账 / 收款通知：优先 Java 层 type=49 + sysmsg/paymsg。
服务通知 / 公众号支付卡片：辅助 Hook libaff_biz.so 的 XML push Notify 链路。
支付密码 / 签名 / 证书：分析 libtenpay_utils.so。
```
