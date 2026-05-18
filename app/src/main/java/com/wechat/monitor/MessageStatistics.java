package com.wechat.monitor;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 消息统计管理器
 * 统计各类消息的数量、频率等数据
 */
public class MessageStatistics {

    private static final String TAG = "MessageStatistics";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private Context context;
    private MessageLogger messageLogger;

    // 统计数据
    private Map<String, Integer> messageTypeCount = new HashMap<>();
    private Map<String, Long> messageTimeMap = new HashMap<>();
    private int totalMessages = 0;
    private int todayMessages = 0;
    private long startTime = System.currentTimeMillis();

    public MessageStatistics(Context context, MessageLogger messageLogger) {
        this.context = context;
        this.messageLogger = messageLogger;
        initStatistics();
    }

    /**
     * 初始化统计数据
     */
    private void initStatistics() {
        messageTypeCount.put("文本消息", 0);
        messageTypeCount.put("图片消息", 0);
        messageTypeCount.put("语音消息", 0);
        messageTypeCount.put("视频消息", 0);
        messageTypeCount.put("支付消息", 0);
        messageTypeCount.put("红包消息", 0);
        messageTypeCount.put("转账消息", 0);
        messageTypeCount.put("其他消息", 0);
    }

    /**
     * 记录消息统计
     */
    public synchronized void recordMessage(String messageType) {
        if (TextUtils.isEmpty(messageType)) {
            messageType = "其他消息";
        }

        // 更新类型计数
        int count = messageTypeCount.getOrDefault(messageType, 0);
        messageTypeCount.put(messageType, count + 1);

        // 更新总计数
        totalMessages++;
        todayMessages++;

        // 记录时间
        messageTimeMap.put(messageType, System.currentTimeMillis());

        // 定期保存统计
        if (totalMessages % 10 == 0) {
            saveStatistics();
        }

        Log.d(TAG, "消息统计更新: " + messageType + " = " + (count + 1));
    }

    /**
     * 获取统计数据
     */
    public Map<String, Integer> getStatistics() {
        return new HashMap<>(messageTypeCount);
    }

    /**
     * 获取统计报告
     */
    public String getStatisticsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== 消息统计报告 ========== \n");
        sb.append("统计时间: ").append(DATE_FORMAT.format(new Date(startTime))).append("\n");
        sb.append("当前时间: ").append(DATE_FORMAT.format(new Date())).append("\n");
        sb.append("运行时长: ").append(getRunningTime()).append("\n\n");

        sb.append("消息总数: ").append(totalMessages).append("\n");
        sb.append("今日消息: ").append(todayMessages).append("\n\n");

        sb.append("各类型统计:\n");
        for (Map.Entry<String, Integer> entry : messageTypeCount.entrySet()) {
            sb.append(String.format("  %s: %d条\n", entry.getKey(), entry.getValue()));
        }

        sb.append("\n平均频率:\n");
        long runningSeconds = (System.currentTimeMillis() - startTime) / 1000;
        if (runningSeconds > 0) {
            float avgRate = (float) totalMessages / runningSeconds * 60;
            sb.append(String.format("  每分钟: %.2f条\n", avgRate));
            sb.append(String.format("  每小时: %.2f条\n", avgRate * 60));
        }

        sb.append("=================================\n");

        return sb.toString();
    }

    /**
     * 获取运行时间
     */
    private String getRunningTime() {
        long seconds = (System.currentTimeMillis() - startTime) / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * 保存统计数据
     */
    private void saveStatistics() {
        try {
            String report = getStatisticsReport();
            if (messageLogger != null) {
                messageLogger.logMessage("统计报告", report);
            }
            Log.d(TAG, "统计数据已保存");
        } catch (Exception e) {
            Log.e(TAG, "保存统计数据失败", e);
        }
    }

    /**
     * 清空统计
     */
    public synchronized void clearStatistics() {
        initStatistics();
        totalMessages = 0;
        todayMessages = 0;
        startTime = System.currentTimeMillis();
        messageTimeMap.clear();
        Log.d(TAG, "统计数据已清空");
    }

    /**
     * 判断消息类型
     */
    public static String getMessageType(String content) {
        if (TextUtils.isEmpty(content)) {
            return "其他消息";
        }

        // 根据内容特征判断消息类型
        if (content.contains("图片") || content.contains("img") || content.contains("image")) {
            return "图片消息";
        } else if (content.contains("语音") || content.contains("voice") || content.contains("audio")) {
            return "语音消息";
        } else if (content.contains("视频") || content.contains("video")) {
            return "视频消息";
        } else if (content.contains("红包") || content.contains("red packet")) {
            return "红包消息";
        } else if (content.contains("转账") || content.contains("transfer")) {
            return "转账消息";
        } else if (content.contains("支付") || content.contains("pay")) {
            return "支付消息";
        } else if (content.contains("文件") || content.contains("file")) {
            return "文件消息";
        } else if (content.contains("位置") || content.contains("location")) {
            return "位置消息";
        } else if (content.contains("名片") || content.contains("card")) {
            return "名片消息";
        } else {
            return "文本消息";
        }
    }
}