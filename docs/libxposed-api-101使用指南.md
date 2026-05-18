# libxposed API 101 使用指南

## 1. API 101 是什么

libxposed API 101 是现代 Xposed 模块 API 的当前首个正式接口版本，用于替代旧版 `XposedBridge` / `IXposedHookLoadPackage` 写法。它的核心特点是：

- 模块入口继承 `io.github.libxposed.api.XposedModule`。
- 生命周期通过 `onModuleLoaded`、`onPackageLoaded`、`onPackageReady` 回调进入。
- Hook 使用 `hook(Method).intercept(Hooker)`，不再使用旧 API 的 `findAndHookMethod`。
- Hook 回调通过 `XposedInterface.Chain` 控制继续执行、读取参数、读取 this 对象和返回结果。
- 模块入口和作用域通过 `META-INF/xposed/*` 资源声明。

## 2. Gradle 依赖

模块开发时使用 `compileOnly`，因为实际运行时由 Xposed 框架提供 API 实现。

```gradle
dependencies {
    compileOnly 'io.github.libxposed:api:101.0.1'
}
```

API 101 当前要求 Android 8.0+，项目应设置：

```gradle
android {
    defaultConfig {
        minSdkVersion 26
    }
}
```

## 3. 模块入口资源

API 101 使用 `app/src/main/resources/META-INF/xposed/` 下的资源文件声明模块。

### 3.1 `java_init.list`

路径：

```text
app/src/main/resources/META-INF/xposed/java_init.list
```

内容是一行一个入口类完整类名：

```text
com.wechat.monitor.WeChatMonitorHook
```

本项目当前入口就是：

```text
com.wechat.monitor.WeChatMonitorHook
```

### 3.2 `module.prop`

路径：

```text
app/src/main/resources/META-INF/xposed/module.prop
```

推荐内容：

```properties
minApiVersion=101
targetApiVersion=101
staticScope=true
```

字段说明：

- `minApiVersion=101`：模块最低要求 API 101。
- `targetApiVersion=101`：模块按 API 101 行为编写。
- `staticScope=true`：模块使用静态作用域文件。

### 3.3 `scope.list`

路径：

```text
app/src/main/resources/META-INF/xposed/scope.list
```

内容是一行一个目标包名：

```text
com.tencent.mm
```

本项目只 Hook 微信，所以只声明 `com.tencent.mm`。

## 4. 入口类写法

入口类继承 `XposedModule`：

```java
package com.wechat.monitor;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import io.github.libxposed.api.XposedModule;

public final class WeChatMonitorHook extends XposedModule {
    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        log(Log.INFO, "WeChatMonitor", "process=" + param.getProcessName());
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.Q)
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        if (!"com.tencent.mm".equals(param.getPackageName())) {
            return;
        }

        ClassLoader classLoader = param.getDefaultClassLoader();
        installHooks(classLoader);
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!"com.tencent.mm".equals(param.getPackageName())) {
            return;
        }

        ClassLoader classLoader = param.getClassLoader();
    }

    private void installHooks(ClassLoader classLoader) {
    }
}
```

## 5. 生命周期说明

### 5.1 `onModuleLoaded(ModuleLoadedParam param)`

模块被加载到目标进程时调用一次。

适合做：

- 记录进程名。
- 初始化模块级状态。
- 打印框架信息。

常用 API：

```java
param.getProcessName();
param.isSystemServer();
getApiVersion();
getFrameworkName();
getFrameworkVersion();
getFrameworkVersionCode();
getFrameworkProperties();
```

### 5.2 `onPackageLoaded(PackageLoadedParam param)`

有代码的包被加载时调用。这里可以拿到默认 ClassLoader：

```java
ClassLoader classLoader = param.getDefaultClassLoader();
```

适合做：

- 判断目标包名。
- 加载目标 App 类。
- 安装大部分 Java Hook。

注意：`getDefaultClassLoader()` 需要 Android Q 及以上注解约束，入口方法通常加：

```java
@RequiresApi(Build.VERSION_CODES.Q)
```

### 5.3 `onPackageReady(PackageReadyParam param)`

AppComponentFactory 准备完成后调用，可以拿到最终 ClassLoader：

