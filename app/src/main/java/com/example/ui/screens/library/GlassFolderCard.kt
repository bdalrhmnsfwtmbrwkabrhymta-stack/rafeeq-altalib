package com.example.ui.screens.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Custom Folder Back Shape with Protruding Top Tab (لسان علوي بارز للمجلد).
 * Automatically adapts tab side to RTL/LTR layoutDirection.
 */
private val FolderBackShape: Shape = GenericShape { size, layoutDirection ->
    val w = size.width
    val h = size.height
    val r = (w * 0.085f).coerceAtMost(24f)
    val tabHeight = h * 0.14f
    val tabWidth = w * 0.44f
    val slope = w * 0.10f

    if (layoutDirection == LayoutDirection.Rtl) {
        // Tab on Top-Right
        moveTo(0f, tabHeight + r)
        quadraticTo(0f, tabHeight, r, tabHeight)
        lineTo(w - tabWidth - slope, tabHeight)
        cubicTo(
            w - tabWidth - slope * 0.45f, tabHeight,
            w - tabWidth - slope * 0.35f, 0f,
            w - tabWidth, 0f
        )
        lineTo(w - r, 0f)
        quadraticTo(w, 0f, w, r)
        lineTo(w, h - r)
        quadraticTo(w, h, w - r, h)
        lineTo(r, h)
        quadraticTo(0f, h, 0f, h - r)
        close()
    } else {
        // Tab on Top-Left
        moveTo(0f, r)
        quadraticTo(0f, 0f, r, 0f)
        lineTo(tabWidth, 0f)
        cubicTo(
            tabWidth + slope * 0.35f, 0f,
            tabWidth + slope * 0.45f, tabHeight,
            tabWidth + slope, tabHeight
        )
        lineTo(w - r, tabHeight)
        quadraticTo(w, tabHeight, w, tabHeight + r)
        lineTo(w, h - r)
        quadraticTo(w, h, w - r, h)
        lineTo(r, h)
        quadraticTo(0f, h, 0f, h - r)
        close()
    }
}

/**
 * Custom Stepped Front Frosted Glass Pocket Path (الجيب الزجاجي الأمامي الشفاف).
 * Matches the dipped pocket contour seen in Image 1 ("My Files" & "Archives").
 */
private fun buildFrontPocketPath(
    w: Float,
    h: Float,
    layoutDirection: LayoutDirection
): Path {
    val r = (w * 0.085f).coerceAtMost(24f)
    val pocketTopHigh = h * 0.23f
    val pocketTopLow = h * 0.34f
    val highSpan = w * 0.36f
    val slope = w * 0.12f

    return Path().apply {
        if (layoutDirection == LayoutDirection.Rtl) {
            // High lip on the LEFT, dipping down toward RIGHT (where the back tab protrudes)
            moveTo(0f, pocketTopHigh + r * 0.7f)
            quadraticTo(0f, pocketTopHigh, r * 0.7f, pocketTopHigh)
            lineTo(highSpan, pocketTopHigh)
            cubicTo(
                highSpan + slope * 0.45f, pocketTopHigh,
                highSpan + slope * 0.55f, pocketTopLow,
                highSpan + slope, pocketTopLow
            )
            lineTo(w - r * 0.7f, pocketTopLow)
            quadraticTo(w, pocketTopLow, w, pocketTopLow + r * 0.7f)
            lineTo(w, h - r)
            quadraticTo(w, h, w - r, h)
            lineTo(r, h)
            quadraticTo(0f, h, 0f, h - r)
            close()
        } else {
            // High lip on the RIGHT, dipping down toward LEFT
            moveTo(0f, pocketTopLow + r * 0.7f)
            quadraticTo(0f, pocketTopLow, r * 0.7f, pocketTopLow)
            lineTo(w - highSpan - slope, pocketTopLow)
            cubicTo(
                w - highSpan - slope * 0.55f, pocketTopLow,
                w - highSpan - slope * 0.45f, pocketTopHigh,
                w - highSpan, pocketTopHigh
            )
            lineTo(w - r * 0.7f, pocketTopHigh)
            quadraticTo(w, pocketTopHigh, w, pocketTopHigh + r * 0.7f)
            lineTo(w, h - r)
            quadraticTo(w, h, w - r, h)
            lineTo(r, h)
            quadraticTo(0f, h, 0f, h - r)
            close()
        }
    }
}

private val FolderFrontPocketShape: Shape = GenericShape { size, layoutDirection ->
    addPath(buildFrontPocketPath(size.width, size.height, layoutDirection))
}

