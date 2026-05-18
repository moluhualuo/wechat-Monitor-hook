# libkinda_android.so 逆向分析

## 1. 文件信息

- 目标文件：`wx8068_mcp_decode/lib/arm64-v8a/libkinda_android.so`
- 目标版本：微信 Android 8.0.68
- IDA 模块名：`libkinda_android.so`
- 文件大小：约 16MB
- 函数数量：80,679（自动分析后）

结论：`libkinda_android.so` 是微信 **Kinda 框架** — 跨平台 C++ 支付业务逻辑层。它是目前唯一命中 `PayMsgInfo`、`paymsg_type`、`PayMsgType` 的 native 库，是 native 层支付消息监听的核心目标。

---

## 2. 功能定位

Kinda 框架主要负责：

1. 支付 UI 业务逻辑（UseCase 模式）
2. 收银台（Cashier）流程控制
3. 离线支付消息接收和分发
4. 收款消息监听（KCollectPayerMsg）
5. 财付通 CGI 请求隧道（TenpayCgiCallback）
6. 红包接收（BizHongBaoReceiveUseCase）
7. 转账流程（BizF2FTransferMoneyUseCase）

---

## 3. 关键字符串

| 地址 | 字符串 | 说明 |
|------|--------|------|
| `0xC4D7B` | `PayMsgInfo` | 支付消息信息结构体 |
| `0xC6EEB` | `paymsg_type` | 支付消息类型字段 |
| `0xFA49A` | `PayMsgType` | 支付消息类型码 |
| `0xF99E4` | `OfflinePayMsgInfo` | 离线支付消息信息 |
| `0x107017` | `WPHKOfflinePayMsgInfo` | 香港钱包离线支付消息信息 |
| `0xC9FEF` | `registerPayerMsgRecvListenerImpl` | 注册收款消息监听 |
| `0xBDE45` | `notifyPayerMsgListUpdate too early: {}` | 消息列表更新日志 |
| `0xC024F` | `notifyPayerMsgListUpdate, refresh_type:{}, {}` | 消息列表更新日志 |
| `0xFB7E2` | `ackOfflineMsg request ack msg: ack_key:{}, req_key:{}, paymsg_type:{}, transactionid:{}` | 离线消息确认日志 |
| `0xF83A3` | `notifyHKOfflineNewXml paymsg` | 香港钱包离线支付 XML 通知 |
| `0xC8751` | `hk_msg_type` | 香港钱包消息类型字段 |
| `0xC875D` | `HKOfflineUseCase` | 香港钱包离线 UseCase |

---

## 4. 核心函数：离线支付消息分发

### 4.1 `sub_AB0834`：OfflineUseCase 消息分发主函数

地址：`0xAB0834`

该函数是 `kinda::OfflineUseCase` 的离线支付消息分发主函数，包含一个 switch-case 结构，根据消息类型（`v331`）分发处理：

| case | 日志字符串 | 消息类型 | 处理逻辑 |
|------|-----------|---------|---------|
| 1 | `onRecvOfflinePayNotifyMsg` | 支付通知消息 | 检查 pay_cmd，可能更新 token，显示成功页面 |
| 2 | `onRecvOfflinePaySuccMsg` | 支付成功消息 | 显示成功页面，上报 `/cgi-bin/mmpay-bin/offlinev2datareport` |
| 3 | `onRecvOfflineCashierMsg` | 收银台消息 | 显示收银台 UI，处理 `prefer_bind_serial`/`operation_type`/`cashier_data` 等字段 |
| 4 | `onRecvOfflinePayingMsg` | 支付中消息 | 显示 loading 动画，设置超时（默认 20 秒） |
| 5 | `onRecvInstalPayNotifyMsg` | 分期支付通知 | 处理分期支付通知消息 |

处理完成后，发送 ACK 到 `/cgi-bin/mmpay-bin/offlinev2ackmsg`。

### 4.2 Case 1：`onRecvOfflinePayNotifyMsg`

```
收到支付通知消息
  → 检查 offlinepay 是否已出现（v3 + 1305）
  → 检查 pay_cmd 是否需要更新 token
  → 如果 pay_cmd == 1：调用 sub_A9EE14 更新 token
  → 检查消息数据中是否有成功页面标志
  → 如果有：调用 sub_AB2378 显示成功页面
```

关键日志：
- `onRecvOfflinePayNotifyMsg recv offline pay notify msg`
- `onRecvOfflinePayNotifyMsg start handle notify msg`
- `onRecvOfflinePayNotifyMsg pay_cmd is 1, should update token`
- `onRecvOfflinePayNotifyMsg not offlinepay appear, can not show succ page`

