package com.app.games

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Maps each character to its exact 3D front-facing showcase portrait (from 02_characters_3d_showcase).
 */
@DrawableRes
fun GameCharacterId.frontShowcaseDrawableRes(): Int = when (this) {
    GameCharacterId.WALKING_BOOK -> R.drawable.img_char_front_walking_book
    GameCharacterId.FLYING_BOOK_TOWER -> R.drawable.img_char_front_flying_book_tower
    GameCharacterId.GENIUS_LAMP -> R.drawable.img_char_front_genius_lamp
    GameCharacterId.SCIENCE_ASTRONAUT -> R.drawable.img_char_front_science_astronaut
    GameCharacterId.FAHIM_SMART -> R.drawable.img_char_front_fahim_smart
    GameCharacterId.ROYAL_GOLDEN_HERO -> R.drawable.img_char_front_royal_golden_hero
}

/**
 * Maps each character to its exact 3D back-facing runner sprite (turned 180° looking forward into the stage).
 */
@DrawableRes
fun GameCharacterId.backRunnerDrawableRes(): Int = when (this) {
    GameCharacterId.WALKING_BOOK -> R.drawable.img_char_back_walking_book
    GameCharacterId.FLYING_BOOK_TOWER -> R.drawable.img_char_back_flying_book_tower
    GameCharacterId.GENIUS_LAMP -> R.drawable.img_char_back_genius_lamp
    GameCharacterId.SCIENCE_ASTRONAUT -> R.drawable.img_char_back_science_astronaut
    GameCharacterId.FAHIM_SMART -> R.drawable.img_char_back_fahim_smart
    GameCharacterId.ROYAL_GOLDEN_HERO -> R.drawable.img_char_back_royal_golden_hero
}

/**
 * Exact 3D studio character portrait for Shop cards, Home Hub, Profile, and Victory dialogs.
 * Matches Reference 01 & 02 100%.
 */
@Composable
fun CharacterPortraitCanvas(
    character: GameCharacterId,
    modifier: Modifier = Modifier.size(106.dp),
    animPhase: Float = 0f
) {
    val floatOffsetY = (sin(animPhase * 6.28318f) * 3.5f)
    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = character.frontShowcaseDrawableRes()),
            contentDescription = character.titleAr,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = floatOffsetY
                }
        )
    }
}

/**
 * Draws the 3D ground contact shadow, glowing speed trail, and power-up aura beneath the running character on the track.
 */
