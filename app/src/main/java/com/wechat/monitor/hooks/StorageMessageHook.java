package com.wechat.monitor.hooks;

import com.wechat.monitor.core.HookInstaller;
import com.wechat.monitor.core.MonitorContext;
import com.wechat.monitor.model.HookTargets;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

public final class StorageMessageHook implements HookInstaller {
    @Override
    public void install(XposedInterface xposed, MonitorContext monitorContext) {
        hookStorageInsert(xposed, monitorContext);
        hookStorageDispatcher(xposed, monitorContext);
        hookMessageDispatcher(xposed, monitorContext);
        hookPlaintextGetters(xposed, monitorContext);
    }

    private void hookStorageInsert(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            ClassLoader classLoader = monitorContext.getClassLoader();
            Class<?> msgClass = classLoader.loadClass(HookTargets.MESSAGE_INFO);
            Class<?> storageClass = classLoader.loadClass(HookTargets.MESSAGE_STORAGE);

            Method u8Method = storageClass.getDeclaredMethod("U8", msgClass);
            intercept(xposed, u8Method, chain -> {
                Object result = chain.proceed();
                if (result instanceof Long && ((Long) result) >= 0L) {
                    emitStorageMessage(monitorContext, "消息新增", chain.getArg(0));
                }
                return result;
            });

            Method w8Method = storageClass.getDeclaredMethod("W8", msgClass, boolean.class);
            intercept(xposed, w8Method, chain -> {
                Object result = chain.proceed();
                if (result instanceof Long && ((Long) result) >= 0L) {
                    emitStorageMessage(monitorContext, "消息新增", chain.getArg(0));
                }
                return result;
            });
            monitorContext.log("Hooked h8 storage methods");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook storage: " + t.getMessage());
        }
    }

    private void hookStorageDispatcher(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            ClassLoader classLoader = monitorContext.getClassLoader();
            Class<?> msgClass = classLoader.loadClass(HookTargets.MESSAGE_INFO);
            Class<?> addMsgClass = classLoader.loadClass(HookTargets.ADD_MSG);
            Class<?> t9Class = classLoader.loadClass(HookTargets.STORAGE_DISPATCHER);

            Method nMethod = t9Class.getDeclaredMethod("n", msgClass, addMsgClass);
            intercept(xposed, nMethod, chain -> {
                Object result = chain.proceed();
                emitStorageMessage(monitorContext, "消息入库", chain.getArg(0));
                return result;
            });
            monitorContext.log("Hooked xv0.t9.n");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook xv0.t9.n: " + t.getMessage());
        }
    }

    private void hookMessageDispatcher(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            ClassLoader classLoader = monitorContext.getClassLoader();
            Class<?> addMsgClass = classLoader.loadClass(HookTargets.ADD_MSG);
            Class<?> wcClass = classLoader.loadClass(HookTargets.MESSAGE_DISPATCHER);

            Method jMethod = wcClass.getDeclaredMethod("j", addMsgClass);
            intercept(xposed, jMethod, chain -> {
                Object result = chain.proceed();
                emitStorageMessage(monitorContext, "消息分发", chain.getArg(0));
                return result;
            });
            monitorContext.log("Hooked xv0.wc.j");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook xv0.wc.j: " + t.getMessage());
        }
    }

    private void hookPlaintextGetters(XposedInterface xposed, MonitorContext monitorContext) {
        monitorContext.log("Plaintext getter hooks disabled (field read used instead)");
    }

    private void emitStorageMessage(MonitorContext monitorContext, String source, Object msgInfo) {
        if (!monitorContext.isMonitorEnabled() || msgInfo == null) return;

        String talker = monitorContext.readStringField(msgInfo, "field_talker");
        if (talker.isEmpty()) talker = monitorContext.extractMethodValue(msgInfo, "P0");
        if (talker.isEmpty()) talker = monitorContext.extractMethodValue(msgInfo, "getTalker");

        String displayName = extractDisplayName(monitorContext, msgInfo);
        if (!displayName.isEmpty()) {
            monitorContext.rememberTalkerName(talker, displayName);
        } else {
            displayName = monitorContext.getTalkerDisplayName(talker);
        }
        monitorContext.log("Message talker=" + monitorContext.trimValue(talker)
            + " displayName=" + monitorContext.trimValue(displayName)
            + " source=" + source);

        String content = monitorContext.readStringField(msgInfo, "field_content");
        if (content.isEmpty()) content = monitorContext.extractMethodValue(msgInfo, "j");
        if (content.isEmpty()) content = monitorContext.extractMethodValue(msgInfo, "getContent");

        String type = monitorContext.extractMethodValue(msgInfo, "getType");
        String msgId = monitorContext.extractMethodValue(msgInfo, "getMsgId");
        if (talker.isEmpty() || (content.isEmpty() && "0".equals(type))) return;

        String namePart = displayName.isEmpty()
            ? "talker=" + monitorContext.trimValue(talker)
            : "displayName=" + monitorContext.trimValue(displayName) + " | talker=" + monitorContext.trimValue(talker);
        String details = source + " | " + namePart + " | type=" + type
            + " | msgId=" + msgId + " | content=" + monitorContext.trimValue(content);
        String messageType = monitorContext.classifyMessage(details);
        String fingerprint = messageType + "|" + details;

        if (monitorContext.shouldEmit(fingerprint)) {
            monitorContext.logMessage(messageType, details);
        }
    }

    private String extractDisplayName(MonitorContext monitorContext, Object msgInfo) {
        String[] fieldNames = new String[]{
            "field_displayName", "field_nickname", "field_remark", "field_conRemark", "field_digestUser"
        };
        for (String fieldName : fieldNames) {
            String value = monitorContext.readStringField(msgInfo, fieldName);
            if (!value.isEmpty()) {
                return value;
            }
        }

        String[] methodNames = new String[]{
            "getDisplayName", "getNickname", "getRemark", "getDisplayNickName", "getDigestUser"
        };
        for (String methodName : methodNames) {
            String value = monitorContext.extractMethodValue(msgInfo, methodName);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

}
