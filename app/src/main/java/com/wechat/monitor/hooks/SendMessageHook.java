package com.wechat.monitor.hooks;

import com.wechat.monitor.core.HookInstaller;
import com.wechat.monitor.core.MonitorContext;
import com.wechat.monitor.model.HookTargets;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

public final class SendMessageHook implements HookInstaller {
    @Override
    public void install(XposedInterface xposed, MonitorContext monitorContext) {
        hookQ1(xposed, monitorContext);
        hookQ1Setters(xposed, monitorContext);
        hookR1(xposed, monitorContext);
        hookSendDispatch(xposed, monitorContext);
        hookNetSceneSend(xposed, monitorContext);
        hookNetSceneSendInit(xposed, monitorContext);
    }

    private void hookQ1(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            Class<?> q1Class = monitorContext.getClassLoader().loadClass(HookTargets.Q1);
            Method bMethod = q1Class.getDeclaredMethod("b");
            intercept(xposed, bMethod, chain -> {
                emitQ1SendRequest(monitorContext, "px0.q1.b", chain.getThisObject());
                return chain.proceed();
            });

            Method aMethod = q1Class.getDeclaredMethod("a");
            intercept(xposed, aMethod, chain -> {
                emitQ1SendRequest(monitorContext, "px0.q1.a", chain.getThisObject());
                return chain.proceed();
            });
            monitorContext.log("Hooked px0.q1 methods");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook px0.q1: " + t.getMessage());
        }
    }

    private void hookR1(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            Class<?> r1Class = monitorContext.getClassLoader().loadClass(HookTargets.R1);
            Method r1Method = r1Class.getDeclaredMethod("a", String.class);
            intercept(xposed, r1Method, chain -> {
                Object result = chain.proceed();
                Object arg = chain.getArg(0);
                if (arg instanceof String && !((String) arg).isEmpty()) {
                    monitorContext.logMessage("发送链路", "px0.r1.a | talker=" + monitorContext.trimValue((String) arg));
                }
                return result;
            });
            monitorContext.log("Hooked px0.r1.a");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook px0.r1.a: " + t.getMessage());
        }
    }

    private void hookNetSceneSend(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            Class<?> r0Class = monitorContext.getClassLoader().loadClass(HookTargets.NET_SCENE_SEND);
            Class<?> q1Class = monitorContext.getClassLoader().loadClass(HookTargets.Q1);

            for (Method m : r0Class.getDeclaredMethods()) {
                if ("doScene".equals(m.getName())) {
                    intercept(xposed, m, chain -> {
                        if (monitorContext.isMonitorEnabled()) {
                            String type = monitorContext.extractMethodValue(chain.getThisObject(), "getType");
                            monitorContext.logMessage("发送网络", "px0.r0.doScene | type=" + type);
                        }
                        return chain.proceed();
                    });
                }
            }

            for (Method m : r0Class.getDeclaredMethods()) {
                if ("onGYNetEnd".equals(m.getName())) {
                    intercept(xposed, m, chain -> {
                        if (monitorContext.isMonitorEnabled()) {
                            monitorContext.logMessage("发送回调", "px0.r0.onGYNetEnd | args=" + chain.getArgs().size());
                        }
                        return chain.proceed();
                    });
                }
            }
            monitorContext.log("Hooked px0.r0 (NetSceneSend)");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook px0.r0: " + t.getMessage());
        }
    }

    private void hookSendDispatch(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            Class<?> dispatchClass = monitorContext.getClassLoader().loadClass(HookTargets.SEND_DISPATCH);
            Class<?> q1Class = monitorContext.getClassLoader().loadClass(HookTargets.Q1);
            Method jMethod = dispatchClass.getDeclaredMethod("j", q1Class);
            intercept(xposed, jMethod, chain -> {
                Object result = chain.proceed();
                emitQ1SendRequest(monitorContext, "fo1.g.j", chain.getArg(0));
                return result;
            });
            monitorContext.log("Hooked fo1.g.j (send dispatch)");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook fo1.g.j: " + t.getMessage());
        }
    }

    private void hookQ1Setters(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            Class<?> q1Class = monitorContext.getClassLoader().loadClass(HookTargets.Q1);

            hookQ1Setter(xposed, monitorContext, q1Class, "g", String.class, "talker");
            hookQ1Setter(xposed, monitorContext, q1Class, "e", String.class, "content");
            hookQ1Setter(xposed, monitorContext, q1Class, "h", int.class, "type");

            monitorContext.log("Hooked px0.q1 setters (g/e/h)");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook px0.q1 setters: " + t.getMessage());
        }
    }

    private void hookQ1Setter(XposedInterface xposed, MonitorContext monitorContext,
                              Class<?> q1Class, String methodName, Class<?> paramType, String semantic) {
        try {
            Method setter = q1Class.getDeclaredMethod(methodName, paramType);
            intercept(xposed, setter, chain -> {
                Object result = chain.proceed();
                if (monitorContext.isMonitorEnabled()) {
                    Object arg = chain.getArg(0);
                    String argText = arg == null ? "" : monitorContext.trimValue(String.valueOf(arg));
                    String state = dumpQ1State(monitorContext, chain.getThisObject());
                    String fingerprint = "发送构造|" + methodName + "|" + argText + "|" + state;
                    if (monitorContext.shouldEmit(fingerprint)) {
                        monitorContext.logMessage("发送构造",
                            "px0.q1." + methodName + "(" + semantic + ") | arg=" + argText + " | " + state);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            monitorContext.log("Failed to hook px0.q1." + methodName + ": " + t.getMessage());
        }
    }

    private void hookNetSceneSendInit(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            Class<?> r0Class = monitorContext.getClassLoader().loadClass(HookTargets.NET_SCENE_SEND);

            int initCount = 0;
            for (Constructor<?> ctor : r0Class.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                xposed.hook(ctor).intercept(chain -> {
                    Object result = chain.proceed();
                    if (monitorContext.isMonitorEnabled()) {
                        int argCount = chain.getArgs().size();
                        StringBuilder argsText = new StringBuilder();
                        for (int i = 0; i < argCount; i++) {
                            if (i > 0) argsText.append(", ");
                            Object arg = chain.getArg(i);
                            argsText.append(arg == null ? "null" : monitorContext.trimValue(String.valueOf(arg)));
                        }
                        String fingerprint = "发送Scene|R0.<init>|" + argCount + "|" + argsText;
                        if (monitorContext.shouldEmit(fingerprint)) {
                            monitorContext.logMessage("发送Scene",
                                "px0.r0.<init> | argc=" + argCount + " | args=[" + argsText + "]");
                        }
                    }
                    return result;
                });
                initCount++;
            }

            try {
                Method getType = r0Class.getDeclaredMethod("getType");
                intercept(xposed, getType, chain -> {
                    Object result = chain.proceed();
                    if (monitorContext.isMonitorEnabled()) {
                        String typeText = result == null ? "" : String.valueOf(result);
                        String fingerprint = "发送Scene|R0.getType|" + typeText;
                        if (monitorContext.shouldEmit(fingerprint)) {
                            monitorContext.logMessage("发送Scene", "px0.r0.getType | type=" + typeText);
                        }
                    }
                    return result;
                });
            } catch (Throwable t) {
                monitorContext.log("Failed to hook px0.r0.getType: " + t.getMessage());
            }

            monitorContext.log("Hooked px0.r0.<init> (overloads=" + initCount + ") and getType");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook px0.r0 init/getType: " + t.getMessage());
        }
    }

    private String dumpQ1State(MonitorContext monitorContext, Object q1) {
        if (q1 == null) return "<null q1>";
        String talker = monitorContext.trimValue(monitorContext.readStringField(q1, "b"));
        String content = monitorContext.trimValue(monitorContext.readStringField(q1, "d"));
        String type = monitorContext.trimValue(monitorContext.readStringField(q1, "e"));
        String flag = monitorContext.trimValue(monitorContext.readStringField(q1, "f"));
        return "state: talker=" + talker + " type=" + type + " flag=" + flag + " content=" + content;
    }

    private void emitQ1SendRequest(MonitorContext monitorContext, String source, Object q1) {
        if (!monitorContext.isMonitorEnabled() || q1 == null) return;

        String talker = monitorContext.readStringField(q1, "b");
        String content = monitorContext.readStringField(q1, "d");
        if (talker.isEmpty() && content.isEmpty()) return;

        String displayName = monitorContext.getTalkerDisplayName(talker);
        String namePart = displayName.isEmpty()
            ? "talker=" + monitorContext.trimValue(talker)
            : "displayName=" + monitorContext.trimValue(displayName) + " | talker=" + monitorContext.trimValue(talker);
        String details = source + " | " + namePart + " | content=" + monitorContext.trimValue(content);
        String fingerprint = "发送请求|" + talker + "|" + content;
        if (monitorContext.shouldEmit(fingerprint)) {
            monitorContext.logMessage("发送请求", details);
        }
    }
}