fun DrawScope.drawRunnerCharacterGroundEffects3D(
    character: GameCharacterId,
    centerX: Float,
    footY: Float,
    scale: Float = 1f,
    runPhase: Float = 0f,
    hasShield: Boolean = false,
    hasMagnet: Boolean = false
) {
    val s = scale * (size.width / 390f).coerceIn(0.8f, 1.45f)
    val strideSin = sin(runPhase * 6.28318f)
    val bounce = abs(strideSin) * 14f * s

    // 1. Speed trail beams extending behind the character's feet (like Ref 09 & 11)
    val trailColor = when (character) {
        GameCharacterId.FAHIM_SMART, GameCharacterId.SCIENCE_ASTRONAUT -> Color(0xFF00E5FF)
        GameCharacterId.ROYAL_GOLDEN_HERO, GameCharacterId.GENIUS_LAMP -> Color(0xFFFFD54F)
        GameCharacterId.WALKING_BOOK, GameCharacterId.FLYING_BOOK_TOWER -> Color(0xFF38BDF8)
    }
    for (leg in -1..1 step 2) {
        val legX = centerX + leg * 22f * s
        val trailLen = (75f + (if (leg == -1) strideSin else -strideSin) * 22f) * s
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(
                    trailColor.copy(alpha = 0.55f),
                    trailColor.copy(alpha = 0.0f)
                ),
                startY = footY - 8f * s,
                endY = footY + trailLen
            ),
            start = Offset(legX, footY - 8f * s),
            end = Offset(legX + leg * 8f * s, footY + trailLen),
            strokeWidth = 10f * s
        )
    }

    // 2. Soft 3D ground contact shadow ellipse
    val shadowScale = (1f - (bounce / (45f * s))).coerceIn(0.72f, 1f)
    val shadowW = 108f * s * shadowScale
    val shadowH = 30f * s * shadowScale
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.52f),
                Color.Black.copy(alpha = 0.22f),
                Color.Transparent
            ),
            center = Offset(centerX, footY - 2f * s),
            radius = shadowW * 0.6f
        ),
        topLeft = Offset(centerX - shadowW * 0.5f, footY - shadowH * 0.5f),
        size = Size(shadowW, shadowH)
    )

    // 3. Sparkle dust particles for Royal Hero / Flying Tower / Genius Lamp (like Ref 07 & 10)
    for (i in 0 until 6) {
        val pPhase = (runPhase * 2f + i * 0.17f) % 1f
        val px = centerX + sin(i * 2.1f + runPhase * 6.28f) * (34f * s)
        val py = footY + pPhase * 52f * s
        val pAlpha = (1f - pPhase) * 0.75f
        drawCircle(
            color = trailColor.copy(alpha = pAlpha),
            radius = (4.5f - pPhase * 2.5f).coerceAtLeast(1.5f) * s,
            center = Offset(px, py)
        )
    }

    // 4. Active Magnet Golden Aura Ring
    if (hasMagnet) {
        val pulse = 1f + 0.08f * sin(runPhase * 12.56f)
        drawOval(
            color = Color(0xFFFFD54F).copy(alpha = 0.65f),
            topLeft = Offset(centerX - 74f * s * pulse, footY - 24f * s * pulse),
            size = Size(148f * s * pulse, 48f * s * pulse),
            style = Stroke(width = 3.5f * s)
        )
    }

    // 5. Active Shield 3D Energy Dome Aura
    if (hasShield) {
        val domeCenter = Offset(centerX, footY - 82f * s - bounce)
        val domeRadius = 96f * s
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.06f),
                    Color(0xFF00E5FF).copy(alpha = 0.28f),
                    Color(0xFF38BDF8).copy(alpha = 0.62f)
                ),
                center = domeCenter,
                radius = domeRadius
            ),
            radius = domeRadius,
            center = domeCenter
        )
        drawCircle(
            color = Color(0xFF7DD3FC).copy(alpha = 0.85f),
            radius = domeRadius,
            center = domeCenter,
            style = Stroke(width = 2.8f * s)
        )
    }
}

/**
 * Renders the 3D Back-Facing Running Character (turned 180° looking forward into the stage!)
 * with dynamic 3D running stride physics, bounce, cadence rock, and lane-switch banking.
 */
@Composable
fun RunnerBackCharacter3DOverlay(
    character: GameCharacterId,
    centerX: Float,
    footY: Float,
    runPhase: Float,
    laneTilt: Float,
    isHitFlashing: Boolean = false
) {
    val strideAngle = runPhase * 6.283185f
    val strideSin = sin(strideAngle)
    val strideCos = cos(strideAngle * 2f)

    // Dynamic 3D running physics: vertical stride hop, left-right cadence rock, and 3D banking tilt
    val verticalBouncePx = -abs(strideSin) * 22f
    val cadenceRockDeg = strideSin * 4.2f
    val totalTiltDeg = (laneTilt * 15f + cadenceRockDeg).coerceIn(-22f, 22f)
    val scaleXAnim = 1f + strideCos * 0.028f
    val scaleYAnim = 1f - strideCos * 0.028f

    val charWidthDp = 152.dp
    val charHeightDp = 188.dp

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = character.backRunnerDrawableRes()),
            contentDescription = character.titleAr,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = charWidthDp, height = charHeightDp)
                .offset {
                    IntOffset(
                        x = (centerX - (charWidthDp.toPx() * 0.5f)).roundToInt(),
                        y = (footY - (charHeightDp.toPx() * 0.94f) + verticalBouncePx).roundToInt()
                    )
                }
                .graphicsLayer {
                    rotationZ = totalTiltDeg
                    scaleX = scaleXAnim
                    scaleY = scaleYAnim
                    alpha = if (isHitFlashing) 0.65f else 1f
                }
        )
    }
}