```java
ClassLoader classLoader = param.getClassLoader();
```

适合做：

- 处理自定义 AppComponentFactory 场景。
- 刷新 ClassLoader。
- 延迟安装依赖 Application 初始化后的 Hook。

## 6. Hook Method 的标准写法

API 101 使用 `hook(Method).intercept(Hooker)`。

```java
Class<?> targetClass = classLoader.loadClass("com.example.Target");
Method method = targetClass.getDeclaredMethod("targetMethod", String.class);
method.setAccessible(true);

hook(method)
    .setExceptionMode(ExceptionMode.PROTECTIVE)
    .intercept(chain -> {
        Object thisObject = chain.getThisObject();
        Object arg0 = chain.getArg(0);

        Object result = chain.proceed();

        return result;
    });
```

## 7. Chain 的用法

`XposedInterface.Chain` 表示当前 Hook 调用链。

### 7.1 获取当前方法

```java
Executable executable = chain.getExecutable();
```

### 7.2 获取 this 对象

```java
Object thisObject = chain.getThisObject();
```

静态方法没有 this，返回 `null`。

### 7.3 获取参数

```java
Object arg0 = chain.getArg(0);
List<Object> args = chain.getArgs();
```

`getArgs()` 返回不可变列表。如果要修改参数，不要直接改列表，应使用 `proceed(Object[] args)`。

### 7.4 继续执行原逻辑

```java
Object result = chain.proceed();
return result;
```

如果不想改变返回值，标准写法就是返回 `chain.proceed()` 的结果。

### 7.5 修改参数后继续执行

```java
Object[] newArgs = new Object[] { "new value" };
Object result = chain.proceed(newArgs);
return result;
```

### 7.6 修改 this 后继续执行

```java
Object result = chain.proceedWith(newThisObject);
return result;
```

静态方法不要使用 `proceedWith`。

## 8. Hooker 返回值规则

### 8.1 普通有返回值方法

必须返回目标方法兼容的结果：

```java
.intercept(chain -> {
    Object result = chain.proceed();
    return result;
});
```

### 8.2 void 方法或构造方法

返回值会被框架忽略，但 Java 里仍然要返回 `null`：

```java
.intercept(chain -> {
    chain.proceed();
    return null;
});
```

### 8.3 阻断原方法

如果不调用 `chain.proceed()`，就不会继续执行后续 Hook 和原方法：

```java
.intercept(chain -> {
    return "fake result";
});
```

本项目是监听模块，默认不阻断微信逻辑，Hooker 基本都调用 `chain.proceed()`。

## 9. 异常模式

API 101 的 Hook 支持异常处理模式：

```java
hook(method)
    .setExceptionMode(ExceptionMode.PROTECTIVE)
    .intercept(hooker);
```

常用模式：

- `ExceptionMode.PROTECTIVE`：Hooker 抛异常时框架捕获并记录，尽量不影响目标 App，适合正式监听模块。
- `ExceptionMode.PASSTHROUGH`：Hooker 抛异常会继续向上抛，适合调试。
- `ExceptionMode.DEFAULT`：使用框架或模块默认配置。

本项目统一使用 `PROTECTIVE`，避免 Hook 逻辑异常导致微信崩溃。

## 10. 调用原始方法

如果需要绕过 Hook 链调用原方法，可使用 `getInvoker(method)`：

```java
Object result = getInvoker(method)
    .setType(Invoker.Type.ORIGIN)
    .invoke(thisObject, args);
```

本项目当前只监听消息，不需要主动调用微信原始方法，所以主要使用 `chain.proceed()`。

## 11. Hook 构造方法

构造方法使用 `Constructor`：

```java
Constructor<?> constructor = targetClass.getDeclaredConstructor(String.class);
constructor.setAccessible(true);

hook(constructor).intercept(chain -> {
    Object result = chain.proceed();
    return result;
});
```

创建原始实例可用：

```java
Object instance = getInvoker(constructor)
    .setType(Invoker.Type.ORIGIN)
    .newInstance("arg");
```

## 12. Hook 静态初始化块

API 101 支持 Hook 类的 `<clinit>`：

```java
hookClassInitializer(targetClass).intercept(chain -> {
    Object result = chain.proceed();
    return result;
});
```