/**
 * 3D Glassmorphism Folder Card matching Image 1:
 * - Aspect Ratio 4:3
 * - Border-radius 22dp + protruding top folder tab
 * - Flowing multi-color gradient waves + optional peeking white paper sheet behind frosted glass
 * - Frosted translucent glass pocket with thin luminous specular border & soft shadow
 * - Bottom-corner rounded-square / pill badge with subject icon, file count, or (+) action
 */
@Composable
fun GlassFolderCard(
    subject: SubjectFolderSpec,
    studyType: StudyType,
    bookCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudyLibraryPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 450f),
        label = "folderPressScale"
    )

    val waveColors = if (palette.isDark) subject.waveColorsDark else subject.waveColorsLight
    val displayTitle = if (studyType == StudyType.LANGUAGES) subject.nameLanguages else subject.nameAr
    val secondaryTitle = if (studyType == StudyType.LANGUAGES) subject.nameAr else "${bookCount} ملف موثق"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .scale(scale)
            .shadow(
                elevation = if (palette.isDark) 16.dp else 10.dp,
                shape = FolderBackShape,
                ambientColor = waveColors.first().copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = if (palette.isDark) 0.65f else 0.22f)
            )
            .clip(FolderBackShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = StudyLibraryColors.AccentYellow),
                onClick = onClick
            )
            .testTag("folder_card_${subject.id}")
    ) {
        // 1. Back Layer: Rich Fluid Gradient Waves (glowing behind the translucent glass pocket)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(12.dp)
        ) {
            val w = size.width
            val h = size.height

            // Base rich backdrop
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(waveColors[0], waveColors[1], waveColors[2]),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            // Organic sweeping wave 1
            val wave1 = Path().apply {
                moveTo(0f, h * 0.55f)
                cubicTo(w * 0.28f, h * 0.20f, w * 0.65f, h * 0.80f, w, h * 0.32f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = wave1,
                brush = Brush.linearGradient(
                    colors = listOf(
                        waveColors[2].copy(alpha = 0.92f),
                        waveColors[1].copy(alpha = 0.78f)
                    ),
                    start = Offset(0f, h * 0.2f),
                    end = Offset(w, h)
                )
            )

            // Organic sweeping wave 2 (luminous highlight ribbon)
            val wave2 = Path().apply {
                moveTo(0f, h * 0.30f)
                cubicTo(w * 0.35f, h * 0.65f, w * 0.65f, h * 0.12f, w, h * 0.48f)
                lineTo(w, h * 0.75f)
                cubicTo(w * 0.60f, h * 0.45f, w * 0.30f, h * 0.85f, 0f, h * 0.60f)
                close()
            }
            drawPath(
                path = wave2,
                color = Color.White.copy(alpha = if (palette.isDark) 0.16f else 0.34f)
            )
        }

        // 2. Optional Peeking White Document Sheet (like "Archives" folder in Image 1)
        if (subject.visualVariant == FolderVisualVariant.PAPER_ARCHIVE) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 18.dp, end = 18.dp)
                    .fillMaxWidth(0.76f)
                    .fillMaxHeight(0.62f)
                    .shadow(4.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFFF1F5F9)
                            )
                        )
                    )
                    .border(0.8.dp, Color.White, RoundedCornerShape(14.dp))
            ) {
                // Subtle document rule lines on peeking sheet
                Column(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(waveColors[0].copy(alpha = 0.28f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFCBD5E1).copy(alpha = 0.6f))
                    )
                }
            }
        }

        // 3. Front Frosted Glass Pocket with Luminous Thin Specular Rim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(FolderFrontPocketShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.glassTop,
                            palette.glassBottom
                        )
                    )
                )
                .drawBehind {
                    val pocketPath = buildFrontPocketPath(size.width, size.height, layoutDirection)
                    // Thin luminous specular border along pocket edge
                    drawPath(
                        path = pocketPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                palette.glassInnerRim,
                                palette.glassBorder,
                                palette.glassBorder.copy(alpha = 0.35f)
                            )
                        ),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
        ) {
            // Top-pocket tiny yellow accent pill indicator (Poster Image 2 touch)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 42.dp, start = 14.dp)
                    .width(24.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(StudyLibraryColors.AccentYellow)
            )

            // Bottom Content Row inside Frosted Pocket: Subject Title + Corner Badge
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = displayTitle,
                        fontFamily = StudyLibraryTypography.DisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        lineHeight = 19.sp,
                        color = palette.inkPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = secondaryTitle,
                        fontFamily = StudyLibraryTypography.BodyFont,
                        fontSize = 10.5.sp,
                        color = palette.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Bottom-Corner Badge (Squircle or Pill matching Image 1: '+' dark squircle / 'ooo' pill / subject icon)
                when (subject.badgeType) {
                    "plus" -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.badgeBg,
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = subject.icon,
                                    contentDescription = displayTitle,
                                    tint = palette.badgeContent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    "dots" -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.pillBadgeBg.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, palette.pillBadgeBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = subject.icon,
                                    contentDescription = displayTitle,
                                    tint = palette.inkPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (bookCount > 0) "$bookCount" else "فتح",
                                    fontFamily = StudyLibraryTypography.BodyFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = palette.inkPrimary
                                )
                            }
                        }
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (palette.isDark) Color.White.copy(alpha = 0.14f) else Color(0xFF0F172A).copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, palette.glassBorder),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = subject.icon,
                                    contentDescription = displayTitle,
                                    tint = if (palette.isDark) StudyLibraryColors.AccentYellow else palette.inkPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Verified Book Card rendered in Frosted Glassmorphism style.
 */
