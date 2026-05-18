package com.wechat.monitor;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.util.Map;

/**
 * 支付消息监听器
 * 不再依赖 Xposed API，仅作为数据处理辅助类
 */
public class PayMessageMonitor {

    private static final String TAG = "PayMessageMonitor";

    // 支付消息类型常量
    private static final int PAY_MSG_TYPE_WALLET_CHANGE = 0x10;      // 16: 钱包类型改变
    private static final int PAY_MSG_TYPE_WALLET_UPDATE = 0x11;      // 17: 钱包类型更新
    private static final int PAY_MSG_TYPE_C2C_UPDATE = 0x25;         // 37: C2C内容更新

    private Context context;
    private MessageLogger messageLogger;

    public PayMessageMonitor(Context context, MessageLogger messageLogger) {
        this.context = context;
        this.messageLogger = messageLogger;
    }

    /**
     * 解析支付消息 XML
     */
    @SuppressWarnings("unchecked")
    public String parsePaymentXml(String xml, ClassLoader classLoader) {
        if (xml == null || classLoader == null) {
            return null;
        }
        try {
            // 使用微信的 XML 解析工具类
            Class<?> aaClass = classLoader.loadClass("com.tencent.mm.sdk.platformtools.aa");
            java.lang.reflect.Method parseMethod = aaClass.getDeclaredMethod("d", String.class, String.class, String.class);
            parseMethod.setAccessible(true);

            Map<String, String> map = (Map<String, String>) parseMethod.invoke(null, xml, "sysmsg", null);
            if (map == null || map.isEmpty()) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            String payType = map.get(".sysmsg.paymsg.PayMsgType");
            if (payType != null) {
                sb.append("PayMsgType=").append(payType);
                sb.append("(").append(getPayMsgTypeDesc(payType)).append(")");
            }
            String walletType = map.get(".sysmsg.paymsg.WalletType");
            if (walletType != null) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("WalletType=").append(walletType);
            }

            // 输出所有 paymsg 相关字段
            for (Map.Entry<String, String> e : map.entrySet()) {
                String key = e.getKey();
                if (key.contains("paymsg") && !key.endsWith("PayMsgType") && !key.endsWith("WalletType")) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append(key.substring(key.lastIndexOf('.') + 1)).append("=").append(e.getValue());
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Throwable t) {
            Log.e(TAG, "parsePaymentXml failed: " + t.getMessage());
            return null;
        }
    }

    /**
     * 处理支付消息
     */
    public void handlePayMessage(int payMsgType, Map<String, String> msgMap, String content) {
        String message = "";

        switch (payMsgType) {
            case PAY_MSG_TYPE_WALLET_CHANGE:
                String walletType = msgMap.get(".sysmsg.paymsg.WalletType");
                message = String.format("[钱包类型改变] WalletType=%s", walletType);
                break;

            case PAY_MSG_TYPE_WALLET_UPDATE:
                String walletTypeUpdate = msgMap.get(".sysmsg.paymsg.WalletType");
                message = String.format("[钱包类型更新] WalletType=%s", walletTypeUpdate);
                break;

            case PAY_MSG_TYPE_C2C_UPDATE:
                message = String.format("[C2C内容更新] %s", content);
                break;

            default:
                message = String.format("[未知支付消息] Type=%d, Content=%s", payMsgType, content);
                break;
        }

        logPayMessage(message);
        Log.d(TAG, message);
    }

    /**
     * 记录支付消息
     */
    private void logPayMessage(String message) {
        if (messageLogger != null) {
            messageLogger.logMessage("支付消息", message);
        }
    }

    /**
     * 获取支付消息类型描述
     */
    public static String getPayMsgTypeDesc(String type) {
        if (type == null) return "?";
        switch (type) {
            case "16": return "钱包类型改变";
            case "17": return "钱包类型更新";
            case "37": return "C2C内容更新";
            default: return "未知";
        }
    }
}
