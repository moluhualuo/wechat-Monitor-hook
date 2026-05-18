Java.perform(function() {
    console.log("[PROBE] Checking main process hooks...");

    var h8 = Java.use("com.tencent.mm.storage.h8");

    h8.U8.implementation = function(msg) {
        console.log("[MAIN] h8.U8 CALLED in PID=" + Process.id);
        var result = this.U8(msg);
        console.log("[MAIN] h8.U8 result=" + result);
        return result;
    };

    h8.W8.overload("com.tencent.mm.storage.f8", "boolean").implementation = function(msg, b) {
        console.log("[MAIN] h8.W8 CALLED in PID=" + Process.id);
        var result = this.W8(msg, b);
        console.log("[MAIN] h8.W8 result=" + result);
        return result;
    };

    console.log("[DONE] Frida hooks installed on h8.U8 and h8.W8 in PID=" + Process.id);
    console.log("[DONE] Send a message now!");
});
