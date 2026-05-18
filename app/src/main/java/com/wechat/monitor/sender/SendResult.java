package com.wechat.monitor.sender;

/**
 * 主动发送结果。
 * ok 表示请求是否成功入队/校验通过；message 用于打印；error 在反射调用阶段抛出时携带。
 * 不直接代表「微信服务端是否签收」，那部分由 SendMessageHook 监听到的 onGYNetEnd 回调反映。
 */
public final class SendResult {

    private final boolean ok;
    private final String message;
    private final Throwable error;

    private SendResult(boolean ok, String message, Throwable error) {
        this.ok = ok;
        this.message = message;
        this.error = error;
    }

    public static SendResult ok(String message) {
        return new SendResult(true, message, null);
    }

    public static SendResult fail(String message) {
        return new SendResult(false, message, null);
    }

    public static SendResult fail(String message, Throwable error) {
        return new SendResult(false, message, error);
    }

    public boolean isOk() {
        return ok;
    }

    public String getMessage() {
        return message == null ? "" : message;
    }

    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        return "SendResult{" + (ok ? "ok" : "fail") + ", " + getMessage() + "}";
    }
}
