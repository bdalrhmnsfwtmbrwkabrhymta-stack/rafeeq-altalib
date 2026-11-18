# ==============================================================================
# 🛡️ إعدادات الحماية والتشفير الخفيفة والآمنة (ProGuard & R8)
# ==============================================================================

# 1. التشفير وضغط الكود (Obfuscation & Optimization)
# ------------------------------------------------------------------------------
# تقليص أسماء الكلاسات والميثودات إلى حروف غير مفهومة (a, b, c...)
-repackageclasses ''
-allowaccessmodification

# الحفاظ على أرقام الأسطر لتقارير الأعطال (Crashlytics) مع إخفاء أسماء الملفات الأصلية
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# الحفاظ على الأنوتيشنز (Annotations) وتوقيعات الـ Generics المهمة لمكتبات مثل Moshi / Retrofit
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# 2. حذف رسائل الـ Log وسجلات الفحص من كود الـ Release نهائياً لمنع تسريب أسرار الكود
# ------------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int println(...);
}

# ------------------------------------------------------------------------------
# 3. استثناء وحماية مكونات أندرويد الأساسية من الحذف والتلف
# ------------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# دعم الأنوتيشن @Keep لضمان عدم حذف أي عنصر تم وسمه بـ Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ------------------------------------------------------------------------------
# 4. حماية نماذج البيانات والكلاسات المستخدمة في الـ JSON والشبكة (Data Models & Network)
# ------------------------------------------------------------------------------
# استثناء كلاسات وموديلات المشروع الخاصة بالبيانات لتفادي مشاكل التحويل (Serialization / Deserialization)
-keep class com.example.data.** { *; }
-keep class com.example.domain.**.**Model** { *; }
-keep class com.example.domain.**.**Models** { *; }
-keep class com.example.domain.**.**Entity** { *; }

# مكتبة Moshi لتحويل JSON
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json *;
}

# مكتبة Retrofit للاتصال بالإنترنت
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# مكتبة OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# ------------------------------------------------------------------------------
# 5. حماية قاعدة بيانات Room ومكتبة Coroutines
# ------------------------------------------------------------------------------
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# مكتبة Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ------------------------------------------------------------------------------
# 6. Jetpack Compose & Material 3
# ------------------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ------------------------------------------------------------------------------
# 7. Smart Download Engine (youtubedl-android, FFmpeg, Aria2c, Media3)
# ------------------------------------------------------------------------------
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
-keep class com.example.download.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

