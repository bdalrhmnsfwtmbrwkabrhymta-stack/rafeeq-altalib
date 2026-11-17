package com.hawas.study.rafid.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.rafid.data.BlockCategory
import com.hawas.study.rafid.engine.AdAndTrackerShield
import com.hawas.study.rafid.engine.SniffedMediaCandidate
import com.hawas.study.rafid.models.RafidBookmarkEntity
import com.hawas.study.rafid.models.RafidConfigState
import com.hawas.study.rafid.ui.theme.LocalRafidIsEnglish
import com.hawas.study.rafid.ui.theme.LocalRafidPalette
import java.io.ByteArrayInputStream
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RafidBrowserScreen(
    bookmarks: List<RafidBookmarkEntity>,
    config: RafidConfigState,
    excludedDomains: Set<String>,
    isIncognito: Boolean,
    activePageUrl: String?,
    sniffedMedia: List<SniffedMediaCandidate>,
    onToggleIncognito: () -> Unit,
    onPageChanged: (url: String?, title: String?) -> Unit,
    onMediaSniffed: (SniffedMediaCandidate) -> Unit,
    onRequestExtractCurrentOrSniffed: (url: String, cookies: String?) -> Unit,
    onRecordBlocked: (BlockCategory) -> Unit,
    onAddCustomBookmark: (title: String, url: String) -> Unit,
    onDeleteCustomBookmark: (String) -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current
    val context = LocalContext.current

    var urlBarText by remember(activePageUrl) { mutableStateOf(activePageUrl ?: "") }
    var loadProgress by remember { mutableIntStateOf(0) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = activePageUrl != null) {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onPageChanged(null, null)
        }
    }

    val containerTint = if (isIncognito && !palette.isDark) {
        Color(0xFFEAF2FC)
    } else {
        palette.background
    }

    fun navigateToInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return
        val target = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
        urlBarText = target
        onPageChanged(target, target)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(containerTint)
            .statusBarsPadding()
            .testTag("rafid_browser_screen")
    ) {
        // Header matching reference screenshot e4461737
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activePageUrl != null) {
                        IconButton(
                            onClick = { onPageChanged(null, null) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isEnglish) "Back to home grid" else "رجوع للمنصات",
                                tint = palette.textPrimary
                            )
                        }
                    }
                    Text(
                        text = if (isEnglish) "Smart Browser" else "المتصفح الذكي",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activePageUrl != null) {
                        Surface(
                            onClick = { webViewRef?.reload() },
                            shape = CircleShape,
                            color = palette.surfaceVariant,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = if (isEnglish) "Reload" else "تحديث",
                                    tint = palette.textPrimary
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onToggleIncognito,
                        shape = CircleShape,
                        color = if (isIncognito) palette.heroBlue else palette.surfaceVariant,
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("browser_btn_incognito")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = if (isEnglish) "Incognito Mode" else "التصفح الخفي",
                                tint = if (isIncognito) Color.White else palette.textPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }

            // Address / Search Pill Bar
            OutlinedTextField(
                value = urlBarText,
                onValueChange = { urlBarText = it },
                placeholder = {
                    Text(
                        text = if (isEnglish) "Type a URL or search" else "اكتب عنواناً أو ابحث",
                        color = palette.textSecondary,
                        fontSize = 14.sp
                    )
                },
                trailingIcon = {
                    if (urlBarText.isNotBlank()) {
                        IconButton(onClick = {
                            urlBarText = ""
                            onPageChanged(null, null)
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = palette.textSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.heroBlue,
                    unfocusedBorderColor = palette.borderSubtle,
                    focusedContainerColor = palette.surface,
                    unfocusedContainerColor = palette.surface,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigateToInput(urlBarText) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("browser_url_input")
            )
        }

        if (activePageUrl == null) {
            // Landing Grid View (matching screenshot e4461737)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = if (isEnglish) "Sites & Platforms" else "مواقع ومنصات",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = palette.textPrimary
                    )
                }

                item {
                    val gridEntries = bookmarks + RafidBookmarkEntity(
                        id = "__add_custom__",
                        titleAr = "إضافة",
                        titleEn = "Add",
                        url = "",
                        platformKey = "ADD",
                        sortOrder = 999,
                        isDefault = true
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        gridEntries.chunked(4).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { bm ->
                                    PlatformGridTile(
                                        bookmark = bm,
                                        isEnglish = isEnglish,
                                        onClick = {
                                            if (bm.platformKey == "ADD") {
                                                showAddBookmarkDialog = true
                                            } else {
                                                navigateToInput(bm.url)
                                            }
                                        },
                                        onLongDelete = if (!bm.isDefault) {
                                            { onDeleteCustomBookmark(bm.id) }
                                        } else null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("browser_tile_${bm.platformKey}")
                                    )
                                }
                                repeat(4 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Mint-Green Privacy Shield Banner matching screenshot e4461737
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = palette.emeraldBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = palette.emeraldText,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (isEnglish) {
                                    "Browse normally — when you reach a video, the download button lights up. No ads, no tracking, and your login stays strictly on your device."
                                } else {
                                    "تصفّح كالمعتاد، وحين تجد فيديو يظهر زر التحميل. لا إعلانات، ولا تتبع، وتسجيل دخولك يبقى على جهازك"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 20.sp,
                                color = palette.emeraldText
                            )
                        }
                    }
                }
            }
        } else {
            // Active WebView with Ad/Tracker blocking, Cookie capture & Live Media Sniffer
            if (loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    color = palette.heroBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = !isIncognito
                            settings.databaseEnabled = !isIncognito
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    onPageChanged(view?.url, title)
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    urlBarText = url.orEmpty()
                                    onPageChanged(url, view?.title)
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null
                                    val blockCat = AdAndTrackerShield.classifyBlockedRequest(
                                        requestUrl = reqUrl,
                                        pageHost = activePageUrl,
                                        config = config,
                                        excludedDomains = excludedDomains
                                    )
                                    if (blockCat != null) {
                                        onRecordBlocked(blockCat)
                                        return WebResourceResponse(
                                            "text/plain",
                                            "utf-8",
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }

                                    if (AdAndTrackerShield.isSniffableMediaUrl(reqUrl)) {
                                        val cookies = runCatching {
                                            CookieManager.getInstance().getCookie(reqUrl)
                                                ?: CookieManager.getInstance().getCookie(activePageUrl)
                                        }.getOrNull()
                                        AdAndTrackerShield.inferMediaCandidate(
                                            url = reqUrl,
                                            pageTitle = view?.title,
                                            cookiesHeader = cookies
                                        )?.let { candidate ->
                                            onMediaSniffed(candidate)
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }

                            loadUrl(activePageUrl)
                            webViewRef = this
                        }
                    },
                    update = { wv ->
                        if (wv.url != activePageUrl && activePageUrl.startsWith("http")) {
                            wv.loadUrl(activePageUrl)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 86.dp)
                )

                DisposableEffect(Unit) {
                    onDispose {
                        runCatching {
                            if (isIncognito) {
                                webViewRef?.clearHistory()
                                webViewRef?.clearCache(true)
                            }
                            webViewRef?.destroy()
                        }
                    }
                }

                // Glowing media detected pill above the floating bottom bar
                if (sniffedMedia.isNotEmpty()) {
                    Surface(
                        onClick = {
                            val first = sniffedMedia.first()
                            onRequestExtractCurrentOrSniffed(first.url, first.cookiesHeader)
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = palette.emeraldTint,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 104.dp)
                            .testTag("browser_sniffed_download_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (isEnglish) {
                                    "Media Ready (${sniffedMedia.size}) — Download Now"
                                } else {
                                    "تم التقاط وسائط (${sniffedMedia.size}) — تحميل فوري"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddBookmarkDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { showAddBookmarkDialog = false },
            containerColor = palette.surface,
            title = {
                Text(
                    text = if (isEnglish) "Add Study Platform" else "إضافة موقع أو منصة دراسة",
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(if (isEnglish) "Platform Name" else "اسم المنصة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text(if (isEnglish) "Website URL" else "الرابط") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCustomBookmark(newTitle, newUrl)
                        showAddBookmarkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.heroBlue)
                ) {
                    Text(if (isEnglish) "Add" else "إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmarkDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }
}

@Composable
private fun PlatformGridTile(
    bookmark: RafidBookmarkEntity,
    isEnglish: Boolean,
    onClick: () -> Unit,
    onLongDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val palette = LocalRafidPalette.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = palette.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                PlatformEmblemBadge(platformKey = bookmark.platformKey)
            }
        }
        Text(
            text = if (isEnglish) bookmark.titleEn else bookmark.titleAr,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlatformEmblemBadge(platformKey: String) {
    val palette = LocalRafidPalette.current
    when (platformKey.uppercase()) {
        "YOUTUBE" -> Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFFF0000),
            modifier = Modifier.size(width = 38.dp, height = 28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        "PINTEREST" -> Surface(
            shape = CircleShape,
            color = Color(0xFFE60023),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("P", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        }
        "TIKTOK" -> Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF111111),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("♪", color = Color(0xFF25F4EE), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
        }
        "INSTAGRAM" -> Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Transparent)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f))
                )
            }
        }
        "FACEBOOK" -> Surface(
            shape = CircleShape,
            color = Color(0xFF1877F2),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("f", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            }
        }
        "X" -> Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0F172A),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("𝕏", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        "STUDY" -> Surface(
            shape = RoundedCornerShape(10.dp),
            color = palette.heroBlue.copy(alpha = 0.14f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = palette.heroBlue, modifier = Modifier.size(20.dp))
            }
        }
        "ADD" -> Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = palette.textPrimary,
            modifier = Modifier.size(24.dp)
        )
        else -> Surface(
            shape = CircleShape,
            color = palette.heroBlue.copy(alpha = 0.15f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Public, contentDescription = null, tint = palette.heroBlue, modifier = Modifier.size(20.dp))
            }
        }
    }
}
