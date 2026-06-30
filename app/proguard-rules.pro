# Keep Gson classes
-keep class com.google.gson.** { *; }
-keepattributes Signature

# Keep OkHttp
-keep class okhttp3.** { *; }
-keepattributes Signature

# Keep Android classes
-keep class android.nfc.** { *; }
-keep class android.bluetooth.** { *; }

# Keep Room entities
-keep class com.speakerroom.tap2sound.data.entity.** { *; }

# Keep coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
