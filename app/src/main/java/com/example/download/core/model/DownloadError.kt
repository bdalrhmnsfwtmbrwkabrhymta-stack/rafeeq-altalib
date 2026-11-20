package com.example.download.core.model

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

/**
 * Explicit 20-variant structured error taxonomy for extraction, segmented downloads,
 * FFmpeg/muxing, verification, storage, and signed engine updates.
 */
sealed class DownloadError(
    val code: String,
    val userMessageAr: String,
    val userMessageEn: String,
    open val technicalDetail: String,
    val isRetryable: Boolean,
    val suggestLoginOrBrowser: Boolean = false
) {
    data class UnsupportedPlatform(
        val host: String,
        override val technicalDetail: String = "Unsupported platform: $host"
    ) : DownloadError(
        code = "UNSUPPORTED_PLATFORM",
        userMessageAr = "هذه المنصة أو نوع الرابط غير مدعوم حالياً ($host).",
        userMessageEn = "This platform or link format is not supported ($host).",
        technicalDetail = technicalDetail,
        isRetryable = false,
        suggestLoginOrBrowser = true
    )

    data class UnsupportedMedia(
        val mediaType: String,
        override val technicalDetail: String = "Unsupported media type: $mediaType"
    ) : DownloadError(
        code = "UNSUPPORTED_MEDIA",
        userMessageAr = "الرابط يشير إلى صفحة ويب أو صيغة غير قابلة للتحميل المباشر ($mediaType).",
        userMessageEn = "Target resource is a web page or unsupported media ($mediaType).",
        technicalDetail = technicalDetail,
        isRetryable = false,
        suggestLoginOrBrowser = true
    )

    data class ExtractionFailed(
        val platformName: String,
        override val technicalDetail: String
    ) : DownloadError(
        code = "EXTRACTION_FAILED",
        userMessageAr = "تعذر استخلاص روابط التشغيل الفعلية من $platformName. يمكنك إعادة المحاولة أو الفتح في المتصفح الذكي.",
        userMessageEn = "Failed to extract playable media streams from $platformName.",
        technicalDetail = technicalDetail,
        isRetryable = true,
        suggestLoginOrBrowser = true
    )

    data class ExpiredMediaURL(
        val statusCode: Int = 403,
        override val technicalDetail: String = "Media stream URL expired (HTTP $statusCode)"
    ) : DownloadError(
        code = "EXPIRED_MEDIA_URL",
        userMessageAr = "انتهت صلاحية رابط التحميل المؤقت من الخادم. اضغط «إعادة المحاولة» لتجديد الرابط.",
        userMessageEn = "Temporary media URL expired (HTTP $statusCode). Retry to refresh.",
        technicalDetail = technicalDetail,
        isRetryable = true
    )

    data class AuthenticationRequired(
        val platformName: String,
        override val technicalDetail: String = "Authentication/cookies required for $platformName"
    ) : DownloadError(
        code = "AUTHENTICATION_REQUIRED",
        userMessageAr = "يتطلب هذا المحتوى تسجيل الدخول في $platformName لعرضه أو تحميله.",
        userMessageEn = "Sign-in is required on $platformName to access this media.",
        technicalDetail = technicalDetail,
        isRetryable = false,
        suggestLoginOrBrowser = true
    )

    data class PrivateContent(
        val platformName: String,
        override val technicalDetail: String = "Private or restricted content on $platformName"
    ) : DownloadError(
        code = "PRIVATE_CONTENT",
        userMessageAr = "هذا المحتوى خاص أو محذوف أو غير متاح للعرض العام على $platformName.",
        userMessageEn = "This content is private, deleted, or unavailable on $platformName.",
        technicalDetail = technicalDetail,
        isRetryable = false,
        suggestLoginOrBrowser = true
    )

    data class ProtectedContent(
        override val technicalDetail: String = "DRM / encrypted / paid stream"
    ) : DownloadError(
        code = "PROTECTED_CONTENT",
        userMessageAr = "هذا المحتوى محمي بتشفير DRM أو محتوى مدفوع ولا يمكن تنزيله.",
        userMessageEn = "This content is DRM-protected or paid and cannot be downloaded.",
        technicalDetail = technicalDetail,
        isRetryable = false
    )

    data class NetworkUnavailable(
        override val technicalDetail: String = "No active network connection or DNS failure"
    ) : DownloadError(
        code = "NETWORK_UNAVAILABLE",
        userMessageAr = "انقطع الاتصال بالإنترنت أو تعذر الوصول للخادم. تحقق من الشبكة ثم أعد المحاولة.",
        userMessageEn = "Network is unavailable or host could not be resolved.",
        technicalDetail = technicalDetail,
        isRetryable = true
    )

    data class NetworkTimeout(
        override val technicalDetail: String = "Socket/connect timeout"
    ) : DownloadError(
        code = "NETWORK_TIMEOUT",
        userMessageAr = "انتهت مهلة الاتصال بالخادم أثناء القراءة. أعد المحاولة.",
        userMessageEn = "Connection timed out while communicating with the server.",
        technicalDetail = technicalDetail,
        isRetryable = true
    )

    data class HttpError(
        val statusCode: Int,
        override val technicalDetail: String = "HTTP status $statusCode"
    ) : DownloadError(
        code = "HTTP_$statusCode",
        userMessageAr = "رفض الخادم الطلب برمز HTTP $statusCode.",
        userMessageEn = "Server returned HTTP error $statusCode.",
        technicalDetail = technicalDetail,
        isRetryable = statusCode in setOf(408, 429, 500, 502, 503, 504),
        suggestLoginOrBrowser = statusCode in setOf(400, 401, 403)
    )

    data class InvalidRangeResponse(
        val expectedRange: String,
        val actualCode: Int,
        override val technicalDetail: String = "Expected HTTP 206 for Range $expectedRange but got HTTP $actualCode"
    ) : DownloadError(
        code = "INVALID_RANGE_RESPONSE",
        userMessageAr = "استجابة تجزئة الملف من الخادم غير متوافقة؛ أعد المحاولة للتحميل في مسار واحد.",
        userMessageEn = "Server returned an invalid HTTP Range response ($actualCode).",
        technicalDetail = technicalDetail,
        isRetryable = true
    )

    data class StoragePermissionError(
        override val technicalDetail: String = "Storage write permission or MediaStore insert failure"
    ) : DownloadError(
        code = "STORAGE_PERMISSION_ERROR",
        userMessageAr = "تعذر الحفظ في مجلد التنزيلات بسبب أذونات التخزين.",
        userMessageEn = "Cannot write file to Downloads due to storage permissions.",
        technicalDetail = technicalDetail,
        isRetryable = false
    )

    data class InsufficientStorage(
        val requiredBytes: Long,
        val availableBytes: Long,
        override val technicalDetail: String = "Required=$requiredBytes bytes, Available=$availableBytes bytes"
    ) : DownloadError(
        code = "INSUFFICIENT_STORAGE",
        userMessageAr = "المساحة التخزينية على الهاتف غير كافية لإتمام التنزيل.",
        userMessageEn = "Insufficient storage space on device.",
        technicalDetail = technicalDetail,
        isRetryable = false
    )

    data class FileChangedOnServer(
        override val technicalDetail: String = "Remote ETag, Last-Modified, or Content-Length changed before resume"
    ) : DownloadError(
        code = "FILE_CHANGED_ON_SERVER",
        userMessageAr = "تغيّر الملف على الخادم (ETag/الحجم) منذ التوقف المؤقت. اضغط «إعادة المحاولة» للبدء من جديد بسلامة.",
        userMessageEn = "The remote file changed on the server; restart required to prevent corruption.",
        technicalDetail = technicalDetail,
        isRetryable = true
    )

    data class DownloadInterrupted(
        override val technicalDetail: String = "Download interrupted by pause, network drop, or cancellation"
    ) : DownloadError(
        code = "DOWNLOAD_INTERRUPTED",
        userMessageAr = "توقف التحميل مؤقتاً ويمكن استكماله من نقطة التوقف.",
        userMessageEn = "Download was interrupted and can be resumed.",
        technicalDetail = technicalDetail,
        isRetryable = true
    )

    data class EngineUpdateFailed(
        override val technicalDetail: String
    ) : DownloadError(
        code = "ENGINE_UPDATE_FAILED",
        userMessageAr = "تعذر تحديث محرك الاستخلاص مع الإبقاء على النسخة المستقرة الحالية.",
        userMessageEn = "Engine update failed safely; existing working engine kept.",
        technicalDetail = technicalDetail,
        isRetryable = true
    )

    data class EngineCompatibilityError(
        override val technicalDetail: String
    ) : DownloadError(
        code = "ENGINE_COMPATIBILITY_ERROR",
        userMessageAr = "حزمة المحرك غير متوافقة مع معمارية أو إصدار نظام هذا الجهاز.",
        userMessageEn = "Engine package is incompatible with device ABI or Android version.",
        technicalDetail = technicalDetail,
        isRetryable = false
    )

    data class FFmpegFailed(
        override val technicalDetail: String
    ) : DownloadError(
        code = "FFMPEG_FAILED",
        userMessageAr = "فشل معالج FFmpeg أثناء تحويل الصوت أو معالجة البث.",
        userMessageEn = "FFmpeg processing failed.",
        technicalDetail = technicalDetail,
        isRetryable = true
    )

    data class MergeFailed(
        override val technicalDetail: String
    ) : DownloadError(
        code = "MERGE_FAILED",
        userMessageAr = "تعذر دمج مسار الفيديو والصوت للملف المحمّل (تم الحفاظ على الملفات المؤقتة).",
        userMessageEn = "Failed to merge video and audio tracks.",
        technicalDetail = technicalDetail,
        isRetryable = true
    )

    data class IntegrityCheckFailed(
        override val technicalDetail: String
    ) : DownloadError(
        code = "INTEGRITY_CHECK_FAILED",
        userMessageAr = "فشل التحقق من سلامة الملف أو تطابق حجمه/توقيعه بعد التنزيل.",
        userMessageEn = "Downloaded artifact failed integrity or hash verification.",
        technicalDetail = technicalDetail,
        isRetryable = true
    )
}

