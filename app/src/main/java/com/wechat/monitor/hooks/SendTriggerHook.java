package com.wechat.monitor.hooks;

import android.app.Activity;

import com.wechat.monitor.core.HookInstaller;
import com.wechat.monitor.core.MonitorContext;
import com.wechat.monitor.model.HookTargets;
import com.wechat.monitor.sender.SendCommandFile;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * 主动发送触发器。
 *
 * 通过 Hook 微信主界面 LauncherUI 的 onResume 作为节流触发点：
 * 每次用户切回微信主界面（或刚启动微信）时，检查 /sdcard/WeChatMonitor/send.json，
 * 如果存在则读取 talker/content/type 调用 ActiveSender.sendText。
 *
 * 实际 Hook 的是 Activity.onResume，在回调里按类名过滤 LauncherUI，避免依赖
 * LauncherUI 是否 override 了 onResume 方法（多数 Activity 子类不会 override）。
 * 节流由 SendCommandFile 内部保证，5 秒内重复触发会被忽略。
 */
public final class SendTriggerHook implements HookInstaller {

    private static final String MAIN_PROCESS = "com.tencent.mm";

    @Override
    public void install(XposedInterface xposed, MonitorContext monitorContext) {
        if (!MAIN_PROCESS.equals(monitorContext.getProcessName())) {
            monitorContext.log("非主进程 (" + monitorContext.getProcessName()
                + "), 跳过 SendTriggerHook");
            return;
        }
        try {
            Method onResumeMethod = Activity.class.getDeclaredMethod("onResume");
            intercept(xposed, onResumeMethod, chain -> {
                Object result = chain.proceed();
                Object thisObj = chain.getThisObject();
                if (thisObj != null) {
                    String className = thisObj.getClass().getName();
                    if (HookTargets.LAUNCHER_UI.equals(className)) {
                        try {
                            SendCommandFile.pollAndSend(monitorContext);
                        } catch (Throwable t) {
                            monitorContext.log("SendCommandFile.pollAndSend 异常: " + t.getMessage());
                        }
                    }
                }
                return result;
            });
            monitorContext.log("Hooked LauncherUI.onResume (via Activity.onResume) -> SendCommandFile");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook SendTriggerHook: " + t.getMessage());
        }
    }
}