### 4.3 Case 2：`onRecvOfflinePaySuccMsg`

```
收到支付成功消息
  → 检查消息数据中是否有成功页面数据（BYTE4(v329) & 8）
  → 检查 reqKey 是否已处理
  → 调用 sub_A9D9E0 验证消息
  → 调用 payPreSuccess 回调
  → 显示成功页面（offlineshowpage）
  → 上报 /cgi-bin/mmpay-bin/offlinev2datareport
  → 调用 sub_BE8F0C 处理支付结果
  → 显示订单详情页面
```

关键日志：
- `onRecvOfflinePaySuccMsg`
- `onRecvOfflinePaySuccMsg start show success page`
- `onRecvOfflinePaySuccMsg msg has no pay succ page data`
- `onRecvOfflinePaySuccMsg not offlinepay appear, can not show succ page`
- `offline pay show order detail page`

### 4.4 Case 3：`onRecvOfflineCashierMsg`

```
收到收银台消息
  → 检查 req key 是否存在
  → 检查 offlinepay 是否已出现
  → 检查 instalpaycode 是否已创建
  → 检查 reqKey 是否已处理
  → 调用 sub_A9D9E0 验证消息
  → 显示收银台 UI
  → 设置收银台参数：
    - prefer_bind_serial（首选绑定序列号）
    - prefer_bank_type（首选银行类型）
    - selected_bind_serial（选定绑定序列号）
    - selected_bank_type（选定银行类型）
    - operation_type（操作类型）
    - cashier_data（收银台数据）
  → 判断是否使用 LiteApp 收银台
  → 调用 sub_BE8F0C 处理支付
```

关键日志：
- `onRecvOfflineCashierMsg`
- `onRecvOfflineCashierMsg msg has no req key`
- `onRecvOfflineCashierMsg not offlinepay appear, can not show cashier`
- `onRecvOfflineCashierMsg offlinepay && instalpaycode not create, ignore message`
- `onRecvOfflineCashierMsg start show offlinepay cashier`
- `use liteapp offline pay cashier`
- `offline pay show cashier`

### 4.5 Case 4：`onRecvOfflinePayingMsg`

```
收到支付中消息
  → 检查 req key 是否存在
  → 检查 offlinepay 是否已出现
  → 获取超时时间（默认 20 秒）
  → 检查 reqKey 是否已处理
  → 创建 loading UI
  → 调用 sub_A9D9E0 验证消息
  → 显示 loading 动画
```

关键日志：
- `onRecvOfflinePayingMsg`
- `onRecvOfflinePayingMsg msg has no req key`
- `onRecvOfflinePayingMsg not offlinepay appear, can not show loading view for userpaying message`
- `onRecvOfflinePayingMsg show loading`

### 4.6 Case 5：`onRecvInstalPayNotifyMsg`

```
收到分期支付通知消息
  → 检查 offlinepay 是否已出现
  → 调用 sub_A9DF30 验证消息
  → 调用 sub_AB2378 显示成功页面
```

关键日志：
- `onRecvInstalPayNotifyMsg`
- `onRecvInstalPayNotifyMsg start handle notify msg`
- `onRecvInstalPayNotifyMsg not offlinepay appear, can not show succ page`

---

## 5. 核心函数：香港钱包离线通知

### 5.1 `sub_77DF34`：notifyHKOfflineNewXml

地址：`0x77DF34`

该函数处理香港钱包的离线 XML 通知消息。

核心流程：

```
收到 notifyHKOfflineNewXml
  → 提取 func_name
  → 检查 func_name 是否匹配
  → 提取 hk_msg_type
  → 如果 hk_msg_type == 1：
      → 日志："notifyHKOfflineNewXml paymsg"
      → 调用 sub_BE9628 处理支付消息
  → 如果 hk_msg_type == 2：
      → 日志："notifyHKOfflineNewXml update data msg"
      → 更新离线数据
      → 刷新离线支付 token
```

### 5.2 `sub_BE9628`：paymsg 处理函数

地址：`0xBE9628`

该函数处理从 `notifyHKOfflineNewXml` 传入的支付消息。

核心流程：

```
遍历消息列表
  → 调用 sub_BE45B4 处理每条消息
  → 设置 __LiteAppKeyBusinessName
  → 设置 notifyToKindaLite 事件
  → 传递 data 数据
  → 调用 sub_D3D774 处理
  → 发布 publishPayLiteAppGlobalEvent
```

