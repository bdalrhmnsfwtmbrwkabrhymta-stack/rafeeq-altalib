package com.example.ui.prayer

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

enum class PrayerBackgroundSlot(
    val key: String,
    @DrawableRes val photoRes: Int
) {
    FAJR("fajr", R.drawable.bg_mosque_fajr),
    SUNRISE("sunrise", R.drawable.bg_mosque_sunrise),
    DHUHR("dhuhr", R.drawable.bg_mosque_dhuhr),
    ASR("asr", R.drawable.bg_mosque_asr),
    MAGHRIB("maghrib", R.drawable.bg_mosque_maghrib),
    ISHA("isha", R.drawable.bg_mosque_isha)
}

fun resolvePrayerBackgroundSlot(prayerName: String): PrayerBackgroundSlot {
    val norm = prayerName.trim().lowercase()
    return when {
        norm.contains("فجر") || norm.contains("fajr") -> PrayerBackgroundSlot.FAJR
        norm.contains("شروق") || norm.contains("ضحى") || norm.contains("sunrise") -> PrayerBackgroundSlot.SUNRISE
        norm.contains("ظهر") || norm.contains("dhuhr") -> PrayerBackgroundSlot.DHUHR
        norm.contains("عصر") || norm.contains("asr") -> PrayerBackgroundSlot.ASR
        norm.contains("مغرب") || norm.contains("maghrib") -> PrayerBackgroundSlot.MAGHRIB
        norm.contains("عشاء") || norm.contains("isha") -> PrayerBackgroundSlot.ISHA
        else -> PrayerBackgroundSlot.FAJR
    }
}

/**
 * Full-bleed background engine for AdhanFullScreenActivity:
 * 1. If a real silent looping MP4 (raw `video_mosque_<slot>` or downloaded local MP4) exists,
 *    renders it fullscreen via Media3 ExoPlayer (muted, REPEAT_MODE_ALL, RESIZE_MODE_ZOOM).
 * 2. Otherwise (or on playback fallback), renders the pure real architectural mosque photograph
 *    with smooth 3D Ken Burns camera motion + real-time atmospheric light rays, moving clouds,
 *    twinkling starlight / moonlit glow, and subtle water reflection shimmer Canvas overlay.
 */
