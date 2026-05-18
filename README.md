# 微信聊天消息监听模块

> **目标微信版本：8.0.68**（其他版本未做兼容验证，类名/字段名混淆产物可能不一致）

基于 LSPosed/Xposed 框架开发的微信聊天消息监听模块，支持监听普通消息、支付消息、红包消息等多种类型。

## 功能特性

- ✅ 实时监听微信聊天消息
- ✅ 支持多种消息类型识别
- ✅ 支付消息监听（钱包更新、C2C转账）
- ✅ 红包消息监听（收发红包、红包详情）
- ✅ 本地日志记录
- ✅ 日志导出功能
- ✅ 可配置的监听选项

## 环境要求

- Android 5.0+ (API 21+)
- 已安装 LSPosed 或 Xposed 框架
- 已 Root 的设备
- **微信版本：8.0.68**（本模块所有 Hook 点、类名/字段名映射均基于此版本逆向得出）

## 安装步骤

### 1. 编译项目

```bash
# 克隆项目
cd WeChatMonitor

# 编译 APK
./gradlew assembleRelease

# APK 位置
# app/build/outputs/apk/release/app-release.apk
```

### 2. 安装模块

1. 将编译好的 APK 安装到手机
2. 在 LSPosed/Xposed 管理器中激活模块
3. 在模块作用域中勾选微信
4. 重启微信应用

### 3. 配置模块

1. 打开模块应用
2. 启用监听开关
3. 启用日志记录
4. 查看日志保存位置

## 核心实现

### 1. Hook 消息接收监听器

```java
// Hook ox0/xc 接口的 J0 方法
Class<?> listenerClass = XposedHelpers.findClass("ox0.xc", lpparam.classLoader);

XposedHelpers.findAndHookMethod(
    listenerClass,
    "J0",
    XposedHelpers.findClass("com.tencent.mm.modelbase.p0", lpparam.classLoader),
    new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Object messageInfo = param.args[0];
            // 解析并记录消息
            parseAndLogMessage(messageInfo);
        }
    }
);
```

### 2. Hook 聊天消息通知事件

```java
// Hook ChatMsgNotifyEvent 构造函数
Class<?> eventClass = XposedHelpers.findClass(
    "com.tencent.mm.autogen.events.ChatMsgNotifyEvent",
    lpparam.classLoader
);

XposedHelpers.findAndHookConstructor(
    eventClass,
    new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Object event = param.thisObject;
            Object messageData = XposedHelpers.getObjectField(event, "g");
            // 记录消息数据
        }
    }
);
```

### 3. 消息解析流程

```
消息对象 (p0)
    ↓
获取内部数据 (a)
    ↓
获取消息内容 (h)
    ↓
转换为字符串
    ↓
解析 XML 格式
    ↓
记录到日志文件
```

## 项目结构

```
WeChatMonitor/
├── app/
│   ├── src/main/
│   │   ├── java/com/wechat/monitor/
│   │   │   ├── WeChatMonitorHook.java      # Xposed Hook 主类
│   │   │   ├── MessageLogger.java          # 消息日志记录器
│   │   │   ├── ConfigManager.java          # 配置管理
│   │   │   └── SettingsActivity.java       # 设置界面
│   │   ├── assets/
│   │   │   └── xposed_init                 # Xposed 入口配置
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_settings.xml   # 设置界面布局
│   │   │   └── values/
│   │   │       ├── strings.xml             # 字符串资源
│   │   │       └── arrays.xml              # 数组资源
│   │   └── AndroidManifest.xml             # 清单文件
│   └── build.gradle                        # 模块构建配置
├── build.gradle                            # 项目构建配置
└── settings.gradle                         # 项目设置
```

## 使用说明

### 启动监听

1. 打开模块应用
2. 开启"启用监听"开关
3. 开启"记录消息日志"开关
4. 打开微信，开始聊天

### 查看日志

日志文件优先查看：
- `/storage/emulated/0/Android/data/com.tencent.mm/files/logs/`

兼容和迁移目录：
- `/sdcard/WeChatMonitor/logs/`
- `/storage/emulated/0/Android/data/com.wechat.monitor/files/logs/`

日志格式：
```
[2024-01-01 12:00:00] [消息] 消息内容...
[2024-01-01 12:00:01] [消息接收] 消息内容...
```

### 导出日志

1. 点击"导出日志"按钮
2. 日志将导出到应用私有目录
3. 可以通过文件管理器查看

### 清空日志

1. 点击"清空日志"按钮
2. 所有历史日志将被删除

## 技术细节

### Hook 点分析

1. **消息接收监听器** (`ox0/xc.J0`)
   - 所有消息监听器的统一接口
   - 参数包含完整的消息信息

2. **聊天消息通知事件** (`ChatMsgNotifyEvent`)
   - UI 更新事件
   - 包含消息数据对象

3. **数据库操作** (`com.tencent.wcdb.Cursor`)
   - 消息存储操作
   - 可以捕获消息插入

### 消息数据结构

```java
// 消息信息对象
com.tencent.mm.modelbase.p0 {
    a: Lm05/i4 {           // 内部数据
        h: Lm05/xp5        // 消息内容
        o: int             // 时间戳
    }
}

// 消息内容格式
<sysmsg>
    <paymsg>
        <PayMsgType>...</PayMsgType>
    </paymsg>
    <chatrecordstartexport>
        <DeviceName>...</DeviceName>
        <ContactName>...</ContactName>
    </chatrecordstartexport>
</sysmsg>
```

## 注意事项

⚠️ **重要提示**

1. **法律合规**: 仅用于学习和研究目的，不得用于非法用途
2. **隐私保护**: 尊重他人隐私，不要监听他人聊天
3. **数据安全**: 日志文件可能包含敏感信息，请妥善保管
4. **稳定性**: 模块可能影响微信稳定性，请谨慎使用
5. **兼容性**: 仅在**微信 8.0.68** 上验证，其他版本可能因混淆产物变化导致 Hook 失效

## 常见问题

### Q: 模块无法激活？
A: 确保 LSPosed/Xposed 框架正确安装，并授予模块 Root 权限。

### Q: 没有监听到消息？
A: 
1. 检查模块是否正确激活
2. 确认微信在模块作用域中
3. 重启微信应用

### Q: 日志文件找不到？
A: 
1. 检查存储权限
2. 优先检查微信进程目录 `/storage/emulated/0/Android/data/com.tencent.mm/files/logs/`
3. 再检查模块公共目录 `/sdcard/WeChatMonitor/logs/`

### Q: 微信崩溃？
A: 
1. 禁用模块
2. 清除微信数据
3. 重新安装微信

## 更新日志

### v1.0 (2024-01-01)
- ✅ 初始版本发布
- ✅ 实现基础消息监听功能
- ✅ 添加日志记录功能
- ✅ 实现设置界面

## 开发计划

- [ ] 支持更多消息类型
- [ ] 添加消息过滤功能
- [ ] 实现远程日志上传
- [ ] 添加消息通知功能
- [ ] 支持多设备同步

## 许可证

本项目仅供学习研究使用，请勿用于非法用途。

## 联系方式

如有问题或建议，请提交 Issue。
