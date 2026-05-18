// Minimal test: hook Log.w to see if ANYTHING is being logged from this process
// Also hook a very common method to prove frida hooks work at all

Java.perform(function() {
    console.log("[TEST] PID=" + Process.id + " Starting minimal hook test");

    // Hook Log.w to see if our module's logs are being suppressed
    var Log = Java.use("android.util.Log");
    var origW = Log.w.overload("java.lang.String", "java.lang.String");
    origW.implementation = function(tag, msg) {
        if (tag === "WeChatMonitor") {
            console.log("[INTERCEPT] Log.w WeChatMonitor: " + msg);
        }
        return origW.call(this, tag, msg);
    };

    // Hook String.contains to prove hooks fire
    var hitCount = 0;
    var String = Java.use("java.lang.String");
    String.contains.implementation = function(s) {
        hitCount++;
        if (hitCount <= 3) {
            console.log("[STRING] String.contains called (first 3 only)");
        }
        return this.contains(s);
    };

    console.log("[TEST] Hooks installed. If String.contains fires, frida works in this process.");
});