---

## 6. 核心函数：收款消息监听

### 6.1 `sub_C5FFF4`：KQRCodeCollectionService 初始化

地址：`0xC5FFF4`

该函数初始化 `com/tencent/kinda/gen/KQRCodeCollectionService` 的 JNI 方法表，包含：

| JNI 方法 | 签名 | 说明 |
|---------|------|------|
| `initTTS` | `()V` | 初始化 TTS |
| `isF2fRingToneOpen` | `()Z` | 检查面对面收款铃声 |
| `isF2fRingToneOpenMch` | `()Z` | 检查商户铃声 |
| `openVoiceRingTone` | `(I)V` | 打开语音铃声 |
| `closeVoiceRingTone` | `(I)V` | 关闭语音铃声 |
| `getUserTrueName` | `()Ljava/lang/String;` | 获取用户真实姓名 |
| `getDisplayName` | `(ZLjava/lang/String;)Ljava/lang/String;` | 获取显示名称 |
| `registerPayerMsgRecvListenerImpl` | `(Lcom/tencent/kinda/gen/VoidKCollectPayerMsgCallback;)V` | 注册收款消息回调 |
| `unregisterPayerMsgRecvListener` | `()V` | 注销收款消息回调 |
| `saveQRCodeToAlbumImpl` | `(Ljava/lang/String;Lcom/tencent/kinda/gen/KView;Lcom/tencent/kinda/gen/VoidI32Callback;)V` | 保存二维码到相册 |

关键点：`registerPayerMsgRecvListenerImpl` 接收一个 `VoidKCollectPayerMsgCallback` 回调对象，当有新的收款消息时会调用其 `call` 方法。

### 6.2 `sub_8519F4`：notifyPayerMsgListUpdate

地址：`0x8519F4`

该函数负责通知收款消息列表更新。

核心流程：

```
检查初始化状态（v1 + 2064）
  → 如果已初始化：
    → 获取消息列表
    → 日志："notifyPayerMsgListUpdate: {}"
    → 触发回调更新
  → 如果未初始化：
    → 日志："notifyPayerMsgListUpdate too early: {}"
```

### 6.3 `sub_833828`：notifyPayerMsgListUpdate with refresh_type

地址：`0x833828`

带刷新类型的消息列表更新函数。

核心流程：

```
获取当前状态
  → 获取刷新类型（v0）
  → 处理消息列表
  → 日志："notifyPayerMsgListUpdate, refresh_type:{}, {}"
  → 如果有回调：触发回调
```

---

## 7. JNI 入口地址表

| JNI 方法 | 地址 | 作用 |
|---------|------|------|
| `VoidKCollectPayerMsgCallback.CppProxy.nativeDestroy` | `0xC8EE04` | 收款消息回调销毁 |
| `VoidKCollectPayerMsgCallback.CppProxy.native_call` | `0xC8EE58` | 收款消息回调调用 |
| `TenpayCgiCallback.CppProxy.nativeDestroy` | `0xC8D978` | 财付通 CGI 回调销毁 |
| `TenpayCgiCallback.CppProxy.native_onError` | `0xC8D9CC` | 财付通 CGI 错误回调 |
| `TenpayCgiCallback.CppProxy.native_onSuccess` | `0xC8DA4C` | 财付通 CGI 成功回调 |
| `IAppKinda.CppProxy.native_getIsPaying` | `0xC79C88` | 获取是否正在支付 |
| `IAppKinda.CppProxy.native_notifyAllUseCases` | `0xC79CD4` | 通知所有 UseCase |
| `IAppKinda.CppProxy.native_updateOfflinePayTokenWithScene` | `0xC79D98` | 更新离线支付 token |
| `IAppKinda.CppProxy.native_checkIfNeedUpdateOfflinePayToken` | `0xC79DE4` | 检查是否需要更新 token |
| `IAppKinda.CppProxy.native_updateOfflinePayDefaultCard` | `0xC79E2C` | 更新离线支付默认卡 |
| `IAppKinda.CppProxy.native_notifyHKOfflineNewXml` | `0xC79F44` | 香港钱包离线 XML 通知 |
| `UseCase.CppProxy.native_notify` | `0xC8DEF4` | UseCase 通知 |
| `KCgi.CppProxy.native_getNeedNotify` | `0xC82F1C` | 获取是否需要通知 |

---

## 8. 支付 CGI 端点

