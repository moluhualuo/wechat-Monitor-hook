package com.wechat.monitor.hooks;

import com.wechat.monitor.core.HookInstaller;
import com.wechat.monitor.core.MonitorContext;
import com.wechat.monitor.model.HookTargets;

import java.lang.reflect.Method;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

public final class EventBusHook implements HookInstaller {
    @Override
    public void install(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            Class<?> eventClass = monitorContext.getClassLoader().loadClass(HookTargets.EVENT);
            Method eMethod = eventClass.getDeclaredMethod("e");
            intercept(xposed, eMethod, chain -> {
                Object result = chain.proceed();
                emitEventBus(monitorContext, "IEvent.e", chain.getThisObject());
                return result;
            });
            monitorContext.log("Hooked IEvent.e");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook IEvent.e: " + t.getMessage());
        }
    }

    private void emitEventBus(MonitorContext monitorContext, String source, Object eventObj) {
        if (eventObj == null || !monitorContext.isMonitorEnabled()) return;

        String className = eventObj.getClass().getName();
        String lowerClassName = className.toLowerCase(Locale.ROOT);
        if (!lowerClassName.startsWith("com.tencent.mm.autogen.events.")) return;
        if (!lowerClassName.contains("chatmsg") && !lowerClassName.contains("sendmsg")
            && !lowerClassName.contains("message") && !lowerClassName.contains("notify")) return;
        if (!monitorContext.canTraceEventBus()) return;

        monitorContext.logMessage("事件命中", source + " | event=" + className);
    }
}