注意：如果类已经初始化，静态初始化 Hook 不会再触发。

## 13. 日志写法

模块内推荐使用 Xposed API 的 `log` 方法：

```java
log(Log.INFO, "WeChatMonitor", "message");
log(Log.ERROR, "WeChatMonitor", "failed", throwable);
```

本项目在 `MonitorContext.log()` 中同时使用 Android `Log.i` 输出，方便通过 logcat 查看。

## 14. 远程配置和远程文件

API 101 提供远程偏好和远程文件接口：

```java
SharedPreferences preferences = getRemotePreferences("group");
String[] files = listRemoteFiles();
ParcelFileDescriptor fd = openRemoteFile("fileName");
```

注意：这些接口依赖框架能力，可通过属性判断：

```java
boolean remoteSupported = (getFrameworkProperties() & PROP_CAP_REMOTE) != 0;
```

本项目当前使用微信进程内 `SharedPreferences` 和公共日志目录，没有依赖远程配置。

## 15. 本项目的 API 101 模块结构

当前项目按模块化拆分：

```text
com.wechat.monitor.WeChatMonitorHook        # API 101 入口
com.wechat.monitor.core.MonitorContext      # 运行态、日志、配置、去重、昵称缓存、界面标题探针
com.wechat.monitor.core.HookInstaller       # Hook 子模块统一接口
com.wechat.monitor.model.HookTargets        # 微信目标类常量
com.wechat.monitor.hooks.ApplicationContextHook
com.wechat.monitor.hooks.SendMessageHook
com.wechat.monitor.hooks.StorageMessageHook
com.wechat.monitor.hooks.ContactNicknameHook
com.wechat.monitor.hooks.EventBusHook
com.wechat.monitor.hooks.UiEventHook
```

入口安装顺序：

1. `SendMessageHook`：监听发送链路。
2. `StorageMessageHook`：监听入库、分发和明文 getter。
3. `ContactNicknameHook`：Hook `com.tencent.mm.storage.l3.n(String, boolean)`，从返回的 `com.tencent.mm.storage.a3`（继承 `com.tencent.mm.contact.r`）上提取备注/昵称，建立 `talker -> displayName` 缓存。优先级：`h2()` > `O0()` > `u0()` > 字段回退。
4. `EventBusHook`：监听消息相关事件。
5. `UiEventHook`：监听页面事件、红包页、转账页。

## 16. 本项目 Hook 子模块模板

新增 Hook 子模块时实现 `HookInstaller`：

```java
package com.wechat.monitor.hooks;

import com.wechat.monitor.core.HookInstaller;
import com.wechat.monitor.core.MonitorContext;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

public final class ExampleHook implements HookInstaller {
    @Override
    public void install(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            Class<?> targetClass = monitorContext.getClassLoader().loadClass("target.ClassName");
            Method method = targetClass.getDeclaredMethod("methodName", String.class);
            intercept(xposed, method, chain -> {
                Object result = chain.proceed();
                Object arg0 = chain.getArg(0);
                monitorContext.logMessage("示例", "arg0=" + monitorContext.trimValue(String.valueOf(arg0)));
                return result;
            });
            monitorContext.log("Hooked target.ClassName.methodName");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook example: " + t.getMessage());
        }
    }
}
```

然后在 `WeChatMonitorHook.installHooks()` 中加入：

```java
new ExampleHook()
```

## 17. 构建验证

使用本地 Gradle 构建：

```bash
"D:/ck/gardle/gradle-8.7/bin/gradle" -p "F:/nx/wx Android/WeChatMonitor" lintRelease assembleRelease
```

成功后 APK 路径：

```text
F:/nx/wx Android/WeChatMonitor/app/build/outputs/apk/release/app-release.apk
```

检查 APK 是否包含 API 101 入口文件：

```bash
unzip -l "F:/nx/wx Android/WeChatMonitor/app/build/outputs/apk/release/app-release.apk" | grep -E 'META-INF/xposed|io.github.libxposed.api.XposedModule'
```

应看到：

```text
META-INF/xposed/java_init.list
META-INF/xposed/module.prop
META-INF/xposed/scope.list
META-INF/services/io.github.libxposed.api.XposedModule
```

