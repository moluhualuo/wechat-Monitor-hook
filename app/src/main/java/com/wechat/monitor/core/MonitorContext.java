package com.wechat.monitor.core;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.wechat.monitor.ConfigManager;
import com.wechat.monitor.MessageDatabase;
import com.wechat.monitor.MessageLogger;
import com.wechat.monitor.PayMessageMonitor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MonitorContext {
    public static final String TAG = "WeChatMonitor";
    public static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final int MAX_VALUE_LEN = 300;

    private final String processName;
    private ClassLoader classLoader;
    private Context androidContext;
    private MessageLogger messageLogger;
    private MessageDatabase messageDatabase;
    private ConfigManager configManager;
    private PayMessageMonitor payMessageMonitor;
    private String lastFingerprint = "";
    private long lastFingerprintAt = 0L;
    private int eventBusTraceCount = 0;
    private final Map<String, String> talkerNames = new ConcurrentHashMap<>();
    private volatile String currentChatTalker = "";
    private volatile String currentChatTitle = "";

    public MonitorContext(String processName) {
        this.processName = processName;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public String getProcessName() {
        return processName;
    }

    public void initialize(Context context) {
        if (androidContext != null) {
            return;
        }
        androidContext = context;
        messageLogger = new MessageLogger(context);
        messageDatabase = new MessageDatabase(context);
        configManager = new ConfigManager(context);
        payMessageMonitor = new PayMessageMonitor(context, messageLogger);
        log("Context initialized");
    }

    public PayMessageMonitor getPayMessageMonitor() {
        return payMessageMonitor;
    }

    public Context getAndroidContext() {
        return androidContext;
    }

    public boolean isMonitorEnabled() {
        try {
            return configManager == null || configManager.isMonitorEnabled();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public boolean isFileLogEnabled() {
        try {
            return configManager == null || configManager.isLogEnabled();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public boolean isWcdbTraceEnabled() {
        try {
            return configManager != null && configManager.isWcdbTraceEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public synchronized boolean shouldEmit(String fingerprint) {
        long now = System.currentTimeMillis();
        if (fingerprint.equals(lastFingerprint) && now - lastFingerprintAt < 1500L) {
            return false;
        }
        lastFingerprint = fingerprint;
        lastFingerprintAt = now;
        return true;
    }

    public boolean canTraceEventBus() {
        if (eventBusTraceCount >= 220) {
            return false;
        }
        eventBusTraceCount++;
        return true;
    }

    public void logMessage(String type, String content) {
        String line = "[" + processName + "] [" + type + "] " + content;
        log(line);
        try {
            if (messageLogger != null && isFileLogEnabled()) {
                messageLogger.logMessage(type, line);
            }
            if (messageDatabase != null) {
                savePreviewMessage(type, content);
            }
        } catch (Throwable t) {
            log("file log failed: " + t.getMessage());
        }
    }

    public void log(String message) {
        String line = TAG + ": " + message;
        Log.w(TAG, line);
    }

    public boolean rememberTalkerName(String talker, String displayName) {
        String normalizedTalker = trimValue(talker);
        String normalizedName = trimValue(displayName);
        if (normalizedTalker.isEmpty() || normalizedName.isEmpty()) {
            return false;
        }
        if (normalizedTalker.equals(normalizedName)) {
            return false;
        }
        String previous = talkerNames.put(normalizedTalker, normalizedName);
        return !normalizedName.equals(previous);
    }

    public String getTalkerDisplayName(String talker) {
        String normalizedTalker = trimValue(talker);
        if (normalizedTalker.isEmpty()) {
            return "";
        }
        String displayName = talkerNames.get(normalizedTalker);
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        if (normalizedTalker.equals(trimValue(currentChatTalker))) {
            return trimValue(currentChatTitle);
        }
        return "";
    }

    public void rememberCurrentChat(String talker, String title) {
        String normalizedTalker = trimValue(talker);
        String normalizedTitle = trimValue(title);
        if (normalizedTalker.isEmpty() || normalizedTitle.isEmpty()) {
            return;
        }
        currentChatTalker = normalizedTalker;
        currentChatTitle = normalizedTitle;
        rememberTalkerName(normalizedTalker, normalizedTitle);
    }

    public List<String> getRecentPreviewMessages(int limit) {
        if (messageDatabase == null) {
            return Collections.emptyList();
        }
        return messageDatabase.getRecentMessages(limit);
    }

    public void clearPreviewMessages() {
        if (messageDatabase != null) {
            messageDatabase.clearAllData();
        }
    }

    private void savePreviewMessage(String label, String content) {
        if (!"消息".equals(label)) {
            return;
        }
        String talker = extractValue(content, "talker=");
        String displayName = extractValue(content, "displayName=");
        String messageType = extractValue(content, "type=");
        String msgId = extractValue(content, "msgId=");
        String body = extractValue(content, "content=");
        if (body.isEmpty()) {
            body = trimValue(content);
        }
        messageDatabase.insertMessage(label, trimValue(talker), trimValue(displayName), trimValue(body), trimValue(messageType), trimValue(msgId));
    }

    private String extractValue(String source, String key) {
        int start = source.indexOf(key);
        if (start < 0) {
            return "";
        }
        int valueStart = start + key.length();
        int end = source.indexOf(" | ", valueStart);
        if (end < 0) {
            end = source.length();
        }
        return source.substring(valueStart, end).trim();
    }

    public String firstNonEmptyField(Object target, String... fieldNames) {
        if (fieldNames == null) {
            return "";
        }
        for (String fieldName : fieldNames) {
            String value = readStringField(target, fieldName);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    public String firstNonEmptyMethod(Object target, String... methodNames) {
        if (methodNames == null) {
            return "";
        }
        for (String methodName : methodNames) {
            String value = extractMethodValue(target, methodName);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    public Method findMethod(Class<?> targetClass, String[] methodNames, Class<?>... parameterTypes) {
        if (targetClass == null || methodNames == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Method method = targetClass.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
            try {
                Method method = targetClass.getMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public String dumpFieldValues(Object target, String... fieldNames) {
        if (target == null || fieldNames == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String fieldName : fieldNames) {
            String value = readStringField(target, fieldName);
            if (!value.isEmpty()) {
                parts.add(fieldName + "=" + trimValue(value));
            }
        }
        return String.join(", ", parts);
    }

    public String dumpMethodValues(Object target, String... methodNames) {
        if (target == null || methodNames == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String methodName : methodNames) {
            String value = extractMethodValue(target, methodName);
            if (!value.isEmpty()) {
                parts.add(methodName + "()=" + trimValue(value));
            }
        }
        return String.join(", ", parts);
    }

    public List<String> snapshotTextViews(View rootView, int limit) {
        List<String> result = new ArrayList<>();
        collectTextViews(rootView, result, limit);
        return result;
    }

    private void collectTextViews(View view, List<String> result, int limit) {
        if (view == null || result.size() >= limit) {
            return;
        }
        if (view instanceof android.widget.TextView) {
            android.widget.TextView textView = (android.widget.TextView) view;
            CharSequence text = textView.getText();
            if (text != null) {
                String normalized = trimValue(String.valueOf(text));
                if (!normalized.isEmpty()) {
                    String entry = view.getClass().getSimpleName() + "#" + view.getId() + "=" + normalized;
                    result.add(entry);
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount() && result.size() < limit; i++) {
                collectTextViews(group.getChildAt(i), result, limit);
            }
        }
    }

    public String readStringField(Object target, String fieldName) {
        if (target == null) return "";
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                return value == null ? "" : String.valueOf(value);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return "";
            }
        }
        return "";
    }

    public String extractMethodValue(Object target, String methodName) {
        if (target == null) return "";
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(target);
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "";
        }
    }

    public String trimValue(String value) {
        if (value == null) return "";
        String text = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (text.length() > MAX_VALUE_LEN) {
            return text.substring(0, MAX_VALUE_LEN) + "...";
        }
        return text;
    }

    public String classifyMessage(String details) {
        String lower = details.toLowerCase(Locale.ROOT);
        if (lower.contains("luckymoney") || lower.contains("红包")) return "红包消息";
        if (lower.contains("transfer") || lower.contains("转账")) return "转账消息";
        if (lower.contains("paymsg") || lower.contains("支付")) return "支付消息";
        return "消息";
    }
}
