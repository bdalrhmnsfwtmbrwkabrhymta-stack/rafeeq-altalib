package com.example.ui.screens.islamic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Multi-toned, soft pastel-aura ("أطياف") flagship vector illustrations
 * seamlessly integrated into the card surface without boxed frames.
 */

@Composable
fun HeroMosqueAuraIcon(
    modifier: Modifier = Modifier.size(62.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.65.dp.toPx()

        // Soft glowing cyan-gold aura right behind the mosque
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF60A5FA).copy(alpha = 0.34f),
                    Color(0xFF38BDF8).copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = Offset(w * 0.50f, h * 0.50f),
                radius = w * 0.52f
            ),
            radius = w * 0.52f,
            center = Offset(w * 0.50f, h * 0.50f)
        )

        // Gilded crescent finial at top
        drawCircle(
            color = Color(0xFFFBBF24),
            radius = w * 0.055f,
            center = Offset(w * 0.50f, h * 0.11f)
        )
        drawCircle(
            color = Color(0xFF0E1E3B),
            radius = w * 0.042f,
            center = Offset(w * 0.525f, h * 0.10f)
        )
        drawLine(
            color = Color(0xFFFDE68A),
            start = Offset(w * 0.50f, h * 0.16f),
            end = Offset(w * 0.50f, h * 0.22f),
            strokeWidth = sw * 0.9f,
            cap = StrokeCap.Round
        )

        // Left & Right slender minarets
        listOf(w * 0.17f, w * 0.77f).forEach { mx ->
            drawRoundRect(
                color = Color(0xFFBAE6FD).copy(alpha = 0.32f),
                topLeft = Offset(mx, h * 0.38f),
                size = Size(w * 0.08f, h * 0.44f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFE0F2FE),
                topLeft = Offset(mx, h * 0.38f),
                size = Size(w * 0.08f, h * 0.44f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = sw * 0.8f)
            )
            // Minaret dome cap
            val cap = Path().apply {
                moveTo(mx - w * 0.01f, h * 0.38f)
                quadraticTo(mx + w * 0.04f, h * 0.27f, mx + w * 0.09f, h * 0.38f)
                close()
            }
            drawPath(cap, color = Color(0xFFFBBF24))
        }

        // Majestic layered central dome
        val dome = Path().apply {
            moveTo(w * 0.27f, h * 0.49f)
            cubicTo(w * 0.27f, h * 0.31f, w * 0.42f, h * 0.23f, w * 0.50f, h * 0.21f)
            cubicTo(w * 0.58f, h * 0.23f, w * 0.73f, h * 0.31f, w * 0.73f, h * 0.49f)
            close()
        }
        drawPath(
            path = dome,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF93C5FD))
            )
        )
        drawPath(dome, color = Color.White, style = Stroke(width = sw * 0.85f, join = StrokeJoin.Round))

        // Main sanctuary walls
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFE0F2FE).copy(alpha = 0.28f), Color(0xFF60A5FA).copy(alpha = 0.20f))
            ),
            topLeft = Offset(w * 0.21f, h * 0.49f),
            size = Size(w * 0.58f, h * 0.33f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFFE0F2FE),
            topLeft = Offset(w * 0.21f, h * 0.49f),
            size = Size(w * 0.58f, h * 0.33f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = sw * 0.9f)
        )

        // Glowing golden arched sanctuary doorway
        val arch = Path().apply {
            moveTo(w * 0.41f, h * 0.82f)
            lineTo(w * 0.41f, h * 0.64f)
            cubicTo(w * 0.41f, h * 0.55f, w * 0.59f, h * 0.55f, w * 0.59f, h * 0.64f)
            lineTo(w * 0.59f, h * 0.82f)
            close()
        }
        drawPath(
            path = arch,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))
            )
        )

        // Ground glow line
        drawLine(
            color = Color(0xFFBAE6FD).copy(alpha = 0.85f),
            start = Offset(w * 0.11f, h * 0.82f),
            end = Offset(w * 0.89f, h * 0.82f),
            strokeWidth = sw * 0.9f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun PrayerClockAuraIcon(
    modifier: Modifier = Modifier.size(44.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.85.dp.toPx()
        val center = Offset(w * 0.5f, h * 0.5f)
        val r = size.minDimension * 0.40f

        // Soft pastel dial face
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFEFF6FF), Color(0xFFBFDBFE)),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )

        // Translucent elapsed prayer sector
        drawArc(
            color = Color(0xFF3B82F6).copy(alpha = 0.24f),
            startAngle = -90f,
            sweepAngle = 118f,
            useCenter = true,
            topLeft = Offset(center.x - r * 0.78f, center.y - r * 0.78f),
            size = Size(r * 1.56f, r * 1.56f)
        )

        // Outer crisp royal blue bezel
        drawCircle(
            color = Color(0xFF1D4ED8),
            radius = r,
            center = center,
            style = Stroke(width = sw)
        )

        // Cardinal tick accents
        val ticks = listOf(0.0, 90.0, 180.0, 270.0)
        ticks.forEach { deg ->
            val rad = Math.toRadians(deg)
            drawLine(
                color = Color(0xFF60A5FA),
                start = Offset((center.x + cos(rad) * r * 0.74f).toFloat(), (center.y + sin(rad) * r * 0.74f).toFloat()),
                end = Offset((center.x + cos(rad) * r * 0.87f).toFloat(), (center.y + sin(rad) * r * 0.87f).toFloat()),
                strokeWidth = sw * 0.75f,
                cap = StrokeCap.Round
            )
        }

        // Crisp indigo hands
        drawLine(
            color = Color(0xFF1E3A8A),
            start = center,
            end = Offset(center.x, center.y - r * 0.55f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF2563EB),
            start = center,
            end = Offset(center.x + r * 0.46f, center.y + r * 0.24f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )

        // Golden center pivot dot
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = 2.6.dp.toPx(),
            center = center
        )
    }
}

