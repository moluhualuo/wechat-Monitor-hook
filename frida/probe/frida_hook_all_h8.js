Java.perform(function() {
    console.log("[PROBE] PID=" + Process.id + " Hooking ALL h8 methods that take f8 as first arg");

    var h8 = Java.use("com.tencent.mm.storage.h8");
    var methods = h8.class.getDeclaredMethods();
    var count = 0;

    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        var params = m.getParameterTypes();
        if (params.length > 0 && params[0].getName() === "com.tencent.mm.storage.f8") {
            (function(methodName) {
                try {
                    var overloads = h8[methodName].overloads;
                    for (var j = 0; j < overloads.length; j++) {
                        overloads[j].implementation = function() {
                            console.log("[HIT] h8." + methodName + " called! PID=" + Process.id + " args=" + arguments.length);
                            return this[methodName].apply(this, arguments);
                        };
                    }
                    count++;
                } catch(e) {
                    // skip
                }
            })(m.getName());
        }
    }

    console.log("[DONE] Hooked " + count + " h8 methods that accept f8. Send a message!");
});
