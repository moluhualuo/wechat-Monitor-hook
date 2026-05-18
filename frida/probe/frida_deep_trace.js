Java.perform(function() {
    console.log("[PROBE] PID=" + Process.id + " Hooking h8 constructor + all instance creation");

    var h8 = Java.use("com.tencent.mm.storage.h8");
    var f8 = Java.use("com.tencent.mm.storage.f8");

    // Hook f8 constructor to see if messages are being created
    f8.$init.overloads.forEach(function(overload) {
        overload.implementation = function() {
            console.log("[HIT] f8.<init> called! args=" + arguments.length + " PID=" + Process.id);
            return overload.apply(this, arguments);
        };
    });

    // Also try hooking the xv0.t9 and xv0.wc classes
    try {
        var t9 = Java.use("xv0.t9");
        var t9Methods = t9.class.getDeclaredMethods();
        console.log("[INFO] xv0.t9 has " + t9Methods.length + " methods");
        for (var i = 0; i < t9Methods.length; i++) {
            console.log("[INFO] xv0.t9." + t9Methods[i].getName() + " params=" + t9Methods[i].getParameterTypes().length);
        }
        t9.n.implementation = function(msg, addMsg) {
            console.log("[HIT] xv0.t9.n CALLED! PID=" + Process.id);
            return this.n(msg, addMsg);
        };
    } catch(e) {
        console.log("[ERR] t9: " + e);
    }

    try {
        var wc = Java.use("xv0.wc");
        var wcMethods = wc.class.getDeclaredMethods();
        console.log("[INFO] xv0.wc has " + wcMethods.length + " methods");
        for (var i = 0; i < wcMethods.length; i++) {
            console.log("[INFO] xv0.wc." + wcMethods[i].getName() + " params=" + wcMethods[i].getParameterTypes().length);
        }
    } catch(e) {
        console.log("[ERR] wc: " + e);
    }

    // Hook android.database.sqlite.SQLiteDatabase.insert to see if ANY db writes happen
    try {
        var SQLiteDatabase = Java.use("android.database.sqlite.SQLiteDatabase");
        SQLiteDatabase.insert.implementation = function(table, nullColumnHack, values) {
            if (table && table.indexOf("message") >= 0) {
                console.log("[DB] SQLiteDatabase.insert table=" + table + " PID=" + Process.id);
            }
            return this.insert(table, nullColumnHack, values);
        };
        SQLiteDatabase.insertWithOnConflict.implementation = function(table, nullColumnHack, values, conflictAlgorithm) {
            if (table && table.indexOf("message") >= 0) {
                console.log("[DB] SQLiteDatabase.insertWithOnConflict table=" + table + " PID=" + Process.id);
            }
            return this.insertWithOnConflict(table, nullColumnHack, values, conflictAlgorithm);
        };
        console.log("[DONE] Also hooked SQLiteDatabase.insert for message tables");
    } catch(e) {
        console.log("[ERR] SQLite: " + e);
    }

    console.log("[DONE] All hooks installed. Send a message!");
});