@Composable
fun GlassBookCard(
    book: LibraryBookRecord,
    gradeLabel: String,
    onOpenPdf: () -> Unit,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudyLibraryPalette.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (palette.isDark) palette.surfaceCard.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.88f),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, palette.glassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Official cover if available, otherwise clean glass placeholder
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0EA5E9).copy(alpha = 0.20f), Color(0xFFF97316).copy(alpha = 0.22f))
                        )
                    )
                    .border(1.dp, palette.glassBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!book.officialCoverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = book.officialCoverUrl,
                        contentDescription = "غلاف ${book.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "PDF",
                            fontFamily = StudyLibraryTypography.DisplayFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = palette.inkPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = StudyLibraryColors.AccentYellow
                    ) {
                        Text(
                            text = book.bookType,
                            fontFamily = StudyLibraryTypography.BodyFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = StudyLibraryColors.AccentYellowInk,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.pillBadgeBg
                    ) {
                        Text(
                            text = book.term,
                            fontFamily = StudyLibraryTypography.BodyFont,
                            fontSize = 10.sp,
                            color = palette.inkSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = book.title,
                    fontFamily = StudyLibraryTypography.DisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = palette.inkPrimary
                )

                Text(
                    text = "$gradeLabel • ${book.subjectName} • ${book.academicYear}",
                    fontFamily = StudyLibraryTypography.BodyFont,
                    fontSize = 11.sp,
                    color = palette.inkSecondary
                )

                Text(
                    text = "المصدر: ${book.sourceName}",
                    fontFamily = StudyLibraryTypography.BodyFont,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (palette.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenPdf,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.badgeBg,
                            contentColor = palette.badgeContent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "فتح PDF",
                            fontFamily = StudyLibraryTypography.BodyFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenSource,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.glassBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "تنزيل / المصدر الرسمي",
                            fontFamily = StudyLibraryTypography.BodyFont,
                            fontSize = 11.sp,
                            color = palette.inkPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Glassmorphism Empty State Folder/Card when no verified individual PDF links are populated yet.
 * Never displays fabricated PDF URLs or fake covers; links directly to the official Egyptian MOE library.
 */
@Composable
fun GlassEmptyStateCard(
    title: String,
    subtitle: String,
    officialCatalogUrl: String,
    isFilterActive: Boolean,
    onOpenOfficialMinistryLibrary: (String) -> Unit,
    onOpenLocalDocuments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudyLibraryPalette.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (palette.isDark) palette.surfaceCard.copy(alpha = 0.88f)
                else Color.White.copy(alpha = 0.90f)
            )
            .border(1.2.dp, palette.glassBorder, RoundedCornerShape(22.dp))
    ) {
        // Subtle gradient glow accent strip at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF0EA5E9), StudyLibraryColors.AccentYellow, Color(0xFFF97316))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(StudyLibraryColors.AccentYellow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = StudyLibraryColors.AccentYellowInk,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                fontFamily = StudyLibraryTypography.DisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = palette.inkPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                fontFamily = StudyLibraryTypography.BodyFont,
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
                color = palette.inkSecondary
            )

            if (!isFilterActive) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (palette.isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = "المصدر المعتمد: وزارة التربية والتعليم المصرية — جميع الروابط تُضاف بعد توثيقها رسمياً.",
                        fontFamily = StudyLibraryTypography.BodyFont,
                        fontSize = 11.sp,
                        color = palette.inkSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onOpenOfficialMinistryLibrary(officialCatalogUrl) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.badgeBg,
                    contentColor = palette.badgeContent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "فتح المكتبة الإلكترونية لوزارة التربية والتعليم",
                    fontFamily = StudyLibraryTypography.BodyFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenLocalDocuments,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.glassBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = palette.inkPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "فتح ملفاتي ومستنداتي المحفوظة على الهاتف",
                    fontFamily = StudyLibraryTypography.BodyFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = palette.inkPrimary
                )
            }
        }
    }
}
