// Hook ALL WeChat processes at once via spawn-gating is not possible with frida CLI
// Instead, let's find where messages actually go by hooking the WCDB/SQLite layer

Java.perform(function() {
    console.log("[TRACE] PID=" + Process.id + " Tracing message-related DB operations");

    // WeChat uses WCDB, not standard SQLite. Try hooking WCDB
    try {
        var wcdb = Java.use("com.tencent.wcdb.database.SQLiteDatabase");
        wcdb.insertWithOnConflict.implementation = function(table, nullColumnHack, values, conflictAlgorithm) {
            if (table && (table.indexOf("message") >= 0 || table.indexOf("msg") >= 0 || table.indexOf("chat") >= 0)) {
                console.log("[WCDB] insertWithOnConflict table=" + table);
            }
            return this.insertWithOnConflict(table, nullColumnHack, values, conflictAlgorithm);
        };
        console.log("[OK] Hooked WCDB insertWithOnConflict");
    } catch(e) {
        console.log("[SKIP] WCDB not found: " + e.message);
    }

    // Try hooking the native WCDB
    try {
        var wcdb2 = Java.use("com.tencent.wcdb2.database.WCDatabase");
        var methods = wcdb2.class.getDeclaredMethods();
        console.log("[INFO] WCDatabase has " + methods.length + " methods");
        for (var i = 0; i < methods.length && i < 30; i++) {
            console.log("[INFO] WCDatabase." + methods[i].getName());
        }
    } catch(e) {
        console.log("[SKIP] wcdb2: " + e.message);
    }

    // Try to find the actual message sending path
    // px0.q1 is the send request class
    try {
        var q1 = Java.use("px0.q1");
        var q1Methods = q1.class.getDeclaredMethods();
        console.log("[INFO] px0.q1 has " + q1Methods.length + " methods:");
        for (var i = 0; i < q1Methods.length; i++) {
            console.log("[INFO] px0.q1." + q1Methods[i].getName() + " params=" + q1Methods[i].getParameterTypes().length + " ret=" + q1Methods[i].getReturnType().getName());
        }
        // Hook all q1 methods
        q1Methods.forEach(function(m) {
            var name = m.getName();
            try {
                q1[name].overloads.forEach(function(overload) {
                    overload.implementation = function() {
                        console.log("[HIT] px0.q1." + name + " called! args=" + arguments.length);
                        return overload.apply(this, arguments);
                    };
                });
            } catch(e) {}
        });
        console.log("[OK] Hooked all px0.q1 methods");
    } catch(e) {
        console.log("[ERR] q1: " + e.message);
    }

    console.log("[DONE] Send a message now!");
});