@Composable
fun RealisticAnimatedMosqueBackground(
    prayerName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val slot = remember(prayerName) { resolvePrayerBackgroundSlot(prayerName) }

    val videoUri = remember(slot) {
        val rawResId = context.resources.getIdentifier(
            "video_mosque_${slot.key}",
            "raw",
            context.packageName
        )
        when {
            rawResId != 0 -> Uri.parse("android.resource://${context.packageName}/$rawResId")
            else -> {
                val localVideo = File(context.filesDir, "mosque_videos/video_mosque_${slot.key}.mp4")
                if (localVideo.exists() && localVideo.length() > 1024L) Uri.fromFile(localVideo) else null
            }
        }
    }

    var videoReady by remember(videoUri) { mutableStateOf(false) }
    var videoFailed by remember(videoUri) { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "mosque_atmosphere")
    val kbScale by infiniteTransition.animateFloat(
        initialValue = 1.01f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 17000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ken_burns_scale"
    )
    val kbPanX by infiniteTransition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ken_burns_pan_x"
    )
    val kbPanY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ken_burns_pan_y"
    )
    val cloudDrift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cloud_drift"
    )
    val lightPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "light_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060D1E))
    ) {
        // Real architectural photograph layer (always mounted as base + fallback)
        Image(
            painter = painterResource(id = slot.photoRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = kbScale
                    scaleY = kbScale
                    translationX = kbPanX
                    translationY = kbPanY
                }
        )

        // Optional silent looping 1080p MP4 layer via Media3 ExoPlayer when bundled/downloaded
        if (videoUri != null && !videoFailed) {
            val exoPlayer = remember(videoUri) {
                runCatching {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(videoUri))
                        repeatMode = Player.REPEAT_MODE_ALL
                        volume = 0f
                        playWhenReady = true
                        prepare()
                    }
                }.getOrNull()
            }

            if (exoPlayer != null) {
                DisposableEffect(exoPlayer) {
                    val listener = object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            videoReady = true
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            videoFailed = true
                        }
                    }
                    exoPlayer.addListener(listener)
                    onDispose {
                        exoPlayer.removeListener(listener)
                        runCatching { exoPlayer.release() }
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            player = exoPlayer
                        }
                    },
                    update = { view ->
                        if (view.player !== exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (videoReady) 1f else 0f }
                )
            }
        }

        // Real-time atmospheric Canvas overlay (moving clouds, diagonal sun rays, glow & shimmer)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pulse = (sin(lightPulse * 2f * PI.toFloat()) + 1f) * 0.5f

            // Top and bottom glass-readability scrims
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color(0x99060D1E),
                    0.32f to Color(0x28060D1E),
                    0.68f to Color(0x18060D1E),
                    1.0f to Color(0xAA060D1E)
                )
            )

            // Moving translucent misty clouds in upper sky
            val driftOffset = (cloudDrift - 0.5f) * w * 0.45f
            val cloudAlpha = when (slot) {
                PrayerBackgroundSlot.ISHA -> 0.08f
                PrayerBackgroundSlot.DHUHR -> 0.14f
                else -> 0.11f
            }
            val cloudColor = when (slot) {
                PrayerBackgroundSlot.MAGHRIB -> Color(0xFFFED7AA).copy(alpha = cloudAlpha)
                PrayerBackgroundSlot.FAJR -> Color(0xFFBFDBFE).copy(alpha = cloudAlpha)
                PrayerBackgroundSlot.ISHA -> Color(0xFF94A3B8).copy(alpha = cloudAlpha)
                else -> Color.White.copy(alpha = cloudAlpha)
            }
            listOf(
                Triple(0.22f, 0.18f, w * 0.34f),
                Triple(0.62f, 0.13f, w * 0.40f),
                Triple(0.88f, 0.24f, w * 0.30f)
            ).forEachIndexed { index, (cxRatio, cyRatio, radius) ->
                val direction = if (index % 2 == 0) 1f else -0.7f
                val cx = ((w * cxRatio + driftOffset * direction) % (w * 1.4f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(cloudColor, Color.Transparent),
                        center = Offset(cx, h * cyRatio),
                        radius = radius
                    ),
                    center = Offset(cx, h * cyRatio),
                    radius = radius
                )
            }

            when (slot) {
                PrayerBackgroundSlot.ISHA -> {
                    // Twinkling stars + warm illuminated crescent/dome halo
                    val starCoords = listOf(
                        0.12f to 0.09f, 0.28f to 0.06f, 0.46f to 0.11f, 0.68f to 0.08f,
                        0.84f to 0.15f, 0.19f to 0.20f, 0.76f to 0.22f, 0.91f to 0.27f
                    )
                    starCoords.forEachIndexed { idx, (sx, sy) ->
                        val alpha = 0.28f + 0.62f * ((pulse + idx * 0.19f) % 1f)
                        drawCircle(
                            color = Color(0xFFFEF3C7).copy(alpha = alpha),
                            radius = if (idx % 2 == 0) 3.6f else 2.4f,
                            center = Offset(w * sx, h * sy)
                        )
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFBBF24).copy(alpha = 0.11f + pulse * 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.5f, h * 0.52f),
                            radius = w * 0.48f
                        ),
                        center = Offset(w * 0.5f, h * 0.52f),
                        radius = w * 0.48f
                    )
                }

                PrayerBackgroundSlot.MAGHRIB -> {
                    // Golden sunset ray sweep + courtyard marble shimmer
                    val rayPath = Path().apply {
                        moveTo(w * 0.85f, h * 0.26f)
                        lineTo(w * 0.15f, h * 0.95f)
                        lineTo(w * 0.48f, h * 0.95f)
                        lineTo(w * 0.96f, h * 0.36f)
                        close()
                    }
                    drawPath(
                        path = rayPath,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFF59E0B).copy(alpha = 0.12f + pulse * 0.06f),
                                Color.Transparent
                            ),
                            start = Offset(w * 0.85f, h * 0.28f),
                            end = Offset(w * 0.30f, h * 0.90f)
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFF97316).copy(alpha = 0.14f + pulse * 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.62f, h * 0.72f),
                            radius = w * 0.45f
                        ),
                        center = Offset(w * 0.62f, h * 0.72f),
                        radius = w * 0.45f
                    )
                }

                PrayerBackgroundSlot.FAJR -> {
                    // Cool twilight shimmer & soft courtyard golden lantern reflection
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFBBF24).copy(alpha = 0.10f + pulse * 0.07f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.5f, h * 0.70f),
                            radius = w * 0.50f
                        ),
                        center = Offset(w * 0.5f, h * 0.70f),
                        radius = w * 0.50f
                    )
                }

                PrayerBackgroundSlot.SUNRISE,
                PrayerBackgroundSlot.DHUHR,
                PrayerBackgroundSlot.ASR -> {
                    // Volumetric sunlit beams sweeping smoothly from upper corner
                    val sunX = w * (0.12f + lightPulse * 0.12f)
                    val rayPath = Path().apply {
                        moveTo(sunX, 0f)
                        lineTo(sunX + w * 0.28f, 0f)
                        lineTo(w * 0.85f, h * 0.85f)
                        lineTo(w * 0.42f, h * 0.85f)
                        close()
                    }
                    drawPath(
                        path = rayPath,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFFBEB).copy(alpha = 0.16f + pulse * 0.07f),
                                Color.Transparent
                            ),
                            start = Offset(sunX, 0f),
                            end = Offset(w * 0.65f, h * 0.85f)
                        )
                    )
                }
            }
        }
    }
}
