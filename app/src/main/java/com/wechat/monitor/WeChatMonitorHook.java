package com.wechat.monitor;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.wechat.monitor.core.HookInstaller;
import com.wechat.monitor.core.MonitorContext;
import com.wechat.monitor.hooks.ActiveSenderHook;
import com.wechat.monitor.hooks.ContactNicknameHook;
import com.wechat.monitor.hooks.EventBusHook;
import com.wechat.monitor.hooks.SendMessageHook;
import com.wechat.monitor.hooks.SendTriggerHook;
import com.wechat.monitor.hooks.StorageMessageHook;
import com.wechat.monitor.hooks.UiEventHook;
import com.wechat.monitor.hooks.WcdbMessageHook;
import com.wechat.monitor.model.HookTargets;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

public final class WeChatMonitorHook extends XposedModule {
    private static final String BUILD_TOKEN = "2026-05-15-clean-logs";

    private MonitorContext monitorContext;
    private volatile boolean hooksInstalled = false;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        monitorContext = new MonitorContext(param.getProcessName());
        Log.w(MonitorContext.TAG, MonitorContext.TAG + ": onModuleLoaded process=" + param.getProcessName()
            + " framework=" + getFrameworkName() + " api=" + getApiVersion()
            + " build=" + BUILD_TOKEN);
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        if (!HookTargets.WECHAT_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        if (monitorContext == null) {
            monitorContext = new MonitorContext("unknown");
        }
        monitorContext.log("onPackageLoaded package=" + param.getPackageName()
            + " classLoader=" + param.getDefaultClassLoader().getClass().getName());
        hookApplicationAttach();
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!HookTargets.WECHAT_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        if (monitorContext == null) {
            monitorContext = new MonitorContext("unknown");
        }
        ClassLoader cl = param.getClassLoader();
        monitorContext.log("onPackageReady classLoader=" + cl.getClass().getName()
            + " (deferring to Application.attach for Tinker classloader)");
    }

    private void hookApplicationAttach() {
        try {
            Method attachMethod = Application.class.getDeclaredMethod("attach", Context.class);
            attachMethod.setAccessible(true);
            hook(attachMethod).intercept(chain -> {
                Object result = chain.proceed();
                Object arg = chain.getArg(0);
                if (arg instanceof Context) {
                    Context ctx = (Context) arg;
                    monitorContext.initialize(ctx);
                    ClassLoader runtimeCl = ctx.getClassLoader();
                    monitorContext.log("Application.attach classLoader=" + runtimeCl.getClass().getName());
                    if (!hooksInstalled) {
                        monitorContext.setClassLoader(runtimeCl);
                        installHooks(monitorContext);
                    }
                }
                return result;
            });
            monitorContext.log("Hooked Application.attach (deferred hook install)");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook Application.attach: " + t.getMessage());
        }
    }

    private void installHooks(MonitorContext context) {
        HookInstaller[] installers = new HookInstaller[] {
            new SendMessageHook(),
            new ActiveSenderHook(),
            new SendTriggerHook(),
            new WcdbMessageHook(),
            new StorageMessageHook(),
            new ContactNicknameHook(),
            new EventBusHook(),
            new UiEventHook()
        };

        for (HookInstaller installer : installers) {
            try {
                installer.install(this, context);
            } catch (Throwable t) {
                context.log("Installer failed " + installer.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        hooksInstalled = true;
        context.log("Hook installation complete (classLoader=" + context.getClassLoader().getClass().getName() + ")");
    }
}
