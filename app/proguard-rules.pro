# Add project specific ProGuard rules here.
# You can control the set of applied configuration rules using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Xposed related classes
-keep class de.robv.android.xposed.** { *; }
-keep class com.wechat.monitor.WeChatMonitorHook { *; }

# Keep message related classes
-keep class com.wechat.monitor.MessageLogger { *; }
-keep class com.wechat.monitor.ConfigManager { *; }

# Keep all public methods
-keepclassmembers class * {
    public *;
}

# Optimization settings
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# Optimization settings
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*