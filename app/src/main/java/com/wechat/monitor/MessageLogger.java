package com.wechat.monitor;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

/**
 * 消息日志记录器
 */
public class MessageLogger {

    private static final String TAG = "MessageLogger";
    private static final String LOG_DIR = "logs";
    private static final String PUBLIC_ROOT_DIR = "Documents/WeChatMonitor";
    private static final String LOG_FILE_PREFIX = "wechat_monitor_";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

    private final Context context;
    private File logDir;
    private BufferedWriter currentWriter;
    private String currentDate;

    public MessageLogger(Context context) {
        this.context = context;
        initLogDir();
    }

    /**
     * 初始化日志目录
     */
    private void initLogDir() {
        try {
            String configuredLocation = new ConfigManager(context).getSaveLocation();
            if (!TextUtils.isEmpty(configuredLocation)) {
                File configuredDir = new File(configuredLocation);
                if (ensureDirectory(configuredDir)) {
                    logDir = configuredDir;
                }
            }

            if (logDir == null) {
                File publicLogDir = new File(Environment.getExternalStorageDirectory(), PUBLIC_ROOT_DIR + "/" + LOG_DIR);
                if (ensureDirectory(publicLogDir)) {
                    logDir = publicLogDir;
                }
            }

            if (logDir == null) {
                File externalFilesDir = context.getExternalFilesDir(null);
                if (externalFilesDir != null) {
                    File processLogDir = new File(externalFilesDir, LOG_DIR);
                    if (ensureDirectory(processLogDir)) {
                        logDir = processLogDir;
                    }
                }
            }

            if (logDir != null) {
                Log.d(TAG, "日志目录已存在: " + logDir.getAbsolutePath());
            } else {
                Log.e(TAG, "日志目录初始化失败");
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化日志目录失败", e);
        }
    }

    /**
     * 记录消息
     */
    public synchronized void logMessage(String messageType, String content) {
        if (TextUtils.isEmpty(messageType) || TextUtils.isEmpty(content)) {
            return;
        }

        try {
            checkLogFile();

            String timestamp = DATE_FORMAT.format(new Date());
            String logEntry = String.format("[%s] [%s] %s%n", timestamp, messageType, content);

            if (currentWriter != null) {
                currentWriter.write(logEntry);
                currentWriter.flush();
            }

            Log.d(TAG, "消息已记录: " + logEntry.trim());

        } catch (Exception e) {
            Log.e(TAG, "记录消息失败", e);
        }
    }

    /**
     * 检查并创建日志文件
     */
    private void checkLogFile() throws IOException {
        if (logDir == null && !recoverLogDir()) {
            throw new IOException("日志目录不可用");
        }

        String today = FILE_DATE_FORMAT.format(new Date());

        // 如果是新的一天，创建新的日志文件
        if (currentWriter == null || !today.equals(currentDate)) {
            closeLogFile();
            currentDate = today;
            File logFile = new File(logDir, LOG_FILE_PREFIX + currentDate + ".txt");
            currentWriter = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8)
            );
            Log.d(TAG, "创建新日志文件: " + logFile.getAbsolutePath());
        }
    }

    /**
     * 关闭日志文件
     */
    private void closeLogFile() {
        try {
            if (currentWriter != null) {
                currentWriter.close();
                currentWriter = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭日志文件失败", e);
        }
    }

    /**
     * 获取日志文件列表
     */
    public File[] getLogFiles() {
        if (logDir != null && logDir.exists()) {
            return logDir.listFiles((dir, name) -> name.startsWith(LOG_FILE_PREFIX) && name.endsWith(".txt"));
        }
        return new File[0];
    }

    /**
     * 清空所有日志
     */
    public void clearAllLogs() {
        try {
            closeLogFile();

            if (logDir != null && logDir.exists()) {
                File[] files = logDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().startsWith(LOG_FILE_PREFIX)) {
                            boolean deleted = file.delete();
                            Log.d(TAG, "删除日志文件: " + file.getName() + " - " + (deleted ? "成功" : "失败"));
                        }
                    }
                }
            }

            Log.d(TAG, "所有日志已清空");

        } catch (Exception e) {
            Log.e(TAG, "清空日志失败", e);
        }
    }

    /**
     * 导出日志到指定文件
     */
    public boolean exportLogs(File targetFile) {
        try {
            closeLogFile();

            if (logDir == null || !logDir.exists()) {
                Log.e(TAG, "日志目录不存在");
                return false;
            }

            File[] logFiles = getLogFiles();
            if (logFiles.length == 0) {
                Log.e(TAG, "没有找到日志文件");
                return false;
            }

            BufferedWriter exportWriter = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8)
            );

            // 写入导出信息头
            exportWriter.write("# 微信聊天监听日志导出 #\n");
            exportWriter.write("# 导出时间: " + DATE_FORMAT.format(new Date()) + "\n");
            exportWriter.write("# 导出文件数: " + logFiles.length + "\n\n");

            // 合并所有日志文件
            for (File logFile : logFiles) {
                exportWriter.write("# 文件: " + logFile.getName() + "\n");
                exportWriter.write("# 大小: " + logFile.length() + " bytes\n");
                exportWriter.write("# ======== 消息内容 ========\n\n");

                // 这里简化处理，实际应该逐行读取
                exportWriter.write("[内容从文件: " + logFile.getName() + "]\n\n");
            }

            exportWriter.close();
            Log.d(TAG, "日志导出成功: " + targetFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "导出日志失败", e);
            return false;
        }
    }

    /**
     * 获取日志目录路径
     */
    public String getLogDirectory() {
        return logDir != null ? logDir.getAbsolutePath() : "";
    }

    private boolean recoverLogDir() {
        initLogDir();
        return logDir != null && ensureDirectory(logDir);
    }

    private boolean ensureDirectory(File directory) {
        return directory != null && (directory.exists() || directory.mkdirs());
    }
}
