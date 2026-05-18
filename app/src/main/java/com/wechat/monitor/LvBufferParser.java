package com.wechat.monitor;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class LvBufferParser {
    private static final byte START_MARKER = 0x7b;
    private static final byte END_MARKER = 0x7d;
    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    private LvBufferParser() {
    }

    static Result parse(byte[] data) {
        Result result = new Result();
        if (data == null || data.length == 0) {
            result.error = "empty";
            return result;
        }
        if (data[0] != START_MARKER) {
            result.error = "bad-start:" + (data[0] & 0xff);
            return result;
        }
        if (data[data.length - 1] != END_MARKER) {
            result.error = "bad-end:" + (data[data.length - 1] & 0xff);
            return result;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.position(1);
            result.source = readString(buffer);
            result.sourceFlag = readInt(buffer);
            result.bizClientMsgId = readString(buffer);
            result.bizChatId = readInt(buffer);
            result.bizChatUserId = readInt(buffer);
            result.msgSeq = readInt(buffer);
            result.flag = readInt(buffer);
            result.historyId = readInt(buffer);
            result.reserved = readInt(buffer);
            result.transContent = readString(buffer);
            result.transBrandWording = readString(buffer);
            result.msgSource = readString(buffer);
            result.msgSourceType = readInt(buffer);
            result.pushContent = readString(buffer);
            result.extraBytes = readBytes(buffer);
            result.fromUsername = readString(buffer);
            result.toUsername = readString(buffer);
            result.bizChatUserVersion = readInt(buffer);
            result.bizChatVersion = readInt(buffer);
            result.isShowTimer = readInt(buffer);
            result.createTimeHigh = readInt(buffer);
            result.createTimeLow = readInt(buffer);
            result.historySource = readString(buffer);
        } catch (Throwable t) {
            result.error = t.getClass().getSimpleName() + ":" + t.getMessage();
        }
        return result;
    }

    private static String readString(ByteBuffer buffer) {
        int length = readUnsignedShort(buffer);
        if (length == 0) {
            return "";
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, UTF_8);
    }

    private static byte[] readBytes(ByteBuffer buffer) {
        int length = readUnsignedShort(buffer);
        byte[] bytes = new byte[length];
        if (length > 0) {
            buffer.get(bytes);
        }
        return bytes;
    }

    private static int readInt(ByteBuffer buffer) {
        return buffer.getInt();
    }

    private static int readUnsignedShort(ByteBuffer buffer) {
        return buffer.getShort() & 0xffff;
    }

    static final class Result {
        String error;
        String source = "";
        int sourceFlag;
        String bizClientMsgId = "";
        int bizChatId;
        int bizChatUserId;
        int msgSeq;
        int flag;
        int historyId;
        int reserved;
        String transContent = "";
        String transBrandWording = "";
        String msgSource = "";
        int msgSourceType;
        String pushContent = "";
        byte[] extraBytes = new byte[0];
        String fromUsername = "";
        String toUsername = "";
        int bizChatUserVersion;
        int bizChatVersion;
        int isShowTimer;
        int createTimeHigh;
        int createTimeLow;
        String historySource = "";

        boolean isOk() {
            return error == null;
        }

        String summary() {
            if (!isOk()) {
                return "lvbufferError=" + error;
            }
            StringBuilder builder = new StringBuilder();
            append(builder, "source", source);
            append(builder, "sourceFlag", sourceFlag);
            append(builder, "bizClientMsgId", bizClientMsgId);
            append(builder, "msgSeq", msgSeq);
            append(builder, "flag", flag);
            append(builder, "msgSource", msgSource);
            append(builder, "pushContent", pushContent);
            append(builder, "fromUsername", fromUsername);
            append(builder, "toUsername", toUsername);
            append(builder, "historySource", historySource);
            return builder.length() == 0 ? "lvbuffer=empty-fields" : builder.toString();
        }

        private static void append(StringBuilder builder, String key, String value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(key).append('=').append(value.replace('\n', ' ').replace('\r', ' ').trim());
        }

        private static void append(StringBuilder builder, String key, int value) {
            if (value == 0) {
                return;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(key).append('=').append(value);
        }
    }
}