@Composable
fun AdhanSpeakerAuraIcon(
    modifier: Modifier = Modifier.size(44.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()

        // Speaker cone filled with warm honey-amber gradient
        val speaker = Path().apply {
            moveTo(w * 0.14f, h * 0.37f)
            lineTo(w * 0.31f, h * 0.37f)
            lineTo(w * 0.51f, h * 0.20f)
            lineTo(w * 0.51f, h * 0.80f)
            lineTo(w * 0.31f, h * 0.63f)
            lineTo(w * 0.14f, h * 0.63f)
            close()
        }
        drawPath(
            path = speaker,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))
            )
        )
        drawPath(
            path = speaker,
            color = Color(0xFFB45309),
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Layered chromatic sound waves
        drawArc(
            color = Color(0xFFD97706),
            startAngle = -42f,
            sweepAngle = 84f,
            useCenter = false,
            topLeft = Offset(w * 0.53f, h * 0.33f),
            size = Size(w * 0.18f, h * 0.34f),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFFF59E0B),
            startAngle = -48f,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = Offset(w * 0.56f, h * 0.22f),
            size = Size(w * 0.26f, h * 0.56f),
            style = Stroke(width = sw * 0.9f, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFFFBBF24).copy(alpha = 0.6f),
            startAngle = -52f,
            sweepAngle = 104f,
            useCenter = false,
            topLeft = Offset(w * 0.59f, h * 0.12f),
            size = Size(w * 0.34f, h * 0.76f),
            style = Stroke(width = sw * 0.75f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun KaabaQiblaAuraIcon(
    modifier: Modifier = Modifier.size(44.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.4.dp.toPx()

        // 3D Isometric Kaaba Roof (Top Diamond)
        val topFace = Path().apply {
            moveTo(w * 0.50f, h * 0.14f)
            lineTo(w * 0.82f, h * 0.28f)
            lineTo(w * 0.50f, h * 0.41f)
            lineTo(w * 0.18f, h * 0.28f)
            close()
        }
        drawPath(topFace, color = Color(0xFF475569))

        // Left face (Deep charcoal obsidian)
        val leftFace = Path().apply {
            moveTo(w * 0.18f, h * 0.28f)
            lineTo(w * 0.50f, h * 0.41f)
            lineTo(w * 0.50f, h * 0.85f)
            lineTo(w * 0.18f, h * 0.72f)
            close()
        }
        drawPath(
            path = leftFace,
            brush = Brush.verticalGradient(colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
        )

        // Right face (Slightly lighter slate for clean 3D depth)
        val rightFace = Path().apply {
            moveTo(w * 0.50f, h * 0.41f)
            lineTo(w * 0.82f, h * 0.28f)
            lineTo(w * 0.82f, h * 0.72f)
            lineTo(w * 0.50f, h * 0.85f)
            close()
        }
        drawPath(
            path = rightFace,
            brush = Brush.verticalGradient(colors = listOf(Color(0xFF334155), Color(0xFF1E293B)))
        )

        // Iconic Gilded Gold Kiswah Band wrapping both 3D faces
        val goldBelt = Path().apply {
            moveTo(w * 0.18f, h * 0.39f)
            lineTo(w * 0.50f, h * 0.52f)
            lineTo(w * 0.82f, h * 0.39f)
            lineTo(w * 0.82f, h * 0.47f)
            lineTo(w * 0.50f, h * 0.60f)
            lineTo(w * 0.18f, h * 0.47f)
            close()
        }
        drawPath(
            path = goldBelt,
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFD97706), Color(0xFFFBBF24), Color(0xFFF59E0B))
            )
        )

        // Gilded Bab al-Kaaba door on right face
        val door = Path().apply {
            moveTo(w * 0.61f, h * 0.80f)
            lineTo(w * 0.61f, h * 0.62f)
            lineTo(w * 0.70f, h * 0.58f)
            lineTo(w * 0.70f, h * 0.77f)
            close()
        }
        drawPath(door, color = Color(0xFFFBBF24))

        // Crisp subtle 3D edges
        drawPath(topFace, color = Color(0xFF94A3B8).copy(alpha = 0.6f), style = Stroke(width = sw * 0.7f))

        // Mini Qibla compass jewel badge at top-right
        val cx = w * 0.83f
        val cy = h * 0.22f
        drawCircle(color = Color.White, radius = 6.5.dp.toPx(), center = Offset(cx, cy))
        drawCircle(color = Color(0xFFDC2626), radius = 3.3.dp.toPx(), center = Offset(cx, cy))
    }
}

