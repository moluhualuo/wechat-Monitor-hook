Java.perform(function() {
    console.log("[PROBE] Hooking h8.U8 and h8.W8 in PUSH process PID=" + Process.id);

    var h8 = Java.use("com.tencent.mm.storage.h8");

    h8.U8.implementation = function(msg) {
        console.log("[PUSH] h8.U8 CALLED in PID=" + Process.id);
        var result = this.U8(msg);
        console.log("[PUSH] h8.U8 result=" + result);
        return result;
    };

    h8.W8.overload("com.tencent.mm.storage.f8", "boolean").implementation = function(msg, b) {
        console.log("[PUSH] h8.W8 CALLED in PID=" + Process.id);
        var result = this.W8(msg, b);
        console.log("[PUSH] h8.W8 result=" + result);
        return result;
    };

    console.log("[DONE] Send a message now!");
});