从 `libkinda_android.so` 中发现的支付相关 CGI 端点：

| CGI 路径 | 说明 |
|---------|------|
| `/cgi-bin/mmpay-bin/offlinev2ackmsg` | 离线支付消息确认 |
| `/cgi-bin/mmpay-bin/offlinev2datareport` | 离线支付数据上报 |
| `/cgi-bin/mmpay-bin/honeypayerlistcross` | 亲情卡付款人列表 |
| `/cgi-bin/mmpay-bin/checkhoneypayercross` | 检查亲情卡付款人 |
| `/cgi-bin/mmpay-bin/beforetransfer` | 转账前检查 |
| `/cgi-bin/mmpay-bin/transferplaceorder` | 转账下单 |
| `/cgi-bin/mmpay-bin/transferquery` | 转账查询 |
| `/cgi-bin/mmpay-bin/businesshongbao` | 商业红包 |
| `/cgi-bin/mmpay-bin/f2fplaceorder` | 面对面付款下单 |
| `/cgi-bin/mmpay-bin/f2fpaycheck` | 面对面付款检查 |
| `/cgi-bin/mmpay-bin/f2fannounce` | 面对面付款通知 |
| `/cgi-bin/mmpay-bin/tenpay/querywechatwallet` | 查询微信钱包 |
| `/cgi-bin/mmpay-bin/tenpay/querywxpaysetting` | 查询微信支付设置 |
| `/cgi-bin/mmpay-bin/tenpay/resetpwdbytoken` | 重置支付密码 |

---

## 9. 和支付监听的关系

### 9.1 已确认是 native 层支付消息核心库

`libkinda_android.so` 包含：
- `PayMsgInfo` — 支付消息信息结构体
- `paymsg_type` — 支付消息类型字段
- `PayMsgType` — 支付消息类型码
- 离线支付消息分发（5 种消息类型）
- 收款消息监听回调机制
- 香港钱包离线支付通知

### 9.2 和已分析 so 的对比

| so 文件 | 定位 | 是否支付监听主线 |
|---|---|---|
| **`libkinda_android.so`** | **Kinda 支付业务框架** | **是 — native 层支付消息分发核心** |
| `libaff_biz.so` | AffBiz / BrandService / XML Push 通知库 | 辅助 — 服务通知支付卡片 |
| `libMMProtocalJni.so` | 协议层 JNI（pack/unpack/sync/secure notify） | 不直接解析 paymsg XML |
| `libtenpay_utils.so` | 支付安全能力库（RSA/AES/SM4/证书/签名/OTP） | 不适用（但可分析密码加密） |
| `libwechatmm.so` | mars 网络 / mmcrypto / CDN 工具库 | 不适用 |

### 9.3 推荐 Hook 策略

#### 方案一：Java 层（最简单，推荐首选）

```text
ok.y7.j() / com.tencent.mm.storage.f8.j()
type == 49
content contains "<sysmsg"
content contains "paymsg"
com.tencent.mm.sdk.platformtools.aa.d(content, "sysmsg", null)
.sysmsg.paymsg.PayMsgType
.sysmsg.paymsg.WalletType
```

#### 方案二：Native 层 — 离线支付消息（libkinda_android.so）

```javascript
// Hook 离线支付消息分发主函数
const base = Module.findBaseAddress('libkinda_android.so');

// 方案 A：Hook notifyHKOfflineNewXml
Interceptor.attach(base.add(0x77DF34), {
  onEnter(args) {
    console.log('[Kinda] notifyHKOfflineNewXml enter');
  },
  onLeave(retval) {
    console.log('[Kinda] notifyHKOfflineNewXml ret=' + retval);
  }
});

// 方案 B：Hook OfflineUseCase 消息分发
Interceptor.attach(base.add(0xAB0834), {
  onEnter(args) {
    console.log('[Kinda] OfflineUseCase msg dispatch enter');
  },
  onLeave(retval) {
    console.log('[Kinda] OfflineUseCase msg dispatch ret=' + retval);
  }
});
```

#### 方案三：Native 层 — 收款消息回调

```javascript
Java.perform(function() {
  // Hook KCollectPayerMsgCallback.call
  var Cls = Java.use('com.tencent.kinda.gen.VoidKCollectPayerMsgCallback$CppProxy');
  Cls.call.implementation = function(msg) {
    console.log('[Kinda] KCollectPayerMsgCallback.call msg=' + msg);
    return this.call(msg);
  };
});
```

---

## 10. 完整的 Native 支付消息链路

