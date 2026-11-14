package com.example.pdfstudio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * الخط الأساسي لواجهات وأزرار وقوائم استوديو الـ PDF:
 * IBM Plex Sans Arabic (مثبت محلياً في res/font لضمان الموثوقية بدون إنترنت).
 */
val PdfUiFontFamily: FontFamily = FontFamily(
    Font(resId = R.font.ibm_plex_sans_arabic, weight = FontWeight.Normal),
    Font(resId = R.font.ibm_plex_sans_arabic, weight = FontWeight.Medium),
    Font(resId = R.font.ibm_plex_sans_arabic, weight = FontWeight.SemiBold),
    Font(resId = R.font.ibm_plex_sans_arabic, weight = FontWeight.Bold)
)

/**
 * خط القراءة الممتدة والنصوص المستخرجة:
 * Noto Naskh Arabic (مثبت محلياً في res/font).
 */
val PdfReadingFontFamily: FontFamily = FontFamily(
    Font(resId = R.font.noto_naskh_arabic, weight = FontWeight.Normal),
    Font(resId = R.font.noto_naskh_arabic, weight = FontWeight.Medium),
    Font(resId = R.font.noto_naskh_arabic, weight = FontWeight.Bold)
)

/**
 * لوحة ألوان استوديو PDF المعتمدة من دليل المواصفات الشاملة:
 * - خلفية نقية #F8FAFC وبطاقات #FFFFFF بحواف 20.dp وحدود رفيعة ناعمة
 * - الأزرق الملكي #1A6FE3 للبطاقة الرئيسية والتبويبات النشطة
 * - تكيّف تلقائي مريح عند تفعيل الوضع الداكن في التطبيق
 */
object PdfStudioColors {
    val RoyalBlue = Color(0xFF1A6FE3)
    val RoyalBlueDark = Color(0xFF1458B8)
    val PureWhite = Color(0xFFFFFFFF)
    val CleanCanvas = Color(0xFFF8FAFC)
    val SoftBorder = Color(0xFFE2E8F0)
    val SlateTitle = Color(0xFF0F172A)
    val SlateSubtitle = Color(0xFF64748B)

    // ألوان أيقونات الأدوات الهادئة
    val AccentPurple = Color(0xFF7C3AED)
    val AccentMint = Color(0xFF10B981)
    val AccentOrange = Color(0xFFF59E0B)
    val AccentCyan = Color(0xFF0EA5E9)
    val AccentPink = Color(0xFFEC4899)
    val AccentTeal = Color(0xFF14B8A6)
    val AccentIndigo = Color(0xFF4F46E5)

    @Composable
    fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.35f || isSystemInDarkTheme() && MaterialTheme.colorScheme.surface.luminance() < 0.35f

    @Composable
    fun screenBackground(): Color = if (isDark()) MaterialTheme.colorScheme.background else CleanCanvas

    @Composable
    fun cardSurface(): Color = if (isDark()) MaterialTheme.colorScheme.surface else PureWhite

    @Composable
    fun cardBorder(): Color = if (isDark()) MaterialTheme.colorScheme.outlineVariant else SoftBorder

    @Composable
    fun titleText(): Color = if (isDark()) MaterialTheme.colorScheme.onSurface else SlateTitle

    @Composable
    fun subtitleText(): Color = if (isDark()) MaterialTheme.colorScheme.onSurfaceVariant else SlateSubtitle
}

object PdfTypography {
    fun uiTitle(size: TextUnit = 20.sp, weight: FontWeight = FontWeight.Bold): TextStyle = TextStyle(
        fontFamily = PdfUiFontFamily,
        fontSize = size,
        fontWeight = weight,
        lineHeight = size * 1.35f
    )

    fun uiBody(size: TextUnit = 14.sp, weight: FontWeight = FontWeight.Normal): TextStyle = TextStyle(
        fontFamily = PdfUiFontFamily,
        fontSize = size,
        fontWeight = weight,
        lineHeight = size * 1.45f
    )

