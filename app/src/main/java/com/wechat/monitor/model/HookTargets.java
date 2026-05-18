package com.wechat.monitor.model;

public final class HookTargets {
    public static final String WECHAT_PACKAGE = "com.tencent.mm";
    public static final String Q1 = "px0.q1";
    public static final String R1 = "px0.r1";
    public static final String MESSAGE_INFO = "com.tencent.mm.storage.f8";
    public static final String CONTACT_INFO = "com.tencent.mm.storage.a3";
    public static final String CONTACT_STORAGE = "com.tencent.mm.storage.l3";
    public static final String MESSAGE_STORAGE = "com.tencent.mm.storage.h8";
    public static final String ADD_MSG = "com.tencent.mm.modelbase.p0";
    public static final String STORAGE_DISPATCHER = "xv0.t9";
    public static final String MESSAGE_DISPATCHER = "xv0.wc";
    public static final String EVENT = "com.tencent.mm.sdk.event.IEvent";
    public static final String SEND_DISPATCH = "fo1.g";
    public static final String NET_SCENE_SEND = "px0.r0";
    public static final String LUCKY_MONEY_UI = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewReceiveUI";
    public static final String TRANSFER_UI = "com.tencent.mm.plugin.subapp.ui.friend.FMessageTransferUI";
    public static final String LAUNCHER_UI = "com.tencent.mm.ui.LauncherUI";
    public static final String WCDB_DATABASE = "com.tencent.wcdb.database.SQLiteDatabase";
    public static final String MESSAGE_TABLE = "message";

    private HookTargets() {
    }
}