@Composable
fun AzkarSunriseAuraIcon(
    modifier: Modifier = Modifier.size(44.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.75.dp.toPx()

        // Delicate indigo crescent & star for evening Azkar in upper-left sky
        drawCircle(color = Color(0xFF8B5CF6), radius = w * 0.085f, center = Offset(w * 0.24f, h * 0.24f))
        drawCircle(color = Color(0xFFFFF7ED), radius = w * 0.068f, center = Offset(w * 0.27f, h * 0.22f))

        // Glowing warm sunrise sun orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFDE047), Color(0xFFF97316)),
                center = Offset(w * 0.54f, h * 0.56f),
                radius = w * 0.25f
            ),
            radius = w * 0.22f,
            center = Offset(w * 0.54f, h * 0.56f)
        )

        // Radiating golden sunbeams
        listOf(-58.0, -25.0, 8.0, 42.0).forEach { deg ->
            val rad = Math.toRadians(deg - 90.0)
            val cx = w * 0.54f
            val cy = h * 0.56f
            drawLine(
                color = Color(0xFFF59E0B),
                start = Offset((cx + cos(rad) * w * 0.28f).toFloat(), (cy + sin(rad) * w * 0.28f).toFloat()),
                end = Offset((cx + cos(rad) * w * 0.37f).toFloat(), (cy + sin(rad) * w * 0.37f).toFloat()),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }

        // Soft peach mask cutting bottom of sun for crisp horizon
        drawRoundRect(
            color = Color(0xFFFFF7ED),
            topLeft = Offset(w * 0.10f, h * 0.62f),
            size = Size(w * 0.80f, h * 0.24f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        // Layered tranquil coral horizon waves
        drawLine(
            color = Color(0xFFEA580C),
            start = Offset(w * 0.14f, h * 0.64f),
            end = Offset(w * 0.86f, h * 0.64f),
            strokeWidth = sw * 1.05f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFFB923C),
            start = Offset(w * 0.26f, h * 0.77f),
            end = Offset(w * 0.74f, h * 0.77f),
            strokeWidth = sw * 0.95f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun SalawatStarAuraIcon(
    modifier: Modifier = Modifier.size(44.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.7.dp.toPx()
        val sqSize = size.minDimension * 0.50f
        val topLeft = Offset((w - sqSize) / 2f, (h - sqSize) / 2f)

        // Islamic 8-point Rub-el-Hizb star: Square 1
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A))
            ),
            topLeft = topLeft,
            size = Size(sqSize, sqSize),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFFD97706),
            topLeft = topLeft,
            size = Size(sqSize, sqSize),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = sw)
        )

        // Square 2 rotated 45 degrees
        rotate(45f, pivot = Offset(w / 2f, h / 2f)) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE68A).copy(alpha = 0.85f), Color(0xFFFBBF24).copy(alpha = 0.65f))
                ),
                topLeft = topLeft,
                size = Size(sqSize, sqSize),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFB45309),
                topLeft = topLeft,
                size = Size(sqSize, sqSize),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = sw)
            )
        }

        // Luminous amber-gold center jewel
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFFF59E0B)),
                center = Offset(w / 2f, h / 2f),
                radius = w * 0.14f
            ),
            radius = w * 0.13f,
            center = Offset(w / 2f, h / 2f)
        )
    }
}

