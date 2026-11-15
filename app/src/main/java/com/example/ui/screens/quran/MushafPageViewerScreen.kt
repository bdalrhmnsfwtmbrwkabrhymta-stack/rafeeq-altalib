package com.example.ui.screens.quran

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.quran.JuzInfo
import com.example.data.quran.MushafPage
import com.example.data.quran.MushafPagePagination
import com.example.data.quran.SurahInfo
import com.example.ui.components.AutoSizeText
import com.example.ui.screens.formatArabicDigits
import com.example.ui.theme.AmiriQuranFontFamily
import com.example.ui.theme.ReadexProFontFamily
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafPageViewerScreen(
    pages: List<MushafPage>,
    allSurahInfoList: List<SurahInfo>,
    allJuzList: List<JuzInfo>,
    initialPageNumber: Int,
    bookmarkedSurahNumber: Int,
    bookmarkedAyahNumber: Int,
    preferredFontSizeSp: Int,
    isEnglish: Boolean,
    currentPlayingTitle: String?,
    isAudioPlaying: Boolean,
    isAudioLoading: Boolean,
    audioProgressMs: Int,
    audioDurationMs: Int,
    onPlaySurah: (surahNumber: Int, surahNameAr: String) -> Unit,
    onToggleAudioPlayPause: () -> Unit,
    onStopAudio: () -> Unit,
    onSaveBookmark: (surahNumber: Int, surahNameAr: String, ayahNumber: Int) -> Unit,
    onMarkPageRead: () -> Unit,
    onExit: () -> Unit
) {
    BackHandler {
        onExit()
    }

    val safeTotalPages = pages.size.coerceAtLeast(1)
    val initialIndex = (initialPageNumber - 1).coerceIn(0, safeTotalPages - 1)
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { safeTotalPages })
    val coroutineScope = rememberCoroutineScope()

    var targetScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(stiffness = 500f),
        label = "mushafZoomScale"
    )

    var showIndexSheet by remember { mutableStateOf(false) }
    var showAudioBar by remember { mutableStateOf(false) }
    var bookmarkSavedToast by remember { mutableStateOf(false) }

    val currentPage = pages.getOrNull(pagerState.currentPage)
    val currentDisplayPageNum = currentPage?.pageNumber ?: (pagerState.currentPage + 1)
    val currentSurah = currentPage?.primarySurah ?: allSurahInfoList.firstOrNull()
    val currentJuzNum = currentPage?.juzNumber ?: 1

    val isCurrentPageBookmarked = currentPage?.segments?.any { seg ->
        seg.surahInfo.number == bookmarkedSurahNumber &&
            seg.ayahs.any { it.numberInSurah == bookmarkedAyahNumber }
    } == true

    // Reset zoom gently when switching pages via index sheet
    fun jumpToPage(pageNumber: Int) {
        val targetIdx = (pageNumber - 1).coerceIn(0, safeTotalPages - 1)
        targetScale = 1f
        panOffset = Offset.Zero
        coroutineScope.launch {
            pagerState.animateScrollToPage(targetIdx)
        }
    }

    LaunchedEffect(bookmarkSavedToast) {
        if (bookmarkSavedToast) {
            kotlinx.coroutines.delay(1800)
            bookmarkSavedToast = false
        }
    }

    LaunchedEffect(currentPlayingTitle) {
        if (currentPlayingTitle != null) {
            showAudioBar = true
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("mushaf_viewer_root")
        ) {
            // Sleek White Top Bar matching reference mockups 2 & 3
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onExit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("mushaf_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEnglish) "Back" else "رجوع",
                            tint = QuranPalette.NavyInk,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    val headerText = if (currentSurah != null) {
                        "سورة ${currentSurah.nameAr} • الجزء ${formatArabicDigits(currentJuzNum)} • صفحة ${formatArabicDigits(currentDisplayPageNum)}"
                    } else {
                        "المصحف الشريف • صفحة ${formatArabicDigits(currentDisplayPageNum)}"
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        AutoSizeText(
                            text = headerText,
                            maxFontSize = 15.5.sp,
                            minFontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ReadexProFontFamily,
                            color = QuranPalette.NavyInk,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mushaf_top_title")
                        )
                    }

                    IconButton(
                        onClick = { showIndexSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("mushaf_index_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = if (isEnglish) "Index & Page Jump" else "فهرس السور والأجزاء والصفحات",
                            tint = QuranPalette.RoyalBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val firstSegment = currentPage?.segments?.firstOrNull()
                            val firstAyah = firstSegment?.ayahs?.firstOrNull()
                            if (firstSegment != null && firstAyah != null) {
                                onSaveBookmark(
                                    firstSegment.surahInfo.number,
                                    firstSegment.surahInfo.nameAr,
                                    firstAyah.numberInSurah
                                )
                                bookmarkSavedToast = true
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("mushaf_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (isCurrentPageBookmarked || bookmarkSavedToast) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isEnglish) "Bookmark Page" else "حفظ علامة مرجعية",
                            tint = QuranPalette.RoyalBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (currentPlayingTitle != null) {
                                showAudioBar = !showAudioBar
                            } else if (currentSurah != null) {
                                showAudioBar = true
                                onPlaySurah(currentSurah.number, currentSurah.nameAr)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("mushaf_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = if (isEnglish) "Play Surah Audio" else "تشغيل التلاوة الصوتية",
                            tint = QuranPalette.RoyalBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Optional collapsible mini audio player directly inside Mushaf reader
            AnimatedVisibility(
                visible = showAudioBar && (currentPlayingTitle != null || isAudioLoading),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                QuranMiniAudioPlayer(
                    title = currentPlayingTitle ?: "سورة ${currentSurah?.nameAr ?: "الفاتحة"}",
                    isPlaying = isAudioPlaying,
                    isLoading = isAudioLoading,
                    progressMs = audioProgressMs,
                    durationMs = audioDurationMs,
                    onTogglePlayPause = onToggleAudioPlayPause,
                    onStop = {
                        onStopAudio()
                        showAudioBar = false
                    }
                )
            }

            // Main Interactive Sequential 604-Page Mushaf Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .onSizeChanged { viewportSize = it }
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = targetScale <= 1.02f,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("mushaf_horizontal_pager")
                ) { pageIndex ->
                    val pageData = pages.getOrNull(pageIndex)
                    if (pageData != null) {
                        val isActivePage = pageIndex == pagerState.currentPage
                        val pageScale = if (isActivePage) animatedScale else 1f
                        val pageOffset = if (isActivePage) panOffset else Offset.Zero

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(isActivePage) {
                                    if (!isActivePage) return@pointerInput
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (targetScale > 1.05f) {
                                                targetScale = 1f
                                                panOffset = Offset.Zero
                                            } else {
                                                targetScale = 1.8f
                                            }
                                        }
                                    )
                                }
                                .pointerInput(isActivePage) {
                                    if (!isActivePage) return@pointerInput
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val event = awaitPointerEvent()
                                            val pointerCount = event.changes.count { it.pressed }
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()

                                            val isPinching = pointerCount > 1 && abs(zoomChange - 1f) > 0.002f
                                            val isZoomedPan = targetScale > 1.02f

                                            if (isPinching || (pointerCount > 1 && event.calculateCentroidSize() > 0f)) {
                                                val nextScale = (targetScale * zoomChange).coerceIn(1f, 3f)
                                                targetScale = nextScale
                                                if (nextScale <= 1.02f) {
                                                    panOffset = Offset.Zero
                                                } else {
                                                    panOffset = clampPanOffset(
                                                        current = panOffset + panChange,
                                                        scale = nextScale,
                                                        viewport = viewportSize
                                                    )
                                                }
                                                event.changes.forEach {
                                                    if (it.positionChanged()) it.consume()
                                                }
                                            } else if (isZoomedPan) {
                                                panOffset = clampPanOffset(
                                                    current = panOffset + panChange,
                                                    scale = targetScale,
                                                    viewport = viewportSize
                                                )
                                                event.changes.forEach {
                                                    if (it.positionChanged()) it.consume()
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = pageScale
                                    scaleY = pageScale
                                    translationX = pageOffset.x
                                    translationY = pageOffset.y
                                }
                        ) {
                            MushafSinglePageCanvas(
                                page = pageData,
                                preferredFontSizeSp = preferredFontSizeSp
                            )
                        }
                    }
                }

                // Bottom-center sequential page capsule ("صفحة ١ من ٦٠٤")
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showIndexSheet = true }
                        .testTag("mushaf_page_indicator"),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, QuranPalette.Border),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "صفحة ${formatArabicDigits(currentDisplayPageNum)} من ٦٠٤",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = QuranPalette.NavyInk,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }

                // Bottom-end (visual left in RTL) zoom percentage pill ("100%" / "125%", tap resets to 100%)
                val zoomPercent = (targetScale * 100f).roundToInt()
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 14.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            targetScale = 1f
                            panOffset = Offset.Zero
                        }
                        .testTag("mushaf_zoom_badge"),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.96f),
                    border = BorderStroke(
                        1.dp,
                        if (targetScale > 1.02f) QuranPalette.RoyalBlue else QuranPalette.Border
                    ),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "$zoomPercent%",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = if (targetScale > 1.02f) QuranPalette.RoyalBlue else QuranPalette.NavyInk,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                // Bookmark saved feedback banner
                androidx.compose.animation.AnimatedVisibility(
                    visible = bookmarkSavedToast,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = QuranPalette.RoyalBlue,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = "تم حفظ العلامة المرجعية في صفحة ${formatArabicDigits(currentDisplayPageNum)}",
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Quick Navigation Index Sheet: Jump by Page (1..604), Surah (114), or Juz (30) + Mark Page Read
        if (showIndexSheet) {
            MushafQuickIndexBottomSheet(
                currentPageNumber = currentDisplayPageNum,
                allSurahInfoList = allSurahInfoList,
                allJuzList = allJuzList,
                onJumpToPage = { pageNum ->
                    showIndexSheet = false
                    jumpToPage(pageNum)
                },
                onMarkPageRead = {
                    onMarkPageRead()
                    showIndexSheet = false
                },
                onDismiss = { showIndexSheet = false }
            )
        }
    }
}

@Composable
private fun MushafSinglePageCanvas(
    page: MushafPage,
    preferredFontSizeSp: Int
) {
    val baseFontSp = preferredFontSizeSp.coerceIn(19, 27).toFloat().coerceAtLeast(22f)

    MushafOrnamentalFrame(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("mushaf_page_canvas_${page.pageNumber}")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            val isFatihahPage = page.pageNumber == 1
            val textMeasurer = rememberTextMeasurer()
            val density = androidx.compose.ui.platform.LocalDensity.current
            val reservedBannerAndBottomPx = with(density) { (54.dp + 10.dp + 32.dp + 8.dp).roundToPx() }

            // For Page 1 (Al-Fatihah), automatically ensure ALL 7 ayahs fit completely inside
            // the viewport height without scrolling or clipping.
            val (page1FontSp, fitsInsideViewport) = remember(
                page.pageNumber,
                constraints.maxWidth,
                constraints.maxHeight,
                baseFontSp,
                reservedBannerAndBottomPx
            ) {
                if (!isFatihahPage) {
                    baseFontSp.coerceAtLeast(24f) to false
                } else {
                    val sampleText = buildAnnotatedString {
                        page.segments.forEach { seg ->
                            seg.ayahs.forEach { ayah ->
                                append(ayah.text)
                                append(" \u06DD${formatArabicDigits(ayah.numberInSurah)} ")
                            }
                        }
                    }
                    val availHeightPx = (constraints.maxHeight - reservedBannerAndBottomPx).coerceAtLeast(100)
                    var candidateSp = 24f
                    var fits = false
                    while (candidateSp >= 13f) {
                        val measured = textMeasurer.measure(
                            text = sampleText,
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = AmiriQuranFontFamily,
                                fontSize = candidateSp.sp,
                                lineHeight = (candidateSp * 1.85f).sp,
                                textAlign = TextAlign.Center
                            ),
                            constraints = Constraints(maxWidth = (constraints.maxWidth - 4).coerceAtLeast(80))
                        )
                        if (measured.size.height <= availHeightPx) {
                            fits = true
                            break
                        }
                        candidateSp -= 1f
                    }
                    candidateSp.coerceAtLeast(13f) to fits
                }
            }

            val effectiveFontSp = if (isFatihahPage) page1FontSp else 24f.coerceAtLeast(baseFontSp)
            val lineHeightSp = effectiveFontSp * if (isFatihahPage) 1.82f else 1.95f
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isFatihahPage && fitsInsideViewport) Modifier else Modifier.verticalScroll(scrollState)
                    )
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (isFatihahPage && fitsInsideViewport) Arrangement.Center else Arrangement.Top
            ) {
                page.segments.forEachIndexed { segIndex, segment ->
                    if (segIndex > 0) {
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    if (segment.startsSurah) {
                        MushafSurahHeaderBanner(
                            surahNameAr = segment.surahInfo.nameAr,
                            surahNumber = segment.surahInfo.number,
                            revelationType = segment.surahInfo.revelationType,
                            ayahCount = segment.surahInfo.numberOfAyahs
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (segment.showBismillahHeader) {
                        Text(
                            text = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                            fontFamily = AmiriQuranFontFamily,
                            fontSize = (effectiveFontSp - 1f).coerceAtLeast(21f).sp,
                            lineHeight = (effectiveFontSp * 1.75f).sp,
                            color = QuranPalette.NavyInk,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }

                    // Build continuous Uthmani verse paragraph with inline ornate gold ayah medallions
                    val inlineMap = remember(segment.surahInfo.number, segment.ayahs, effectiveFontSp) {
                        val medallionSp = (effectiveFontSp * 1.05f).sp
                        segment.ayahs.associate { ayah ->
                            val id = "ayah_${segment.surahInfo.number}_${ayah.numberInSurah}"
                            id to InlineTextContent(
                                Placeholder(
                                    width = medallionSp,
                                    height = medallionSp,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                                )
                            ) {
                                AyahEndMedallion(ayahNumber = ayah.numberInSurah)
                            }
                        }
                    }

                    val annotatedVerses = remember(segment.surahInfo.number, segment.ayahs) {
                        buildAnnotatedString {
                            segment.ayahs.forEachIndexed { index, ayah ->
                                append(ayah.text)
                                append(" ")
                                appendInlineContent(
                                    id = "ayah_${segment.surahInfo.number}_${ayah.numberInSurah}",
                                    alternateText = "۝${formatArabicDigits(ayah.numberInSurah)}"
                                )
                                if (index < segment.ayahs.lastIndex) {
                                    append(" ")
                                }
                            }
                        }
                    }

                    Text(
                        text = annotatedVerses,
                        inlineContent = inlineMap,
                        fontFamily = AmiriQuranFontFamily,
                        fontSize = effectiveFontSp.sp,
                        lineHeight = lineHeightSp.sp,
                        color = QuranPalette.NavyInk,
                        textAlign = if (isFatihahPage || segment.ayahs.size <= 3) TextAlign.Center else TextAlign.Justify,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mushaf_segment_${segment.surahInfo.number}")
                    )
                }
            }
        }
    }
}

private fun clampPanOffset(
    current: Offset,
    scale: Float,
    viewport: IntSize
): Offset {
    if (scale <= 1.02f || viewport.width <= 0 || viewport.height <= 0) {
        return Offset.Zero
    }
    val maxX = ((viewport.width * (scale - 1f)) / 2f).coerceAtLeast(0f)
    val maxY = ((viewport.height * (scale - 1f)) / 2f).coerceAtLeast(0f)
    return Offset(
        x = current.x.coerceIn(-maxX, maxX),
        y = current.y.coerceIn(-maxY, maxY)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MushafQuickIndexBottomSheet(
    currentPageNumber: Int,
    allSurahInfoList: List<SurahInfo>,
    allJuzList: List<JuzInfo>,
    onJumpToPage: (Int) -> Unit,
    onMarkPageRead: () -> Unit,
    onDismiss: () -> Unit
) {
    var sheetTab by remember { mutableIntStateOf(0) } // 0 = Surahs, 1 = Juz, 2 = Page Slider
    var sliderPage by remember { mutableFloatStateOf(currentPageNumber.toFloat()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "فهرس المصحف الشريف",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = QuranPalette.NavyInk
                )

                TextButton(
                    onClick = onMarkPageRead,
                    colors = ButtonDefaults.textButtonColors(contentColor = QuranPalette.RoyalBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+ تسجيل قراءة صفحة",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("السور (١١٤)", "الأجزاء (٣٠)", "رقم الصفحة").forEachIndexed { idx, label ->
                    val active = sheetTab == idx
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { sheetTab = idx },
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) QuranPalette.RoyalBlue else QuranPalette.Background,
                        border = if (active) null else BorderStroke(1.dp, QuranPalette.Border)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(
                                text = label,
                                fontFamily = ReadexProFontFamily,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.5.sp,
                                color = if (active) Color.White else QuranPalette.SlateBody,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (sheetTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allSurahInfoList, key = { it.number }) { surah ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onJumpToPage(surah.page) },
                                shape = RoundedCornerShape(14.dp),
                                color = QuranPalette.Background,
                                border = BorderStroke(1.dp, QuranPalette.Border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(QuranPalette.RoyalBlue),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = formatArabicDigits(surah.number),
                                                fontFamily = ReadexProFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "سورة ${surah.nameAr}",
                                                fontFamily = ReadexProFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.5.sp,
                                                color = QuranPalette.NavyInk
                                            )
                                            Text(
                                                text = "${formatArabicDigits(surah.numberOfAyahs)} آيات • ${surah.revelationType}",
                                                fontFamily = ReadexProFontFamily,
                                                fontSize = 11.5.sp,
                                                color = QuranPalette.SlateMuted
                                            )
                                        }
                                    }

                                    Text(
                                        text = "ص ${formatArabicDigits(surah.page)}",
                                        fontFamily = ReadexProFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = QuranPalette.RoyalBlue
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allJuzList, key = { it.number }) { juz ->
                            val range = MushafPagePagination.getJuzPageRange(juz.number, allJuzList)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onJumpToPage(juz.startPage) },
                                shape = RoundedCornerShape(14.dp),
                                color = QuranPalette.Background,
                                border = BorderStroke(1.dp, QuranPalette.Border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(QuranPalette.RoyalBlue),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = formatArabicDigits(juz.number),
                                                fontFamily = ReadexProFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = juz.nameAr,
                                                fontFamily = ReadexProFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = QuranPalette.NavyInk
                                            )
                                            Text(
                                                text = "صفحة ${formatArabicDigits(range.first)} إلى ${formatArabicDigits(range.last)}",
                                                fontFamily = ReadexProFontFamily,
                                                fontSize = 11.5.sp,
                                                color = QuranPalette.SlateMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "الانتقال إلى صفحة ${formatArabicDigits(sliderPage.roundToInt())} من ٦٠٤",
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = QuranPalette.RoyalBlue
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Slider(
                            value = sliderPage,
                            onValueChange = { sliderPage = it },
                            valueRange = 1f..604f,
                            colors = SliderDefaults.colors(
                                thumbColor = QuranPalette.RoyalBlue,
                                activeTrackColor = QuranPalette.RoyalBlue,
                                inactiveTrackColor = QuranPalette.RoyalBlueTint
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onJumpToPage(sliderPage.roundToInt()) },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = QuranPalette.RoyalBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = "فتح الصفحة ${formatArabicDigits(sliderPage.roundToInt())}",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
