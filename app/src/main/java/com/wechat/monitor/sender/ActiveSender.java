package com.wechat.monitor.sender;

import android.os.Handler;
import android.os.Looper;

import com.wechat.monitor.core.MonitorContext;

import java.lang.reflect.Method;

/**
 * 主动发送文本消息的 Java 等价实现。
 * 对应 frida/send/frida_active_send_rpc.js 的 rpc.exports.sendtext(talker, content, type)。
 *
 * 反射调用链：
 *   px0.r1.a(talker)   -> 返回 px0.q1
 *   px0.q1.e(content)
 *   px0.q1.h(type)
 *   px0.q1.b()         -> 触发实际发送
 *
 * 线程模型：实际反射调用切到 WeChat 主线程执行，避免在工作线程中调用 UI 相关组件。
 * 安全护栏：仅允许 type=1（普通文本），与 frida 脚本一致。
 *
 * 反射调用会重新触发 SendMessageHook 的拦截器（按 1500ms 去重），因此主动发送的链路
 * 也会被 chain 监听记录，便于自检发送是否真正落地。
 */
public final class ActiveSender {

    private static final String R1_CLASS = "px0.r1";
    private static final String Q1_CLASS = "px0.q1";

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static volatile boolean warmed = false;
    private static volatile Method r1A;
    private static volatile Method q1E;
    private static volatile Method q1H;
    private static volatile Method q1B;

    private ActiveSender() {
    }

    public static synchronized boolean warmUp(MonitorContext ctx) {
        if (warmed) {
            return true;
        }
        if (ctx == null) {
            return false;
        }
        ClassLoader classLoader = ctx.getClassLoader();
        if (classLoader == null) {
            ctx.log("ActiveSender warmUp 失败: ClassLoader 未就绪");
            return false;
        }
        try {
            Class<?> r1 = classLoader.loadClass(R1_CLASS);
            Class<?> q1 = classLoader.loadClass(Q1_CLASS);

            r1A = r1.getDeclaredMethod("a", String.class);
            r1A.setAccessible(true);

            q1E = q1.getDeclaredMethod("e", String.class);
            q1E.setAccessible(true);

            q1H = q1.getDeclaredMethod("h", int.class);
            q1H.setAccessible(true);

            q1B = q1.getDeclaredMethod("b");
            q1B.setAccessible(true);

            warmed = true;
            ctx.log("ActiveSender 就绪 (R1.a=" + r1A.getName()
                + " Q1.e=" + q1E.getName()
                + " Q1.h=" + q1H.getName()
                + " Q1.b=" + q1B.getName() + ")");
            return true;
        } catch (Throwable t) {
            warmed = false;
            ctx.log("ActiveSender warmUp 失败: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return false;
        }
    }

    public static boolean isReady() {
        return warmed && r1A != null && q1E != null && q1H != null && q1B != null;
    }

    /**
     * 主动发送一条文本消息。
     * 入队后立即返回，实际反射调用在 WeChat 主线程异步执行，发送过程通过
     * MonitorContext.logMessage("主动发送", ...) 与 SendMessageHook 拦截器双重记录。
     */
    public static SendResult sendText(final MonitorContext ctx,
                                      final String talker,
                                      final String content,
                                      final int type) {
        if (ctx == null) {
            return SendResult.fail("MonitorContext 为空");
        }
        if (talker == null || talker.isEmpty()) {
            return SendResult.fail("talker 不能为空");
        }
        if (content == null || content.isEmpty()) {
            return SendResult.fail("content 不能为空");
        }
        if (type != 1) {
            return SendResult.fail("仅支持文本消息 type=1, 实际=" + type);
        }
        if (!isReady() && !warmUp(ctx)) {
            return SendResult.fail("ActiveSender 未就绪 (R1/Q1 反射未缓存)");
        }

        ctx.logMessage("主动发送", "ActiveSender.sendText 入队"
            + " | talker=" + ctx.trimValue(talker)
            + " | content=" + ctx.trimValue(content)
            + " | type=" + type);

        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                doSendOnMainThread(ctx, talker, content, type);
            }
        });

        return SendResult.ok("已入队主线程发送 talker=" + talker);
    }

    private static void doSendOnMainThread(MonitorContext ctx, String talker, String content, int type) {
        try {
            Object q1 = r1A.invoke(null, talker);
            if (q1 == null) {
                ctx.logMessage("主动发送", "ActiveSender 失败: R1.a 返回 null | talker=" + ctx.trimValue(talker));
                return;
            }
            ctx.logMessage("主动发送", "ActiveSender R1.a 完成"
                + " | q1=" + q1.getClass().getName()
                + " | talker=" + ctx.trimValue(talker));

            q1E.invoke(q1, content);
            q1H.invoke(q1, type);
            ctx.logMessage("主动发送", "ActiveSender Q1.e/Q1.h 完成"
                + " | content=" + ctx.trimValue(content)
                + " | type=" + type);

            q1B.invoke(q1);
            ctx.logMessage("主动发送", "ActiveSender Q1.b 已调用"
                + " | talker=" + ctx.trimValue(talker));
        } catch (Throwable t) {
            ctx.logMessage("主动发送", "ActiveSender 反射调用异常: "
                + t.getClass().getSimpleName() + " " + t.getMessage());
            ctx.log("ActiveSender doSend 异常: " + t);
        }
    }
}
