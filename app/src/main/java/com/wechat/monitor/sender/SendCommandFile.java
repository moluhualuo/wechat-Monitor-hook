package com.wechat.monitor.sender;

import android.os.Environment;

import com.wechat.monitor.core.MonitorContext;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 主动发送命令文件触发器。
 *
 * 命令文件路径：/sdcard/WeChatMonitor/send.json （UTF-8，可由 adb push 或 SettingsActivity 写入）
 * 文件格式：
 * {
 *   "talker": "wxid_xxx",
 *   "content": "hello",
 *   "type": 1
 * }
 *
 * 流程：
 *   pollAndSend() -> 防抖检查 -> 读文件 -> 解析 JSON -> ActiveSender.sendText -> rename(.done/.failed.时间戳)
 *
 * 触发来源：
 *   - CommandPoller (Hook 进程内 1 秒轮询线程，立即响应 SettingsActivity 按钮)
 *   - SendTriggerHook (LauncherUI.onResume 兜底)
 *
 * 防抖：200ms 内的重复触发被忽略，避免高频轮询/FileObserver 多次回调造成重复 IO。
 * 真正"同一命令不会发送两次"由 rename 机制保证 —— 处理完文件就改名。
 */
public final class SendCommandFile {

    private static final String COMMAND_DIR_NAME = "WeChatMonitor";
    private static final String COMMAND_FILE_NAME = "send.json";
    /**
     * 防抖窗口：CommandPoller 高频轮询 / FileObserver 多次回调时，避免对同一次写入做重复 IO。
     * 真正"同一命令不要被发两次"由 rename(.done/.failed) 机制保证 —— 处理完文件就消失。
     */
    private static final long THROTTLE_MS = 200L;

    private static volatile long lastCheckAt = 0L;
    private static final Object LOCK = new Object();

    private SendCommandFile() {
    }

    public static SendResult pollAndSend(MonitorContext ctx) {
        if (ctx == null) {
            return SendResult.fail("MonitorContext 为空");
        }

        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (now - lastCheckAt < THROTTLE_MS) {
                return SendResult.fail("节流: " + (now - lastCheckAt) + "ms < " + THROTTLE_MS);
            }
            lastCheckAt = now;
        }

        File commandFile = locateCommandFile();
        if (commandFile == null || !commandFile.exists() || !commandFile.isFile()) {
            return SendResult.fail("命令文件不存在: " + (commandFile == null ? "<null>" : commandFile.getAbsolutePath()));
        }

        String json;
        try {
            json = readFileUtf8(commandFile);
        } catch (Throwable t) {
            ctx.log("SendCommandFile 读取失败: " + t.getMessage());
            return SendResult.fail("读取失败", t);
        }
        if (json.isEmpty()) {
            return SendResult.fail("命令文件为空");
        }

        String talker;
        String content;
        int type;
        try {
            JSONObject obj = new JSONObject(json);
            talker = obj.optString("talker", "");
            content = obj.optString("content", "");
            type = obj.optInt("type", 1);
        } catch (Throwable t) {
            ctx.log("SendCommandFile JSON 解析失败: " + t.getMessage());
            renameToFailed(commandFile, "parse");
            return SendResult.fail("JSON 解析失败", t);
        }

        ctx.logMessage("主动发送", "SendCommandFile 读取命令"
            + " | path=" + commandFile.getAbsolutePath()
            + " | talker=" + ctx.trimValue(talker)
            + " | content=" + ctx.trimValue(content)
            + " | type=" + type);

        SendResult result = ActiveSender.sendText(ctx, talker, content, type);

        String suffix = result.isOk() ? "done" : "failed";
        renameToFailed(commandFile, suffix);
        ctx.logMessage("主动发送", "SendCommandFile 完成"
            + " | result=" + (result.isOk() ? "ok" : "fail")
            + " | message=" + result.getMessage());
        return result;
    }

    private static File locateCommandFile() {
        try {
            File externalRoot = Environment.getExternalStorageDirectory();
            if (externalRoot == null) {
                return null;
            }
            return new File(new File(externalRoot, COMMAND_DIR_NAME), COMMAND_FILE_NAME);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readFileUtf8(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] block = new byte[4096];
            int n;
            while ((n = in.read(block)) > 0) {
                buffer.write(block, 0, n);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }

    private static void renameToFailed(File commandFile, String suffix) {
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
            File target = new File(commandFile.getParentFile(),
                COMMAND_FILE_NAME + "." + suffix + "." + ts);
            if (!commandFile.renameTo(target)) {
                // rename 失败兜底：直接删除，避免重复发送
                commandFile.delete();
            }
        } catch (Throwable ignored) {
        }
    }
}