class DownloadEngineException(
    val error: DownloadError,
    cause: Throwable? = null
) : IOException("${error.code}: ${error.technicalDetail}", cause)

object DownloadErrorMapper {

    fun fromHttpStatus(statusCode: Int, url: String = "", detail: String = ""): DownloadError {
        return when (statusCode) {
            401 -> DownloadError.AuthenticationRequired(url, detail.ifBlank { "HTTP 401 for $url" })
            403, 410 -> DownloadError.ExpiredMediaURL(statusCode, detail.ifBlank { "HTTP $statusCode for $url" })
            404 -> DownloadError.PrivateContent(url, detail.ifBlank { "HTTP 404 Not Found for $url" })
            416 -> DownloadError.InvalidRangeResponse("Range", 416, detail.ifBlank { "HTTP 416 Range Not Satisfiable" })
            else -> DownloadError.HttpError(statusCode, detail.ifBlank { "HTTP $statusCode for $url" })
        }
    }

    fun fromThrowable(t: Throwable, fallbackPlatform: String = ""): DownloadError {
        if (t is DownloadEngineException) return t.error
        val msg = (t.message ?: t.javaClass.simpleName).lowercase(Locale.ROOT)
        return when {
            t is UnknownHostException || msg.contains("unable to resolve host") || msg.contains("network is unreachable") ->
                DownloadError.NetworkUnavailable("${t.javaClass.simpleName}: ${t.message}")
            t is SocketTimeoutException || msg.contains("timeout") || msg.contains("timed out") ->
                DownloadError.NetworkTimeout("${t.javaClass.simpleName}: ${t.message}")
            t is SecurityException ->
                DownloadError.StoragePermissionError("${t.javaClass.simpleName}: ${t.message}")
            t is SSLException ->
                DownloadError.NetworkUnavailable("TLS/SSL error: ${t.message}")
            msg.contains("drm") || msg.contains("widevine") || msg.contains("playready") ||
                msg.contains("fairplay") || msg.contains("paid content") || msg.contains("members-only") ->
                DownloadError.ProtectedContent(t.message ?: "DRM/Paid")
            msg.contains("private") || msg.contains("video unavailable") || msg.contains("has been removed") ->
                DownloadError.PrivateContent(fallbackPlatform.ifBlank { "source" }, t.message ?: "Private/unavailable")
            msg.contains("login") || msg.contains("sign in") || msg.contains("cookies") ||
                msg.contains("logged in") || msg.contains("401") ->
                DownloadError.AuthenticationRequired(fallbackPlatform.ifBlank { "source" }, t.message ?: "Login required")
            msg.contains("enos") || msg.contains("no space") ->
                DownloadError.InsufficientStorage(0L, 0L, t.message ?: "No space left")
            msg.contains("403") || msg.contains("410") ->
                DownloadError.ExpiredMediaURL(if (msg.contains("410")) 410 else 403, t.message ?: "Expired URL")
            msg.contains("ffmpeg") ->
                DownloadError.FFmpegFailed(t.message ?: "FFmpeg failure")
            msg.contains("merge") || msg.contains("mux") ->
                DownloadError.MergeFailed(t.message ?: "Mux failure")
            msg.contains("sha-256") || msg.contains("checksum") || msg.contains("integrity") ->
                DownloadError.IntegrityCheckFailed(t.message ?: "Integrity mismatch")
            else ->
                DownloadError.ExtractionFailed(
                    fallbackPlatform.ifBlank { "المصدر" },
                    "${t.javaClass.simpleName}: ${t.message ?: "Unknown error"}"
                )
        }
    }
}
