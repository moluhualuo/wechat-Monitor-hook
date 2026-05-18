package com.wechat.monitor.sender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import com.wechat.monitor.core.MonitorContext;

import java.lang.reflect.Method;

/**
 * 主动发送跨进程触发 Receiver。
 *
 * 工作机制：
 *   - 注册在微信进程内（Hook 进程），监听 ACTION_SEND_TEXT 广播
 *   - SettingsActivity（独立进程 com.wechat.monitor）通过 sendBroadcast 携带 talker/content/type 触发
 *   - adb 也可触发：adb shell am broadcast -p com.tencent.mm -a com.wechat.monitor.ACTION_SEND_TEXT --es talker filehelper --es content ping
 *
 * 为什么不再用文件：Android 11+ scoped storage 让微信进程没权限读 /sdcard/WeChatMonitor/ 下的文件。
 *
 * 安全：Action 名带模块包名前缀，且接收时检查 intent extras 完整性；
 * Android 13+ 注册时必须传 RECEIVER_EXPORTED 才能接收外部广播。
 */
public final class SendCommandReceiver extends BroadcastReceiver {

    public static final String ACTION_SEND_TEXT = "com.wechat.monitor.ACTION_SEND_TEXT";
    public static final String EXTRA_TALKER = "talker";
    public static final String EXTRA_CONTENT = "content";
    public static final String EXTRA_TYPE = "type";

    /** RECEIVER_EXPORTED 在 API 33 才在 Context 上以常量形式出现，值固定为 2。 */
    private static final int FLAG_RECEIVER_EXPORTED = 0x2;

    private final MonitorContext monitorContext;

    public SendCommandReceiver(MonitorContext monitorContext) {
        this.monitorContext = monitorContext;
    }

    public static boolean register(MonitorContext ctx) {
        Context androidContext = ctx.getAndroidContext();
        if (androidContext == null) {
            ctx.log("SendCommandReceiver 注册失败: androidContext 为空");
            return false;
        }
        IntentFilter filter = new IntentFilter(ACTION_SEND_TEXT);
        SendCommandReceiver receiver = new SendCommandReceiver(ctx);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                Method m = Context.class.getMethod("registerReceiver",
                    BroadcastReceiver.class, IntentFilter.class, int.class);
                m.invoke(androidContext, receiver, filter, FLAG_RECEIVER_EXPORTED);
            } else {
                androidContext.registerReceiver(receiver, filter);
            }
            ctx.log("SendCommandReceiver 已注册 action=" + ACTION_SEND_TEXT);
            return true;
        } catch (Throwable t) {
            ctx.log("SendCommandReceiver 注册失败: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return false;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_SEND_TEXT.equals(intent.getAction())) {
            return;
        }
        String talker = intent.getStringExtra(EXTRA_TALKER);
        String content = intent.getStringExtra(EXTRA_CONTENT);
        int type = intent.getIntExtra(EXTRA_TYPE, 1);

        monitorContext.logMessage("主动发送",
            "收到广播 action=" + ACTION_SEND_TEXT
                + " | talker=" + monitorContext.trimValue(String.valueOf(talker))
                + " | content=" + monitorContext.trimValue(String.valueOf(content))
                + " | type=" + type);

        try {
            SendResult result = ActiveSender.sendText(monitorContext, talker, content, type);
            monitorContext.logMessage("主动发送",
                "广播触发完成 | result=" + (result.isOk() ? "ok" : "fail")
                    + " | message=" + result.getMessage());
        } catch (Throwable t) {
            monitorContext.logMessage("主动发送",
                "广播触发异常 | " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }
}
