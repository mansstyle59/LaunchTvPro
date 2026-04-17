# ProGuard rules - TVWebBrowser

# Garder les classes AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Garder le bridge JavaScript (annotations @JavascriptInterface)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.launchtvpro.MainActivity$TVBridge { *; }

# WebView
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
    public *;
}

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# Leanback TV
-keep class androidx.leanback.** { *; }
-dontwarn androidx.leanback.**

# Ne pas obfusquer les logs en debug
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
