package com.example.pdfstudio

import android.net.Uri

/**
 * شريط التبويبات السريع المطابق للشكل (1):
 * الكل | القراءة والتحرير | الضغط والحجم | التنظيم والصفحات
 */
enum class PdfHubTab(val titleAr: String) {
    ALL("الكل"),
    READ_EDIT("القراءة والتحرير"),
    COMPRESS("الضغط والحجم"),
    ORGANIZE("التنظيم والصفحات")
}

sealed class PdfStudioScreenRoute {
    data object Hub : PdfStudioScreenRoute()
    data class Reader(
        val uriString: String,
        val title: String,
        val initialPage: Int = 0
    ) : PdfStudioScreenRoute()
    data class Compress(val initialUriString: String? = null) : PdfStudioScreenRoute()
    data class OrganizeMergeSplit(val initialTab: OrganizeModeTab = OrganizeModeTab.ORGANIZE) : PdfStudioScreenRoute()
    data class ConvertOcrSecurity(val initialTab: ConvertSecurityTab = ConvertSecurityTab.OCR_TEXT) : PdfStudioScreenRoute()
}

enum class OrganizeModeTab(val titleAr: String) {
    ORGANIZE("تنظيم الصفحات"),
    MERGE("دمج ملفات"),
    SPLIT("تقسيم PDF")
}

enum class ConvertSecurityTab(val titleAr: String) {
    IMAGES_TO_PDF("صور ← PDF"),
    PDF_TO_IMAGES("PDF ← صور"),
    OCR_TEXT("النص و OCR"),
    SECURITY_META("الأمان والبيانات")
}

enum class PdfReaderViewMode(val labelAr: String) {
    CONTINUOUS("تمرير متصل"),
    SINGLE_PAGE("صفحة بصفحة")
}

enum class PdfEyeCareMode(val labelAr: String) {
    DAY("نهاري"),
    SEPIA("ورقي"),
    NIGHT("ليلي")
}

data class RecentPdfRecord(
    val uriString: String,
    val title: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val lastPageIndex: Int,
    val lastOpenedMillis: Long
)

data class PdfBookmarkItem(
    val id: String,
    val pageIndex: Int,
    val label: String,
    val createdAtMillis: Long
)

data class PdfOutlineNode(
    val title: String,
    val pageIndex: Int,
    val level: Int
)

data class PdfSearchMatch(
    val pageIndex: Int,
    val snippet: String,
    val matchCountOnPage: Int
)

// ==================== Annotations ====================

enum class AnnotationTool(val labelAr: String) {
    NONE("تصفح"),
    HIGHLIGHTER("تظليل"),
    PEN("قلم"),
    ERASER("ممحاة"),
    TEXT_NOTE("ملاحظة لاصقة"),
    TEXT_BOX("إدراج نص"),
    SHAPE_RECT("مربع"),
    SHAPE_OVAL("دائرة"),
    SHAPE_ARROW("سهم"),
    REDACT("طمس حساس"),
    STAMP("ختم دراسي")
}

enum class StudyStampType(val textAr: String, val badgeColor: Long) {
    IMPORTANT("مهم جداً", 0xFFDC2626),
    REVIEWED("تمت المراجعة", 0xFF059669),
    SUMMARY("ملخص", 0xFF1A6FE3)
}

data class NormalizedPoint(val x: Float, val y: Float)

data class PdfStrokePath(
    val id: String,
    val points: List<NormalizedPoint>,
    val colorArgb: Long,
    val strokeWidthDp: Float,
    val isHighlighter: Boolean
)

data class PdfTextNote(
    val id: String,
    val x: Float,
    val y: Float,
    val text: String,
    val colorArgb: Long
)

data class PdfTextBoxItem(
    val id: String,
    val x: Float,
    val y: Float,
    val text: String,
    val colorArgb: Long
)

enum class AnnotationShapeKind { RECT, OVAL, ARROW, REDACT }

data class PdfShapeItem(
    val id: String,
    val kind: AnnotationShapeKind,
    val start: NormalizedPoint,
    val end: NormalizedPoint,
    val colorArgb: Long,
    val strokeWidthDp: Float
)

data class PdfStampItem(
    val id: String,
    val stampType: StudyStampType,
    val x: Float,
    val y: Float
)

data class PageAnnotations(
    val strokes: List<PdfStrokePath> = emptyList(),
    val notes: List<PdfTextNote> = emptyList(),
    val textBoxes: List<PdfTextBoxItem> = emptyList(),
    val shapes: List<PdfShapeItem> = emptyList(),
    val stamps: List<PdfStampItem> = emptyList()
) {
    fun isEmpty(): Boolean =
        strokes.isEmpty() && notes.isEmpty() && textBoxes.isEmpty() && shapes.isEmpty() && stamps.isEmpty()
}

// ==================== Compression ====================

enum class CompressionLevel(
    val titleAr: String,
    val descAr: String,
    val maxDimensionPx: Int,
    val jpegQuality: Float,
    val estimatedRatio: Float
) {
    LIGHT("ضغط خفيف — أعلى جودة للطباعة", "يحافظ على حدة الخطوط والرسوم مع تنظيف الكائنات غير المستخدمة", 2400, 0.88f, 0.72f),
    BALANCED("ضغط متوازن — موصى به للدراسة", "أفضل توازن بين وضوح المذكرات وتوفير مساحة التخزين", 1600, 0.72f, 0.45f),
    MAX("أقصى ضغط — أصغر مساحة تخزين", "أصغر حجم ممكن للمشاركة السريعة عبر واتساب وتيليجرام", 1100, 0.48f, 0.25f)
}

data class CompressionResult(
    val outputFilePath: String,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val wasKeptOriginal: Boolean,
    val pageCount: Int
) {
    val savedPercent: Int
        get() = if (originalSizeBytes <= 0L || compressedSizeBytes >= originalSizeBytes) 0
        else (((originalSizeBytes - compressedSizeBytes).toDouble() / originalSizeBytes.toDouble()) * 100.0).toInt()
}

// ==================== Organize / Merge / Split ====================

data class OrganizePageItem(
    val uid: String,
    val sourcePageIndex: Int, // -1 = blank, -2 = ruled study sheet
    val rotationDegrees: Int = 0,
    val label: String
)

enum class PdfExportFormat(val titleAr: String, val ext: String) {
    JPG("صور JPG مضغوطة", "jpg"),
    PNG("صور PNG عالية الدقة", "png"),
    BW_PRINT("أبيض وأسود اقتصادي للطباعة", "jpg")
}

// ==================== Security & Metadata ====================

data class PdfMetadataInfo(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = ""
)

data class SecurityPermissionsConfig(
    val allowPrint: Boolean = true,
    val allowCopy: Boolean = true,
    val allowModify: Boolean = false
)

data class MergeInputDoc(
    val id: String,
    val uri: Uri,
    val title: String,
    val sizeBytes: Long,
    val pageCount: Int
)
