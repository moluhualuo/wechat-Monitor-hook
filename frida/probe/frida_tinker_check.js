// The issue is clear: WeChat uses Tinker hot-patching.
// Classes are loaded from tinker_classN.apk, NOT the base APK.
// The ClassLoader used in onPackageLoaded might be different from the one
// that actually loads the patched classes at runtime.
//
// Let's find the ACTUAL classloader that has the live h8 instances

Java.perform(function() {
    console.log("[TINKER] PID=" + Process.id + " Investigating classloader situation");

    // Enumerate all classloaders
    Java.enumerateClassLoaders({
        onMatch: function(loader) {
            try {
                var h8 = loader.loadClass("com.tencent.mm.storage.h8");
                console.log("[FOUND] h8 loaded by: " + loader.toString().substring(0, 200));
            } catch(e) {
                // not in this loader
            }
        },
        onComplete: function() {
            console.log("[DONE] ClassLoader enumeration complete");
        }
    });

    // Check if there are live h8 instances
    Java.choose("com.tencent.mm.storage.h8", {
        onMatch: function(instance) {
            console.log("[INSTANCE] Found live h8 instance: " + instance.getClass().getName());
            console.log("[INSTANCE] ClassLoader: " + instance.getClass().getClassLoader());
        },
        onComplete: function() {
            console.log("[INSTANCE] h8 instance search complete");
        }
    });

    // Check tinker classloader
    try {
        var app = Java.use("android.app.ActivityThread").currentApplication();
        var cl = app.getClassLoader();
        console.log("[APP] Application classloader: " + cl.toString().substring(0, 300));
        var parentCl = cl.getParent();
        if (parentCl) {
            console.log("[APP] Parent classloader: " + parentCl.toString().substring(0, 300));
        }
    } catch(e) {
        console.log("[ERR] " + e.message);
    }

    console.log("[TINKER] Investigation complete");
});