```
网络层收到支付推送
  → libkinda_android.so
    → notifyHKOfflineNewXml (sub_77DF34)
      → 检查 hk_msg_type
        → hk_msg_type == 1 (paymsg)
          → sub_BE9628 (publishPayLiteAppGlobalEvent)
        → hk_msg_type == 2 (update data)
          → 更新离线数据
    → OfflineUseCase 消息分发 (sub_AB0834)
      → case 1: onRecvOfflinePayNotifyMsg (支付通知)
      → case 2: onRecvOfflinePaySuccMsg (支付成功)
      → case 3: onRecvOfflineCashierMsg (收银台)
      → case 4: onRecvOfflinePayingMsg (支付中)
      → case 5: onRecvInstalPayNotifyMsg (分期通知)
    → ACK → /cgi-bin/mmpay-bin/offlinev2ackmsg

收款消息链路：
  → KQRCodeCollectionService.registerPayerMsgRecvListenerImpl
    → VoidKCollectPayerMsgCallback.call
      → notifyPayerMsgListUpdate
        → 刷新收款消息列表
```

---

## 11. Frida 验证脚本

```javascript
// libkinda_android.so 支付消息监听测试
const LIB = 'libkinda_android.so';
const base = Module.findBaseAddress(LIB);
if (!base) {
  console.log('[ERROR] ' + LIB + ' not loaded');
} else {
  console.log('[OK] ' + LIB + ' base=' + base);

  // 1. Hook notifyHKOfflineNewXml
  Interceptor.attach(base.add(0x77DF34), {
    onEnter(args) {
      console.log('[Kinda-Notify] notifyHKOfflineNewXml enter');
    },
    onLeave(retval) {
      console.log('[Kinda-Notify] notifyHKOfflineNewXml ret=' + retval);
    }
  });

  // 2. Hook OfflineUseCase 消息分发
  Interceptor.attach(base.add(0xAB0834), {
    onEnter(args) {
      console.log('[Kinda-Offline] OfflineUseCase msg dispatch enter');
    },
    onLeave(retval) {
      console.log('[Kinda-Offline] OfflineUseCase msg dispatch ret=' + retval);
    }
  });

  // 3. Hook paymsg 处理函数
  Interceptor.attach(base.add(0xBE9628), {
    onEnter(args) {
      console.log('[Kinda-PayMsg] paymsg handler enter');
    },
    onLeave(retval) {
      console.log('[Kinda-PayMsg] paymsg handler ret=' + retval);
    }
  });

  console.log('[OK] Hooks installed');
}
```

---

## 12. 后续分析建议

1. **反编译 `sub_BE9628` 更多细节** — 了解 `publishPayLiteAppGlobalEvent` 的数据格式
2. **追踪 `PayMsgInfo` 结构体** — 在 IDA 中定义结构体，了解支付消息的完整字段
3. **分析 `ackOfflineMsg`** — 了解 ACK 消息中 `paymsg_type` 和 `transactionid` 的具体值
4. **Hook `KCollectPayerMsgCallback.call`** — 验证收款消息回调的实际数据
5. **分析 `TenpayCgiCallback`** — 了解财付通 CGI 请求的响应格式
6. **对比 Java 层和 Native 层** — 确认 `type=49 + <sysmsg>` 是否同时触发 native 层的 `notifyHKOfflineNewXml`

---

## 13. 当前结论

`libkinda_android.so` 是微信 8.0.68 中 Kinda 支付业务框架的 native 实现，核心能力包括：

- 离线支付消息接收和分发（5 种消息类型）
- 收款消息监听回调机制
- 香港钱包离线支付通知处理
- 收银台 UI 流程控制
- 财付通 CGI 请求隧道
- 红包接收和转账流程

它是 native 层支付消息监听的核心目标，包含 `PayMsgInfo`、`paymsg_type`、`PayMsgType` 等关键字符串，以及完整的支付消息分发链路。

最终建议：

```text
支付到账 / 收款通知：
  首选 → Java 层 type=49 + sysmsg/paymsg（简单稳定）
  辅助 → libkinda_android.so 的 notifyHKOfflineNewXml / OfflineUseCase（native 层验证）

收款消息监听：
  首选 → KQRCodeCollectionService.registerPayerMsgRecvListenerImpl + VoidKCollectPayerMsgCallback.call

服务通知 / 公众号支付卡片：
  辅助 → libaff_biz.so 的 XML push Notify 链路

支付密码 / 签名 / 证书：
  分析 → libtenpay_utils.so
```
