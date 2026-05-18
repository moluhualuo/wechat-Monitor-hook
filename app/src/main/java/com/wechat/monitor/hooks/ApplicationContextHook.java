package com.wechat.monitor.hooks;

import android.app.Application;
import android.content.Context;

import com.wechat.monitor.core.HookInstaller;
import com.wechat.monitor.core.MonitorContext;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

public final class ApplicationContextHook implements HookInstaller {
    @Override
    public void install(XposedInterface xposed, MonitorContext monitorContext) throws Throwable {
        Method attachMethod = Application.class.getDeclaredMethod("attach", Context.class);
        intercept(xposed, attachMethod, chain -> {
            Object result = chain.proceed();
            Object arg = chain.getArg(0);
            if (arg instanceof Context) {
                monitorContext.initialize((Context) arg);
            }
            return result;
        });
        monitorContext.log("Hooked Application.attach");
    }
}
