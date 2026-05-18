package com.wechat.monitor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MessageDatabase extends SQLiteOpenHelper {

    private static final String TAG = "MessageDatabase";
    private static final String DATABASE_NAME = "wechat_monitor.db";
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_MESSAGES = "messages";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_LABEL = "label";
    public static final String COLUMN_TALKER = "talker";
    public static final String COLUMN_DISPLAY_NAME = "display_name";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_MESSAGE_TYPE = "message_type";
    public static final String COLUMN_MSG_ID = "msg_id";
    public static final String COLUMN_CREATED_AT = "created_at";

    public MessageDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createMessagesTable = "CREATE TABLE " + TABLE_MESSAGES + " ("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_LABEL + " TEXT NOT NULL, "
            + COLUMN_TALKER + " TEXT, "
            + COLUMN_DISPLAY_NAME + " TEXT, "
            + COLUMN_CONTENT + " TEXT, "
            + COLUMN_MESSAGE_TYPE + " TEXT, "
            + COLUMN_MSG_ID + " TEXT, "
            + COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)"
            + ")";
        db.execSQL(createMessagesTable);
        Log.d(TAG, "数据库表创建成功");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        onCreate(db);
        Log.d(TAG, "数据库升级成功");
    }

    public long insertMessage(String label, String talker, String displayName, String content, String messageType, String msgId) {
        if (content == null || content.trim().isEmpty()) {
            return -1;
        }
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_LABEL, label);
            values.put(COLUMN_TALKER, talker);
            values.put(COLUMN_DISPLAY_NAME, displayName);
            values.put(COLUMN_CONTENT, content);
            values.put(COLUMN_MESSAGE_TYPE, messageType);
            values.put(COLUMN_MSG_ID, msgId);
            return db.insert(TABLE_MESSAGES, null, values);
        } catch (Exception e) {
            Log.e(TAG, "插入消息失败", e);
            return -1;
        } finally {
            db.close();
        }
    }

    public List<String> getRecentMessages(int limit) {
        List<String> messages = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                TABLE_MESSAGES,
                new String[]{COLUMN_LABEL, COLUMN_TALKER, COLUMN_DISPLAY_NAME, COLUMN_CONTENT, COLUMN_MESSAGE_TYPE, COLUMN_MSG_ID},
                COLUMN_TALKER + " <> '' AND " + COLUMN_CONTENT + " <> ''",
                null,
                null,
                null,
                COLUMN_ID + " DESC",
                String.valueOf(limit)
            );
            while (cursor.moveToNext()) {
                String label = cursor.getString(0);
                String talker = cursor.getString(1);
                String displayName = cursor.getString(2);
                String content = cursor.getString(3);
                String messageType = cursor.getString(4);
                String msgId = cursor.getString(5);
                String namePart = safe(displayName).isEmpty() ? safe(talker) : safe(displayName) + " (" + safe(talker) + ")";
                String line = "[" + safe(label) + "] "
                    + namePart
                    + " | type=" + safe(messageType)
                    + " | msgId=" + safe(msgId)
                    + "\n" + safe(content);
                messages.add(line);
            }
        } catch (Exception e) {
            Log.e(TAG, "读取预览消息失败", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
        return messages;
    }

    public void clearAllData() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.delete(TABLE_MESSAGES, null, null);
        } catch (Exception e) {
            Log.e(TAG, "清空数据失败", e);
        } finally {
            db.close();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
