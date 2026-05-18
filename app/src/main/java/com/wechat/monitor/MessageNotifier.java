package com.wechat.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.wechat.monitor.SettingsActivity;

/**
 * 消息通知提醒
 * 当收到重要消息时发送系统通知
 */
public class MessageNotifier {

    private static final String TAG = "MessageNotifier";
    private static final String CHANNEL_ID = "wechat_monitor_channel";
    private static final String CHANNEL_NAME = "微信消息监听";
    private static final int NOTIFICATION_ID = 1000;

    private Context context;
    private NotificationManager notificationManager;
    private ConfigManager configManager;

    // 通知计数
    private int notificationCount = 0;

    public MessageNotifier(Context context) {
        this.context = context;
        this.configManager = new ConfigManager(context);
        initNotificationChannel();
    }

    /**
     * 初始化通知渠道
     */
    private void initNotificationChannel() {
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("微信消息监听通知");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * 发送消息通知
     */
    public void sendMessageNotification(String messageType, String content) {
        if (!configManager.isMonitorEnabled()) {
            return;
        }

        // 过滤重要消息
        if (!isImportantMessage(messageType)) {
            return;
        }

        try {
            // 创建通知
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), android.R.drawable.ic_dialog_email))
                .setContentTitle("微信消息监听 - " + messageType)
                .setContentText(content)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setNumber(++notificationCount);

            // 设置点击意图
            Intent intent = new Intent(context, SettingsActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.setContentIntent(pendingIntent);

            // 发送通知
            notificationManager.notify(NOTIFICATION_ID + notificationCount, builder.build());

            Log.d(TAG, "发送通知: " + messageType + " - " + content);

        } catch (Exception e) {
            Log.e(TAG, "发送通知失败", e);
        }
    }

    /**
     * 发送统计通知
     */
    public void sendStatisticsNotification(String statistics) {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_report_image)
                .setContentTitle("微信消息统计")
                .setContentText("点击查看详细统计")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(statistics))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

            Intent intent = new Intent(context, SettingsActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.setContentIntent(pendingIntent);

            notificationManager.notify(NOTIFICATION_ID, builder.build());

        } catch (Exception e) {
            Log.e(TAG, "发送统计通知失败", e);
        }
    }

    /**
     * 判断是否是重要消息
     */
    private boolean isImportantMessage(String messageType) {
        // 支付、红包、转账等消息认为是重要消息
        return messageType.contains("支付") ||
               messageType.contains("红包") ||
               messageType.contains("转账") ||
               messageType.contains("钱包");
    }

    /**
     * 清除所有通知
     */
    public void clearAllNotifications() {
        try {
            notificationManager.cancelAll();
            notificationCount = 0;
            Log.d(TAG, "已清除所有通知");
        } catch (Exception e) {
            Log.e(TAG, "清除通知失败", e);
        }
    }

    /**
     * 获取通知数量
     */
    public int getNotificationCount() {
        return notificationCount;
    }
}