## 18. 运行验证

在模块管理器中启用模块并选择微信作用域后，重启微信进程。

logcat 中应出现类似日志：

```text
WeChatMonitor: onModuleLoaded process=com.tencent.mm framework=... api=101
WeChatMonitor: onPackageLoaded package=com.tencent.mm process=com.tencent.mm
WeChatMonitor: Hooked Application.attach
WeChatMonitor: Hook installation complete
```

消息触发后应出现：

```text
[com.tencent.mm] [发送请求] ...
[com.tencent.mm] [消息] ...
[com.tencent.mm] [明文消息] ...
[com.tencent.mm] [支付消息] ...
```

模块设置页会显示最近接收消息预览，优先读取模块本地 SQLite 预览库；如果库里没有数据，会回退扫描公共日志目录。当前仅保留真正的“消息”记录，不展示发送请求、事件命中或界面日志。

消息日志与预览现在会优先显示 `displayName (talker)`；如果当前 Hook 点还拿不到昵称，则暂时只显示 `talker`，并在后续同会话命中昵称字段后复用缓存昵称。联系人昵称缓存来自 `ContactNicknameHook`，它会在微信联系人存储返回联系人对象时提取备注名、昵称、别名等字段并建立映射；如果联系人存储链路没有命中，还会回退复用当前聊天页标题作为单聊/群聊名称。在定位阶段，`UiEventHook` 还会记录聊天页 activity 类名、候选字段值、候选 getter 返回值和顶部 `TextView` 快照，便于锁定真实昵称来源。

由于目标设备上文件日志位于 `/storage/emulated/0/Documents/WeChatMonitor/logs`，Android 11+ 上设置页读取公共目录前需要文件访问权限；当前设置页会在首次进入时跳转系统授权页，请授予后再回到设置页查看预览。

如果结构化消息预览仍为空，设置页会继续回退显示最近日志原文尾部，这样即使某次字段解析失败，页面上也能直接看到微信进程最近写入的原始监控行。

设置页会在 `onResume()` 立即刷新，并在进入页面后短延迟再次刷新两次，以覆盖微信进程刚写入日志、用户马上切回模块页的场景。

当预览区为空时，设置页会直接显示诊断信息：
- 未找到日志文件时，展示当前扫描的候选目录。
- 找到日志文件但没有匹配到 `[消息] talker=... content=...` 记录时，明确提示日志格式未命中。
- 读取异常时，展示“预览读取失败”。

文件日志仍持续写入公共日志目录。

如果文件日志开启，公共日志目录默认是：

```text
/sdcard/Documents/WeChatMonitor/logs
```

宿主进程回退目录：

```text
Android/data/<当前进程包名>/files/logs
```

日志文件名格式：

```text
wechat_monitor_yyyyMMdd.txt
```

## 19. 常见问题

### 19.1 模块未加载

检查：

- APK 内是否存在 `META-INF/xposed/java_init.list`。
- `java_init.list` 中入口类名是否正确。
- `module.prop` 是否声明 `minApiVersion=101`。
- 模块管理器是否支持现代 libxposed API。
- 作用域是否包含 `com.tencent.mm`。

### 19.2 Hook 不触发

检查：

- 是否进入了 `onPackageLoaded`。
- 当前微信版本混淆类名是否变化。
- `param.getDefaultClassLoader()` 是否能加载目标类。
- 目标方法签名是否变化。
- 被 Hook 方法是否被内联；必要时考虑更换 Hook 点或使用 `deoptimize`。

### 19.3 微信崩溃

建议：

- Hook 注册统一使用 `ExceptionMode.PROTECTIVE`。
- Hooker 内监听逻辑放在 `try/catch` 或子模块 catch 中。
- 默认调用 `chain.proceed()`，不要阻断微信原流程。
- 不要在 Hooker 中做耗时 IO；本项目只做短日志写入。

### 19.4 参数改不生效

`chain.getArgs()` 返回不可变列表，不能直接修改。应使用：

```java
chain.proceed(new Object[] { newArg0, newArg1 });
```

### 19.5 返回值类型错误

Hooker 返回值必须和原方法返回类型兼容。void 方法返回 `null` 即可。
