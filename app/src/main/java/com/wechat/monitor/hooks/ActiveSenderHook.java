package com.wechat.monitor.hooks;

import com.wechat.monitor.core.HookInstaller;
import com.wechat.monitor.core.MonitorContext;
import com.wechat.monitor.sender.ActiveSender;
import com.wechat.monitor.sender.CommandPoller;
import com.wechat.monitor.sender.SendCommandReceiver;

import io.github.libxposed.api.XposedInterface;

/**
 * ActiveSender 预热器 + 命令通道启动器。
 *
 * 在所有业务 Hook 完成 ClassLoader 注入后:
 *   1. 所有微信进程(主 + 子)都预热 ActiveSender 反射缓存,因为子进程也可能被未来的扩展使用
 *   2. 但发送通道(广播 receiver + 文件轮询)只在主进程 com.tencent.mm 启动
 *      子进程(:push / :appbrand0 / :appbrand1 等)的发送上下文不完整,
 *      调用 px0.r1.a 会抛 InvocationTargetException,所以不接广播也不读文件,避免噪音
 */
public final class ActiveSenderHook implements HookInstaller {

    private static final String MAIN_PROCESS = "com.tencent.mm";

    @Override
    public void install(XposedInterface xposed, MonitorContext monitorContext) {
        boolean ok = ActiveSender.warmUp(monitorContext);
        if (ok) {
            monitorContext.log("ActiveSenderHook 预热完成, 调用 ActiveSender.sendText 即可主动发送文本");
        } else {
            monitorContext.log("ActiveSenderHook 预热失败, 调用 sendText 时会尝试延迟初始化");
        }

        if (!MAIN_PROCESS.equals(monitorContext.getProcessName())) {
            monitorContext.log("非主进程 (" + monitorContext.getProcessName()
                + "), 跳过 SendCommandReceiver / CommandPoller 注册");
            return;
        }

        try {
            SendCommandReceiver.register(monitorContext);
        } catch (Throwable t) {
            monitorContext.log("SendCommandReceiver 注册失败: " + t.getMessage());
        }
        try {
            CommandPoller.start(monitorContext);
        } catch (Throwable t) {
            monitorContext.log("CommandPoller 启动失败: " + t.getMessage());
        }
    }
}
