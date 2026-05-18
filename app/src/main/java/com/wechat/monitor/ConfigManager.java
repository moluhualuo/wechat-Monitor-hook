package com.wechat.monitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * 配置管理类
 * 使用标准 SharedPreferences，不依赖 Xposed API
 */
public class ConfigManager {

    private static final String TAG = "ConfigManager";
    private static final String PACKAGE_NAME = "com.wechat.monitor";
    private static final String PREF_NAME = "wechat_monitor_config";
    private static final String KEY_MONITOR_ENABLED = "monitor_enabled";
    private static final String KEY_LOG_ENABLED = "log_enabled";
    private static final String KEY_SAVE_LOCATION = "save_location";
    private static final String KEY_WCDB_TRACE_ENABLED = "wcdb_trace_enabled";
    private static final String PUBLIC_LOG_DIR = "Documents/WeChatMonitor/logs";
    private static final String MODULE_LOG_DIR = "Android/data/" + PACKAGE_NAME + "/files/logs";

    private final Context context;
    private SharedPreferences prefs;

    public ConfigManager(Context context) {
        this.context = context;

        // 使用标准 SharedPreferences
        try {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get SharedPreferences: " + e.getMessage());
        }
    }

    public boolean isMonitorEnabled() {
        return getBoolean(KEY_MONITOR_ENABLED, true);
    }

    public void setMonitorEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_MONITOR_ENABLED, enabled).apply();
        }
    }

    public boolean isLogEnabled() {
        return getBoolean(KEY_LOG_ENABLED, true);
    }

    public void setLogEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_LOG_ENABLED, enabled).apply();
        }
    }

    /**
     * WCDB SQLiteDatabase message 表写入跟踪开关。
     * 默认关闭，仅在排查发送链路是否真正落库时打开，因为打开后会高频拦截 SQLite 写入。
     */
    public boolean isWcdbTraceEnabled() {
        return getBoolean(KEY_WCDB_TRACE_ENABLED, false);
    }

    public void setWcdbTraceEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_WCDB_TRACE_ENABLED, enabled).apply();
        }
    }

    public String getSaveLocation() {
        String loc = getString(KEY_SAVE_LOCATION, null);
        if (loc != null) {
            String migrated = migrateLegacySaveLocation(loc);
            if (!migrated.equals(loc)) {
                setSaveLocation(migrated);
            }
            return migrated;
        }
        return getDefaultSaveLocation();
    }

    public void setSaveLocation(String location) {
        if (prefs != null) {
            prefs.edit().putString(KEY_SAVE_LOCATION, location).apply();
        }
    }

    private boolean getBoolean(String key, boolean defValue) {
        try {
            return prefs != null ? prefs.getBoolean(key, defValue) : defValue;
        } catch (Exception e) {
            return defValue;
        }
    }

    private String getString(String key, String defValue) {
        try {
            return prefs != null ? prefs.getString(key, defValue) : defValue;
        } catch (Exception e) {
            return defValue;
        }
    }

    private String getDefaultSaveLocation() {
        File externalStorageDir = Environment.getExternalStorageDirectory();
        boolean isHostProcess = !PACKAGE_NAME.equals(context.getPackageName());
        if (externalStorageDir != null) {
            File publicLogDir = new File(externalStorageDir, PUBLIC_LOG_DIR);
            if (ensureDirectory(publicLogDir)) {
                return publicLogDir.getAbsolutePath();
            }

            if (!isHostProcess) {
                File moduleLogDir = new File(externalStorageDir, MODULE_LOG_DIR);
                if (ensureDirectory(moduleLogDir)) {
                    return moduleLogDir.getAbsolutePath();
                }
            }
        }

        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null) {
            File processLogDir = new File(externalFilesDir, "logs");
            if (ensureDirectory(processLogDir)) {
                return processLogDir.getAbsolutePath();
            }
        }
        return "/sdcard/" + PUBLIC_LOG_DIR;
    }

    public void save() {}

    public String getPublicLogLocation() {
        File externalStorageDir = Environment.getExternalStorageDirectory();
        if (externalStorageDir != null) {
            return new File(externalStorageDir, PUBLIC_LOG_DIR).getAbsolutePath();
        }
        return "/sdcard/" + PUBLIC_LOG_DIR;
    }

    private boolean ensureDirectory(File directory) {
        return directory != null && (directory.exists() || directory.mkdirs());
    }

    private String migrateLegacySaveLocation(String location) {
        if (location == null) {
            return getDefaultSaveLocation();
        }
        String normalized = location.replace('\\', '/');
        boolean isHostProcess = !PACKAGE_NAME.equals(context.getPackageName());
        boolean isModuleDir = normalized.endsWith(MODULE_LOG_DIR.replace('\\', '/'));
        if (isHostProcess && isModuleDir) {
            File externalStorageDir = Environment.getExternalStorageDirectory();
            if (externalStorageDir != null) {
                File publicLogDir = new File(externalStorageDir, PUBLIC_LOG_DIR);
                if (ensureDirectory(publicLogDir)) {
                    return publicLogDir.getAbsolutePath();
                }
            }
            return "/sdcard/" + PUBLIC_LOG_DIR;
        }
        return location;
    }
}
