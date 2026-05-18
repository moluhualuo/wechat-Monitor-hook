package com.wechat.monitor.hooks;

import android.content.ContentValues;

import com.wechat.monitor.core.HookInstaller;
import com.wechat.monitor.core.MonitorContext;
import com.wechat.monitor.model.HookTargets;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * WCDB SQLiteDatabase 写入 Hook。
 * 对应 frida/send/frida_friend_send_chain.js 末段对 SQLiteDatabase 的 hook。
 *
 * 只跟踪 table='message' 的 insert/insertWithOnConflict/replace/update，避免性能爆炸。
 * 通过 MonitorContext.isWcdbTraceEnabled() 控制，默认关闭，仅在排查发送链路是否真的落库时启用。
 */
public final class WcdbMessageHook implements HookInstaller {

    @Override
    public void install(XposedInterface xposed, MonitorContext monitorContext) {
        if (!monitorContext.isWcdbTraceEnabled()) {
            monitorContext.log("WcdbMessageHook 已禁用 (config: wcdb_trace_enabled=false)");
            return;
        }
        try {
            Class<?> sdbClass = monitorContext.getClassLoader().loadClass(HookTargets.WCDB_DATABASE);
            hookOverloads(xposed, monitorContext, sdbClass, "insert", 2);
            hookOverloads(xposed, monitorContext, sdbClass, "insertWithOnConflict", 2);
            hookOverloads(xposed, monitorContext, sdbClass, "replace", 2);
            hookOverloads(xposed, monitorContext, sdbClass, "update", 1);
            monitorContext.log("Hooked com.tencent.wcdb.database.SQLiteDatabase (message table only)");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook WCDB SQLiteDatabase: " + t.getMessage());
        }
    }

    private void hookOverloads(XposedInterface xposed, MonitorContext monitorContext,
                               Class<?> sdbClass, String methodName, int cvIndex) {
        int count = 0;
        for (Method m : sdbClass.getDeclaredMethods()) {
            if (!methodName.equals(m.getName())) {
                continue;
            }
            try {
                intercept(xposed, m, chain -> {
                    Object result = chain.proceed();
                    if (!monitorContext.isMonitorEnabled()) {
                        return result;
                    }
                    Object tableArg = chain.getArgs().isEmpty() ? null : chain.getArg(0);
                    String tableName = tableArg == null ? "" : String.valueOf(tableArg);
                    if (!HookTargets.MESSAGE_TABLE.equals(tableName)) {
                        return result;
                    }
                    Object cv = chain.getArgs().size() > cvIndex ? chain.getArg(cvIndex) : null;
                    String cvText = dumpContentValues(cv);
                    String fingerprint = "WCDB|" + methodName + "|" + tableName + "|" + cvText;
                    if (monitorContext.shouldEmit(fingerprint)) {
                        monitorContext.logMessage("WCDB写入",
                            "SQLiteDatabase." + methodName
                                + " | table=" + tableName
                                + " | cv=" + monitorContext.trimValue(cvText)
                                + " | ret=" + String.valueOf(result));
                    }
                    return result;
                });
                count++;
            } catch (Throwable t) {
                monitorContext.log("Failed to hook SQLiteDatabase." + methodName + ": " + t.getMessage());
            }
        }
        monitorContext.log("Hooked SQLiteDatabase." + methodName + " overloads=" + count);
    }

    private String dumpContentValues(Object cv) {
        if (cv == null) {
            return "{}";
        }
        if (cv instanceof ContentValues) {
            try {
                return ((ContentValues) cv).toString();
            } catch (Throwable ignored) {
            }
        }
        return String.valueOf(cv);
    }
}
