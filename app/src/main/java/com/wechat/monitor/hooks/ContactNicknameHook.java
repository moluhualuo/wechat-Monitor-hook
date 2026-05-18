package com.wechat.monitor.hooks;

import com.wechat.monitor.core.HookInstaller;
import com.wechat.monitor.core.MonitorContext;
import com.wechat.monitor.model.HookTargets;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

public final class ContactNicknameHook implements HookInstaller {
    @Override
    public void install(XposedInterface xposed, MonitorContext monitorContext) {
        hookContactStorage(xposed, monitorContext);
    }

    private void hookContactStorage(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            ClassLoader classLoader = monitorContext.getClassLoader();
            Class<?> contactClass = classLoader.loadClass(HookTargets.CONTACT_INFO);
            Class<?> storageClass = classLoader.loadClass(HookTargets.CONTACT_STORAGE);
            monitorContext.log("Contact storage class=" + storageClass.getName() + " contactClass=" + contactClass.getName());

            Method method = monitorContext.findMethod(storageClass, new String[]{"n"}, String.class, boolean.class);
            if (method == null || !contactClass.isAssignableFrom(method.getReturnType())) {
                monitorContext.log("No contact storage method matched candidate names");
                return;
            }

            intercept(xposed, method, chain -> {
                Object result = chain.proceed();
                Object usernameArg = chain.getArg(0);
                rememberContact(monitorContext, usernameArg == null ? "" : String.valueOf(usernameArg), result);
                return result;
            });
            monitorContext.log("Hooked contact storage method: n -> " + method.getName());
        } catch (Throwable t) {
            monitorContext.log("Failed to hook contact storage: " + t.getMessage());
        }
    }

    private void rememberContact(MonitorContext monitorContext, String requestedTalker, Object contact) {
        if (contact == null) {
            if (!requestedTalker.isEmpty()) {
                monitorContext.log("Contact lookup returned null for talker=" + monitorContext.trimValue(requestedTalker));
            }
            return;
        }
        String talker = monitorContext.firstNonEmptyField(contact,
            "field_username", "field_encryptUsername", "field_talker", "field_userName");
        if (talker.isEmpty()) {
            talker = monitorContext.firstNonEmptyMethod(contact,
                "e1", "getUsername", "getUserName", "getTalker", "getEncryptUsername");
        }
        if (talker.isEmpty()) {
            talker = requestedTalker;
        }
        String displayName = monitorContext.firstNonEmptyMethod(contact,
            "h2", "O0", "u0", "getDisplayName", "getRemark", "getNickname", "getAlias");
        if (displayName.isEmpty()) {
            displayName = monitorContext.firstNonEmptyField(contact,
                "field_conRemark", "field_nickname", "field_alias", "field_conRemarkPYFull", "field_conRemarkPYShort");
        }
        boolean isNew = monitorContext.rememberTalkerName(talker, displayName);
        if (isNew) {
            monitorContext.log("Contact resolved talker=" + monitorContext.trimValue(talker)
                + " displayName=" + monitorContext.trimValue(displayName));
        }
    }
}