    fun uiLabel(size: TextUnit = 13.sp, weight: FontWeight = FontWeight.SemiBold): TextStyle = TextStyle(
        fontFamily = PdfUiFontFamily,
        fontSize = size,
        fontWeight = weight,
        lineHeight = size * 1.3f
    )

    fun readingBody(size: TextUnit = 18.sp): TextStyle = TextStyle(
        fontFamily = PdfReadingFontFamily,
        fontSize = size,
        fontWeight = FontWeight.Normal,
        lineHeight = size * 1.6f
    )
}

@Composable
fun PdfStudioTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    testTagBack: String = "pdf_studio_back_btn",
    transparentBackground: Boolean = false,
    centerTitle: Boolean = false,
    actions: @Composable () -> Unit = {}
) {
    Surface(
        color = if (transparentBackground) Color.Transparent else PdfStudioColors.cardSurface(),
        tonalElevation = if (transparentBackground) 0.dp else 1.dp,
        shadowElevation = if (transparentBackground) 0.dp else 2.dp
    ) {
        if (centerTitle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .minimumInteractiveComponentSize()
                        .testTag(testTagBack)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = PdfStudioColors.titleText()
                    )
                }
                Text(
                    text = title,
                    style = PdfTypography.uiTitle(20.sp, FontWeight.Bold),
                    color = PdfStudioColors.titleText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center)
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag(testTagBack)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = PdfStudioColors.titleText()
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = PdfTypography.uiTitle(22.sp, FontWeight.Bold),
                            color = PdfStudioColors.titleText(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = PdfTypography.uiBody(14.sp, FontWeight.Normal),
                                color = PdfStudioColors.subtitleText(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    actions()
                }
            }
        }
    }
}

/**
 * بطاقة الاستيراد الزرقاء ثلاثية الأبعاد (Stacked 3D Card) المطابقة للشكل (1).
 */
@Composable
fun BlueStackedImportCard(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    ) {
        // الطبقة الخلفية العلوية الثالثة
        Surface(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 28.dp)
                .offset(y = (-13).dp),
            shape = RoundedCornerShape(24.dp),
            color = PdfStudioColors.RoyalBlue.copy(alpha = 0.25f)
        ) {}

        // الطبقة الوسطى الثانية
        Surface(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 14.dp)
                .offset(y = (-7).dp),
            shape = RoundedCornerShape(24.dp),
            color = PdfStudioColors.RoyalBlue.copy(alpha = 0.52f)
        ) {}

        // البطاقة الأمامية الزرقاء الرئيسية #1A6FE3
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = PdfStudioColors.RoyalBlue,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(PdfStudioColors.RoyalBlue, PdfStudioColors.RoyalBlueDark)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, bottomEnd = 10.dp, topEnd = 18.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(width = 46.dp, height = 56.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(13.dp)
                                    .background(
                                        color = Color(0xFFDBEAFE),
                                        shape = RoundedCornerShape(bottomStart = 6.dp)
                                    )
                            )
                            Text(
                                text = "PDF",
                                style = PdfTypography.uiLabel(13.sp, FontWeight.ExtraBold),
                                color = PdfStudioColors.RoyalBlue,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "افتح ملف PDF",
                            style = PdfTypography.uiTitle(21.sp, FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "أو اختر أداة",
                            style = PdfTypography.uiBody(15.sp, FontWeight.Medium),
                            color = Color.White.copy(alpha = 0.90f)
                        )
                    }
                }

                Button(
                    onClick = onImportClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = PdfStudioColors.RoyalBlue
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("hub_open_pdf_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "استيراد ملف من الجهاز",
                        style = PdfTypography.uiTitle(15.sp, FontWeight.Bold)
                    )
                }
            }
        }
    }
}

/**
 * بطاقة بيضاء نظيفة بحواف 20.dp وحدود رفيعة متوافقة مع الهوية البصرية.
 */
@Composable
fun StackedDepthCard(
    modifier: Modifier = Modifier,
    accentColor: Color = PdfStudioColors.RoyalBlue,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PdfStudioColors.cardSurface(),
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, PdfStudioColors.cardBorder())
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
