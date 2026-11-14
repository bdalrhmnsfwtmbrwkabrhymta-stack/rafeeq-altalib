package com.example.domain.security

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.security.MessageDigest

/**
 * 🛡️ فئة الحماية البرمجية الخفيفة (Lightweight Security Guard)
 * --------------------------------------------------------
 * مصممة لتوفير طبقة أمان ذكية وخفيفة بدون التأثير على أداء التطبيق
 * وبدون التسبب في إغلاق مفاجئ (Crashes) أو إنذارات كاذبة (False Positives).
 */
object LightweightSecurityGuard {

    /**
     * تشفير النصوص وكلمات المرور والرموز السرية باستخدام SHA-256
     */
    fun hashSha256(input: String): String {
        if (input.isEmpty()) return ""
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * التحقق من تطابق كلمة المرور/الرمز السري مع التشفير أو النص الأصلي (للتوافق العكسي الآمن)
     */
    fun verifyPinOrPassword(input: String, storedHashOrPlain: String): Boolean {
        if (input.isBlank() || storedHashOrPlain.isBlank()) return false
        val trimmedInput = input.trim()
        val trimmedStored = storedHashOrPlain.trim()
        if (trimmedInput == trimmedStored) return true
        val inputHash = hashSha256(trimmedInput)
        return inputHash.equals(trimmedStored, ignoreCase = true)
    }

    // قائمة بأسماء حزم أدوات التعديل والهندسة العكسية والتزييف الشهيرة
    private val KNOWN_PATCHER_PACKAGES = listOf(
        "com.chelpus.lackypatch",
        "com.dimonvideo.luckypatcher",
        "com.forpda.lp",
        "com.android.vending.billing.InAppBillingService.LUCK",
        "com.android.vending.billing.InAppBillingService.LOCK",
        "org.luckyandroid.patcher",
        "com.topjohnwu.magisk"
    )

    /**
     * 1. تفعيل حظر تصوير الشاشة وتسجيل الفيديو (FLAG_SECURE)
     * يمنع التقاط Screenshots وتسجيل الشاشة وحماية المحتوى الحساس من الظهور في قائمة التطبيقات الحديثة (Recent Apps).
     */
    fun enableScreenSecurity(activity: Activity?) {
        try {
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } catch (_: Exception) {
            // معالجة صامتة لضمان عدم حدوث أي انهيار
        }
    }

    /**
     * إلغاء حظر تصوير الشاشة عند الحاجة لشاشة معينة
     */
    fun disableScreenSecurity(activity: Activity?) {
        try {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } catch (_: Exception) {
            // معالجة صامتة
        }
    }

    /**
     * 2. التحقق من سلامة اسم الحزمة الأصلي (Package Name Integrity Check)
     * للتأكد من عدم قيام طرف آخر بتغيير معرف الحزمة وإعادة تجميع التطبيق تحت اسم مختلف.
     */
    fun isPackageNameTampered(context: Context, expectedPackageName: String): Boolean {
        return try {
            val currentPackage = context.packageName
            !currentPackage.equals(expectedPackageName, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 3. فحص خفيف وآمن للمحاكيات المشبوهة (Lightweight Safe Emulator Detection)
     * يفحص العلامات التجارية وخصائص الأجهزة الافتراضية الشائعة دون التأثير على الهواتف الحقيقية.
     */
    fun isRunningOnSuspiciousEmulator(): Boolean {
        return try {
            val fingerprint = Build.FINGERPRINT.lowercase()
            val model = Build.MODEL.lowercase()
            val manufacturer = Build.MANUFACTURER.lowercase()
            val hardware = Build.HARDWARE.lowercase()
            val board = Build.BOARD.lowercase()
            val product = Build.PRODUCT.lowercase()

            val isGeneric = fingerprint.startsWith("generic") || fingerprint.startsWith("unknown")
            val isGoogleSdk = model.contains("google_sdk") || model.contains("emulator") || model.contains("android sdk built for")
            val isGenymotion = manufacturer.contains("genymotion")
            val isGoldfish = hardware.contains("goldfish") || hardware.contains("ranchu")
            val isVbox = hardware.contains("vbox86") || product.contains("vbox86p") || board.contains("vbox86")

            isGeneric || isGoogleSdk || isGenymotion || isGoldfish || isVbox
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 4. فحص خفيف لوجود تطبيقات التعديل الشهيرة (Lucky Patcher / Tamper Tools Detection)
     */
    fun hasModdingToolsInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in KNOWN_PATCHER_PACKAGES) {
            try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                return true // تم العثور على أداة تعديل مثبتة
            } catch (_: PackageManager.NameNotFoundException) {
                // الحزمة غير موجودة، استمر في الفحص
            } catch (_: Exception) {
                // تجاهل الأخطاء العارضة
            }
        }
        return false
    }

    /**
     * تقرير أمني شامل وخفيف يُعطي حالة النظام دون إيقاف التطبيق إجبارياً
     */
    data class SecurityStatus(
        val isPackageValid: Boolean,
        val isEmulator: Boolean,
        val hasModTools: Boolean,
        val isSecure: Boolean
    )

    fun evaluateSecurity(context: Context, expectedPackageName: String = context.packageName): SecurityStatus {
        val tampered = isPackageNameTampered(context, expectedPackageName)
        val emulator = isRunningOnSuspiciousEmulator()
        val modTools = hasModdingToolsInstalled(context)

        return SecurityStatus(
            isPackageValid = !tampered,
            isEmulator = emulator,
            hasModTools = modTools,
            isSecure = !tampered && !modTools
        )
    }
}

/**
 * 🔒 مكون Jetpack Compose لحماية أي شاشة محددة تلقائياً من تصوير الشاشة وتسجيل الفيديو
 * يتم تفعيل الحماية بمجرد دخول الشاشة، وإلغاؤها عند مغادرتها (اختياري).
 */
@Composable
fun SecureScreenContainer(
    keepSecureOnExit: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? Activity
        LightweightSecurityGuard.enableScreenSecurity(activity)

        onDispose {
            if (!keepSecureOnExit) {
                LightweightSecurityGuard.disableScreenSecurity(activity)
            }
        }
    }

    content()
}