@Composable
fun TasbeehBeadsAuraIcon(
    modifier: Modifier = Modifier.size(44.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()

        // Delicate connecting cord loop
        drawCircle(
            color = Color(0xFFA78BFA).copy(alpha = 0.65f),
            radius = w * 0.30f,
            center = Offset(w * 0.50f, h * 0.44f),
            style = Stroke(width = 1.3.dp.toPx())
        )

        // 7 glossy violet pearls around the loop
        val beadAngles = listOf(-140.0, -90.0, -40.0, 12.0, 168.0, 52.0, 128.0)
        beadAngles.forEachIndexed { idx, deg ->
            val rad = Math.toRadians(deg)
            val bx = (w * 0.50f + cos(rad) * w * 0.30f).toFloat()
            val by = (h * 0.44f + sin(rad) * w * 0.30f).toFloat()
            val beadR = if (idx == 1) 4.4.dp.toPx() else 3.8.dp.toPx()

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFDDD6FE), Color(0xFF7C3AED)),
                    center = Offset(bx - beadR * 0.3f, by - beadR * 0.3f),
                    radius = beadR * 1.4f
                ),
                radius = beadR,
                center = Offset(bx, by)
            )
            // Specular highlight dot
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = beadR * 0.28f,
                center = Offset(bx - beadR * 0.32f, by - beadR * 0.32f)
            )
        }

        // Gilded Imam bead at bottom junction
        drawRoundRect(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))),
            topLeft = Offset(w * 0.44f, h * 0.69f),
            size = Size(w * 0.12f, h * 0.13f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        // Flowing plum tassel strands
        drawLine(
            color = Color(0xFF6D28D9),
            start = Offset(w * 0.50f, h * 0.82f),
            end = Offset(w * 0.44f, h * 0.92f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF8B5CF6),
            start = Offset(w * 0.50f, h * 0.82f),
            end = Offset(w * 0.56f, h * 0.92f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun QuranHifzAuraIcon(
    modifier: Modifier = Modifier.size(44.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.65.dp.toPx()

        // Left & Right curving mint-cream pages
        val leftPage = Path().apply {
            moveTo(w * 0.50f, h * 0.28f)
            quadraticTo(w * 0.33f, h * 0.16f, w * 0.12f, h * 0.22f)
            lineTo(w * 0.12f, h * 0.73f)
            quadraticTo(w * 0.33f, h * 0.67f, w * 0.50f, h * 0.78f)
            close()
        }
        val rightPage = Path().apply {
            moveTo(w * 0.50f, h * 0.28f)
            quadraticTo(w * 0.67f, h * 0.16f, w * 0.88f, h * 0.22f)
            lineTo(w * 0.88f, h * 0.73f)
            quadraticTo(w * 0.67f, h * 0.67f, w * 0.50f, h * 0.78f)
            close()
        }

        drawPath(
            path = leftPage,
            brush = Brush.verticalGradient(colors = listOf(Color.White, Color(0xFFD1FAE5)))
        )
        drawPath(
            path = rightPage,
            brush = Brush.verticalGradient(colors = listOf(Color.White, Color(0xFFA7F3D0)))
        )

        drawPath(leftPage, color = Color(0xFF047857), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(rightPage, color = Color(0xFF047857), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Subtle verse lines on both pages
        listOf(0.39f, 0.51f).forEach { yFrac ->
            drawLine(
                color = Color(0xFF10B981).copy(alpha = 0.55f),
                start = Offset(w * 0.21f, h * yFrac),
                end = Offset(w * 0.42f, h * (yFrac + 0.03f)),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF10B981).copy(alpha = 0.55f),
                start = Offset(w * 0.58f, h * (yFrac + 0.03f)),
                end = Offset(w * 0.79f, h * yFrac),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Golden silk ribbon bookmark hanging from center spine
        val ribbon = Path().apply {
            moveTo(w * 0.46f, h * 0.74f)
            lineTo(w * 0.46f, h * 0.91f)
            lineTo(w * 0.50f, h * 0.86f)
            lineTo(w * 0.54f, h * 0.91f)
            lineTo(w * 0.54f, h * 0.74f)
            close()
        }
        drawPath(ribbon, color = Color(0xFFF59E0B))
    }
}

@Composable
fun PrayerLogAuraIcon(
    modifier: Modifier = Modifier.size(44.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.65.dp.toPx()

        // Main teal tracker card body
        drawRoundRect(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFFF0FDFA), Color(0xFFCCFBF1))),
            topLeft = Offset(w * 0.18f, h * 0.20f),
            size = Size(w * 0.58f, h * 0.66f),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF0F766E),
            topLeft = Offset(w * 0.18f, h * 0.20f),
            size = Size(w * 0.58f, h * 0.66f),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            style = Stroke(width = sw)
        )

        // Golden top clip header
        drawRoundRect(
            brush = Brush.horizontalGradient(colors = listOf(Color(0xFFF59E0B), Color(0xFFFBBF24))),
            topLeft = Offset(w * 0.34f, h * 0.13f),
            size = Size(w * 0.26f, h * 0.11f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // Tracker line hints inside card
        listOf(0.42f, 0.56f, 0.70f).forEach { yf ->
            drawLine(
                color = Color(0xFF14B8A6).copy(alpha = 0.55f),
                start = Offset(w * 0.29f, h * yf),
                end = Offset(w * 0.56f, h * yf),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Overlapping bright emerald completion badge + white check
        val bx = w * 0.72f
        val by = h * 0.68f
        val br = w * 0.17f
        drawCircle(color = Color.White, radius = br + 1.5.dp.toPx(), center = Offset(bx, by))
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(Color(0xFF34D399), Color(0xFF059669))),
            radius = br,
            center = Offset(bx, by)
        )
        val check = Path().apply {
            moveTo(bx - br * 0.42f, by + br * 0.02f)
            lineTo(bx - br * 0.10f, by + br * 0.34f)
            lineTo(bx + br * 0.46f, by - br * 0.30f)
        }
        drawPath(check, color = Color.White, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun AdhanDiagAuraIcon(
    modifier: Modifier = Modifier.size(44.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.7.dp.toPx()

        // Warm amber-coral diagnostic shield
        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.14f)
            lineTo(w * 0.80f, h * 0.26f)
            cubicTo(w * 0.80f, h * 0.56f, w * 0.67f, h * 0.77f, w * 0.50f, h * 0.87f)
            cubicTo(w * 0.33f, h * 0.77f, w * 0.20f, h * 0.56f, w * 0.20f, h * 0.26f)
            close()
        }
        drawPath(
            path = shield,
            brush = Brush.verticalGradient(colors = listOf(Color(0xFFFFEDD5), Color(0xFFFDBA74)))
        )
        drawPath(
            path = shield,
            color = Color(0xFFC2410C),
            style = Stroke(width = sw, join = StrokeJoin.Round)
        )

        // Crisp audio diagnostic waveform across center
        val pulse = Path().apply {
            moveTo(w * 0.28f, h * 0.50f)
            lineTo(w * 0.38f, h * 0.50f)
            lineTo(w * 0.45f, h * 0.35f)
            lineTo(w * 0.53f, h * 0.64f)
            lineTo(w * 0.61f, h * 0.43f)
            lineTo(w * 0.72f, h * 0.50f)
        }
        drawPath(pulse, color = Color(0xFF9A3412), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Tiny teal active status dot
        drawCircle(color = Color.White, radius = 4.2.dp.toPx(), center = Offset(w * 0.74f, h * 0.26f))
        drawCircle(color = Color(0xFF10B981), radius = 2.9.dp.toPx(), center = Offset(w * 0.74f, h * 0.26f))
    }
}

@Composable
fun FullMushafAuraIcon(
    modifier: Modifier = Modifier.size(44.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()

        // Cream-white thick book page block peeking on left/bottom
        drawRoundRect(
            color = Color(0xFFDBEAFE),
            topLeft = Offset(w * 0.22f, h * 0.20f),
            size = Size(w * 0.56f, h * 0.68f),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )

        // Royal Blue decorated hardcover front
        drawRoundRect(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))),
            topLeft = Offset(w * 0.26f, h * 0.14f),
            size = Size(w * 0.54f, h * 0.67f),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF93C5FD).copy(alpha = 0.7f),
            topLeft = Offset(w * 0.31f, h * 0.19f),
            size = Size(w * 0.44f, h * 0.57f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )

        // Gilded golden geometric medallion in cover center
        val cx = w * 0.53f
        val cy = h * 0.47f
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))),
            radius = w * 0.13f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color(0xFF1E3A8A),
            radius = w * 0.055f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.2.dp.toPx())
        )

        // Gold bookmark ribbon peeking from bottom
        val ribbon = Path().apply {
            moveTo(w * 0.44f, h * 0.80f)
            lineTo(w * 0.44f, h * 0.92f)
            lineTo(w * 0.48f, h * 0.88f)
            lineTo(w * 0.52f, h * 0.92f)
            lineTo(w * 0.52f, h * 0.80f)
            close()
        }
        drawPath(ribbon, color = Color(0xFFFBBF24))
    }
}
