package com.wechat.monitor.hooks;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.wechat.monitor.core.HookInstaller;
import com.wechat.monitor.core.MonitorContext;
import com.wechat.monitor.model.HookTargets;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

public final class UiEventHook implements HookInstaller {
    @Override
    public void install(XposedInterface xposed, MonitorContext monitorContext) {
        hookActivityResume(xposed, monitorContext);
        hookLuckyMoneyUi(xposed, monitorContext);
        hookTransferUi(xposed, monitorContext);
        monitorContext.log("Hooked UI events");
    }

    private void hookActivityResume(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            Method onResumeMethod = Activity.class.getDeclaredMethod("onResume");
            intercept(xposed, onResumeMethod, chain -> {
                Object result = chain.proceed();
                Object thisObj = chain.getThisObject();
                if (thisObj != null) {
                    String name = thisObj.getClass().getName();
                    if (name.startsWith("com.tencent.mm.")) {
                        monitorContext.logMessage("界面", "onResume " + name);
                        rememberChatTitle(monitorContext, thisObj);
                    }
                }
                return result;
            });
            monitorContext.log("Hooked Activity.onResume");
        } catch (Throwable t) {
            monitorContext.log("Failed to hook Activity.onResume: " + t.getMessage());
        }
    }

    private void rememberChatTitle(MonitorContext monitorContext, Object activityObj) {
        String talker = monitorContext.firstNonEmptyField(activityObj,
            "talker", "field_username", "username", "userName", "conversationTalker", "talkerUserName");
        if (talker.isEmpty()) {
            talker = monitorContext.firstNonEmptyMethod(activityObj,
                "getTalkerUserName", "getTalker", "getUsername", "getUserName", "getConversationTalker");
        }

        String fieldTitle = monitorContext.firstNonEmptyField(activityObj,
            "nickname", "title", "field_nickname", "field_title", "chatroomName", "conversationTitle", "mNickName");
        if (fieldTitle.isEmpty()) {
            fieldTitle = monitorContext.firstNonEmptyMethod(activityObj,
                "getNickname", "getTitle", "getChatroomName", "getConversationTitle", "getDisplayName");
        }

        String title = "";
        if (activityObj instanceof Activity) {
            Activity activity = (Activity) activityObj;
            int titleId = activity.getResources().getIdentifier("title_tv", "id", activity.getPackageName());
            if (titleId != 0) {
                try {
                    TextView titleView = activity.findViewById(titleId);
                    if (titleView != null) {
                        title = String.valueOf(titleView.getText());
                    }
                } catch (Throwable ignored) {
                }
            }
            if (title.isEmpty()) {
                CharSequence activityTitle = activity.getTitle();
                if (activityTitle != null) {
                    title = String.valueOf(activityTitle);
                }
            }
            if (title.isEmpty()) {
                title = fieldTitle;
            }

            String fieldDump = monitorContext.dumpFieldValues(activityObj,
                "talker", "field_username", "username", "userName", "conversationTalker", "talkerUserName",
                "nickname", "title", "field_nickname", "field_title", "chatroomName", "conversationTitle", "mNickName");
            String methodDump = monitorContext.dumpMethodValues(activityObj,
                "getTalkerUserName", "getTalker", "getUsername", "getUserName", "getConversationTalker",
                "getNickname", "getTitle", "getChatroomName", "getConversationTitle", "getDisplayName");
            if (nameLooksUseful(fieldTitle) || nameLooksUseful(title) || !fieldDump.isEmpty() || !methodDump.isEmpty()) {
                monitorContext.log("Chat probe activity=" + activity.getClass().getName()
                    + " talker=" + monitorContext.trimValue(talker)
                    + " activityTitle=" + monitorContext.trimValue(title)
                    + " fieldTitle=" + monitorContext.trimValue(fieldTitle)
                    + " fields=[" + fieldDump + "]"
                    + " methods=[" + methodDump + "]"
                    + " textViews=" + monitorContext.snapshotTextViews(activity.getWindow().getDecorView(), 12));
            }
        }
        if (!talker.isEmpty() && !title.isEmpty() && nameLooksUseful(title)) {
            monitorContext.log("Chat title resolved talker=" + monitorContext.trimValue(talker)
                + " title=" + monitorContext.trimValue(title));
            monitorContext.rememberCurrentChat(talker, title);
        }
    }

    private boolean nameLooksUseful(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return false;
        }
        if (text.startsWith("com.tencent.mm.")) {
            return false;
        }
        return !text.startsWith("wxid_");
    }

    private void hookLuckyMoneyUi(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            Class<?> luckyMoneyClass = monitorContext.getClassLoader().loadClass(HookTargets.LUCKY_MONEY_UI);
            Method onCreateMethod = luckyMoneyClass.getDeclaredMethod("onCreate", Bundle.class);
            intercept(xposed, onCreateMethod, chain -> {
                Object result = chain.proceed();
                monitorContext.logMessage("红包", "打开红包领取页");
                return result;
            });
        } catch (Throwable ignored) {
        }
    }

    private void hookTransferUi(XposedInterface xposed, MonitorContext monitorContext) {
        try {
            Class<?> transferClass = monitorContext.getClassLoader().loadClass(HookTargets.TRANSFER_UI);
            Method onCreateMethod = transferClass.getDeclaredMethod("onCreate", Bundle.class);
            intercept(xposed, onCreateMethod, chain -> {
                Object result = chain.proceed();
                monitorContext.logMessage("转账", "打开转账页面");
                return result;
            });
        } catch (Throwable ignored) {
        }
    }
}
