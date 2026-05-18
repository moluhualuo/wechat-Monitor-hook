package com.wechat.monitor.core;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

public interface HookInstaller {
    void install(XposedInterface xposed, MonitorContext monitorContext) throws Throwable;

    default void intercept(XposedInterface xposed, Method method, XposedInterface.Hooker hooker) {
        method.setAccessible(true);
        xposed.hook(method).intercept(hooker);
    }
}
