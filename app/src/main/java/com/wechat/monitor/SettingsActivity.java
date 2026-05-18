package com.wechat.monitor;

import android.content.Intent;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 微信聊天监听模块设置界面
 */
public class SettingsActivity extends Activity {

    private Switch switchMonitor;
    private Switch switchLog;
    private TextView tvLocation;
    private TextView tvStatus;
    private TextView tvPreview;
    private Button btnExport;
    private Button btnClear;
    private EditText etSendTalker;
    private EditText etSendContent;
    private Button btnSend;

    private ConfigManager configManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable previewRefreshRunnable = this::refreshPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        configManager = new ConfigManager(this);

        initViews();
        loadData();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPreview();
        handler.removeCallbacks(previewRefreshRunnable);
        handler.postDelayed(previewRefreshRunnable, 500);
        handler.postDelayed(previewRefreshRunnable, 1500);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(previewRefreshRunnable);
        super.onPause();
    }

    private void initViews() {
        switchMonitor = findViewById(R.id.switch_monitor);
        switchLog = findViewById(R.id.switch_log);
        tvLocation = findViewById(R.id.tv_location);
        tvStatus = findViewById(R.id.tv_status);
        tvPreview = findViewById(R.id.tv_preview);
        btnExport = findViewById(R.id.btn_export);
        btnClear = findViewById(R.id.btn_clear);
        etSendTalker = findViewById(R.id.et_send_talker);
        etSendContent = findViewById(R.id.et_send_content);
        btnSend = findViewById(R.id.btn_send);
    }

    private void loadData() {
        switchMonitor.setChecked(configManager.isMonitorEnabled());
        switchLog.setChecked(configManager.isLogEnabled());
        tvLocation.setText(buildLogLocationSummary());
        ensurePreviewStorageAccess();
        refreshPreview();

        updateStatus();
    }

    private void ensurePreviewStorageAccess() {
        if (Environment.isExternalStorageManager()) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "请授予文件访问权限后重新打开设置页", Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
                Toast.makeText(this, "请授予文件访问权限后重新打开设置页", Toast.LENGTH_LONG).show();
            } catch (Exception ignoredAgain) {
            }
        }
    }

    private void setupListeners() {
        switchMonitor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setMonitorEnabled(isChecked);
            updateStatus();
            Toast.makeText(this, isChecked ? "监听已启用" : "监听已停止", Toast.LENGTH_SHORT).show();
        });

        switchLog.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setLogEnabled(isChecked);
            Toast.makeText(this, isChecked ? "日志已启用" : "日志已停止", Toast.LENGTH_SHORT).show();
        });

        btnExport.setOnClickListener(v -> {
            exportLogs();
        });

        btnClear.setOnClickListener(v -> {
            clearLogs();
        });

        btnSend.setOnClickListener(v -> writeSendCommand());
    }

    private void writeSendCommand() {
        String talker = etSendTalker.getText().toString().trim();
        if (talker.isEmpty()) {
            talker = getString(R.string.send_default_talker);
        }
        String content = etSendContent.getText().toString();
        if (content.isEmpty()) {
            Toast.makeText(this, R.string.send_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent("com.wechat.monitor.ACTION_SEND_TEXT");
            intent.setPackage("com.tencent.mm");
            intent.putExtra("talker", talker);
            intent.putExtra("content", content);
            intent.putExtra("type", 1);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sendBroadcast(intent);
            Toast.makeText(this,
                getString(R.string.send_command_written, talker),
                Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this,
                getString(R.string.send_command_failed, String.valueOf(t.getMessage())),
                Toast.LENGTH_LONG).show();
        }
    }

    private void updateStatus() {
        if (switchMonitor.isChecked()) {
            tvStatus.setText(getString(R.string.status_monitoring));
        } else {
            tvStatus.setText(getString(R.string.status_stopped));
        }
    }

    private void exportLogs() {
        try {
            List<File> logFiles = getAllMonitorLogFiles();
            if (logFiles.isEmpty()) {
                Toast.makeText(this, "没有找到日志文件，请先查看微信进程日志目录", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "找到 " + logFiles.size() + " 个日志文件", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void clearLogs() {
        try {
            List<File> files = getAllMonitorLogFiles();
            if (files.isEmpty()) {
                Toast.makeText(this, "没有找到日志文件", Toast.LENGTH_SHORT).show();
                return;
            }

            int deletedCount = 0;
            for (File file : files) {
                if (file.delete()) {
                    deletedCount++;
                }
            }
            new MessageDatabase(this).clearAllData();
            refreshPreview();
            Toast.makeText(this, "已清空 " + deletedCount + " 个日志文件", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "清空失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshPreview() {
        List<String> previews = new MessageDatabase(this).getRecentMessages(8);
        if (!previews.isEmpty()) {
            tvPreview.setText(joinPreviewLines(previews));
            return;
        }

        PreviewLoadResult fileResult = readRecentMessagesFromLogs(8);
        if (!fileResult.messages.isEmpty()) {
            tvPreview.setText(joinPreviewLines(fileResult.messages));
            return;
        }

        if (!fileResult.rawTailLines.isEmpty()) {
            tvPreview.setText(joinPreviewLines(fileResult.rawTailLines));
            return;
        }

        String diagnostics = buildPreviewDiagnostics(fileResult);
        tvPreview.setText(diagnostics.isEmpty() ? getString(R.string.preview_empty) : diagnostics);
    }

    private String joinPreviewLines(List<String> previews) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < previews.size(); i++) {
            if (i > 0) {
                builder.append("\n\n");
            }
            builder.append(previews.get(i));
        }
        return builder.toString();
    }

    private String buildPreviewDiagnostics(PreviewLoadResult result) {
        if (result == null) {
            return getString(R.string.preview_loading_failed);
        }
        if (result.errorMessage != null && !result.errorMessage.isEmpty()) {
            return getString(R.string.preview_loading_failed) + "\n" + result.errorMessage;
        }
        if (result.scannedFiles == 0) {
            return getString(R.string.preview_empty)
                + "\n\n未找到日志文件"
                + "\n当前候选目录:" + "\n" + joinPaths(result.scannedDirectories);
        }
        if (result.messageLineCount == 0) {
            return getString(R.string.preview_empty)
                + "\n\n已读取 " + result.scannedFiles + " 个日志文件，但没有匹配到 [消息] 记录"
                + "\n当前候选目录:" + "\n" + joinPaths(result.scannedDirectories);
        }
        return "";
    }

    private String joinPaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return "(无)";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) {
                builder.append("\n");
            }
            builder.append(paths.get(i));
        }
        return builder.toString();
    }

    private PreviewLoadResult readRecentMessagesFromLogs(int limit) {
        PreviewLoadResult result = new PreviewLoadResult();
        try {
            List<File> logFiles = getAllMonitorLogFiles();
            result.scannedFiles = logFiles.size();
            if (logFiles.isEmpty()) {
                result.scannedDirectories = getCandidateLogDirPaths();
                return result;
            }
            for (int i = logFiles.size() - 1; i >= 0 && result.messages.size() < limit; i--) {
                appendMessagesFromFile(logFiles.get(i), result, limit);
            }
            result.scannedDirectories = getCandidateLogDirPaths();
            return result;
        } catch (Exception e) {
            result.scannedDirectories = getCandidateLogDirPaths();
            result.errorMessage = e.getMessage();
            return result;
        }
    }

    private void appendMessagesFromFile(File file, PreviewLoadResult result, int limit) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            List<String> rawLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("[com.tencent.mm]")) {
                    rawLines.add(line.trim());
                    if (rawLines.size() > limit) {
                        rawLines.remove(0);
                    }
                }
                if (line.contains("[消息]") && line.contains("talker=") && line.contains("content=")) {
                    result.messageLineCount++;
                    String displayName = extractPreviewField(line, "displayName=");
                    String talker = extractPreviewField(line, "talker=");
                    String messageType = extractPreviewField(line, "type=");
                    String msgId = extractPreviewField(line, "msgId=");
                    String content = extractPreviewField(line, "content=");
                    if (!talker.isEmpty() && !content.isEmpty()) {
                        String namePart = displayName.isEmpty() ? talker : displayName + " (" + talker + ")";
                        lines.add("[消息] " + namePart + " | type=" + messageType + " | msgId=" + msgId + "\n" + content);
                    } else {
                        lines.add(formatPreviewLine(line));
                    }
                }
            }
            for (int i = lines.size() - 1; i >= 0 && result.messages.size() < limit; i--) {
                result.messages.add(lines.get(i));
            }
            for (int i = rawLines.size() - 1; i >= 0 && result.rawTailLines.size() < limit; i--) {
                result.rawTailLines.add(0, rawLines.get(i));
            }
        } catch (Exception ignored) {
        }
    }

    private String formatPreviewLine(String line) {
        int processIndex = line.indexOf("[com.tencent.mm] [消息] ");
        String text = processIndex >= 0 ? line.substring(processIndex + "[com.tencent.mm] [消息] ".length()) : line;
        return text.trim();
    }

    private String extractPreviewField(String line, String key) {
        int start = line.indexOf(key);
        if (start < 0) {
            return "";
        }
        int valueStart = start + key.length();
        int end = line.indexOf(" | ", valueStart);
        if (end < 0) {
            end = line.length();
        }
        return line.substring(valueStart, end).trim();
    }

    private String buildLogLocationSummary() {
        return "公共目录(推荐):\n"
            + configManager.getPublicLogLocation()
            + "\n\n当前写入目录:\n"
            + configManager.getSaveLocation();
    }

    private List<File> getAllMonitorLogFiles() {
        List<File> result = new ArrayList<>();
        for (File logDir : getCandidateLogDirs()) {
            File[] files = getMonitorLogFiles(logDir);
            Collections.addAll(result, files);
        }
        result.sort(Comparator.comparing(File::getName));
        return result;
    }

    private List<String> getCandidateLogDirPaths() {
        List<String> paths = new ArrayList<>();
        for (File dir : getCandidateLogDirs()) {
            paths.add(dir.getAbsolutePath());
        }
        return paths;
    }

    private List<File> getCandidateLogDirs() {
        Set<String> directories = new LinkedHashSet<>();
        directories.add(configManager.getPublicLogLocation());
        directories.add(configManager.getSaveLocation());

        File externalRoot = Environment.getExternalStorageDirectory();
        if (externalRoot != null) {
            directories.add(new File(externalRoot, "Documents/WeChatMonitor/logs").getAbsolutePath());
            directories.add(new File(externalRoot, "WeChatMonitor/logs").getAbsolutePath());
        }

        List<File> result = new ArrayList<>();
        for (String path : directories) {
            if (path != null && !path.trim().isEmpty()) {
                result.add(new File(path));
            }
        }
        return result;
    }

    private File[] getMonitorLogFiles(File logDir) {
        if (!logDir.exists() || !logDir.isDirectory()) {
            return new File[0];
        }
        File[] files = logDir.listFiles((dir, name) -> name.startsWith("wechat_monitor_") && name.endsWith(".txt"));
        return files != null ? files : new File[0];
    }

    private static final class PreviewLoadResult {
        private final List<String> messages = new ArrayList<>();
        private final List<String> rawTailLines = new ArrayList<>();
        private List<String> scannedDirectories = new ArrayList<>();
        private String errorMessage = "";
        private int scannedFiles;
        private int messageLineCount;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        configManager.save();
    }
}
