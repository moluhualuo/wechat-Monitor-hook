package com.wechat.monitor.sender;

import android.os.Handler;
import android.os.HandlerThread;

import com.wechat.monitor.core.MonitorContext;

/**
 * 命令文件轮询器。
 *
 * 在 Hook 进程内启动一个后台线程，按固定间隔检查 /sdcard/WeChatMonitor/send.json。
 * 之所以用轮询而不是 FileObserver：
 *   - FileObserver 监听 /sdcard 在 Android 11+ scoped storage 下可能受限
 *   - 文件不存在时的 stat 调用成本可忽略，1 秒间隔足够"按钮即时发送"的体感
 *
 * 与 SendTriggerHook (LauncherUI.onResume) 并存：
 *   - CommandPoller 提供"任意时刻立即发送"体验
 *   - SendTriggerHook 作为兜底，模块装配后即使 poller 异常崩溃也能在 onResume 触发
 */
public final class CommandPoller {

    private static final long INTERVAL_MS = 1000L;
    private static final String THREAD_NAME = "WeChatMonitor-CommandPoller";

    private static volatile HandlerThread thread;
    private static volatile Handler handler;
    private static volatile MonitorContext ctxRef;

    private CommandPoller() {
    }

    public static synchronized void start(MonitorContext ctx) {
        if (thread != null) {
            return;
        }
        ctxRef = ctx;
        thread = new HandlerThread(THREAD_NAME);
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(TICK);
        ctx.log("CommandPoller 已启动 interval=" + INTERVAL_MS + "ms");
    }

    public static synchronized void stop() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
        ctxRef = null;
    }

    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            try {
                MonitorContext ctx = ctxRef;
                if (ctx != null) {
                    SendCommandFile.pollAndSend(ctx);
                }
            } catch (Throwable t) {
                MonitorContext ctx = ctxRef;
                if (ctx != null) {
                    ctx.log("CommandPoller 异常: " + t.getClass().getSimpleName() + " " + t.getMessage());
                }
            } finally {
                Handler h = handler;
                if (h != null) {
                    h.postDelayed(this, INTERVAL_MS);
                }
            }
        }
    };
}
