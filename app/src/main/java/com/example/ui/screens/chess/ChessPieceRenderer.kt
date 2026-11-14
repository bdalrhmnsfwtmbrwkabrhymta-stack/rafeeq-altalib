package com.example.ui.screens.chess

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.domain.chess.ChessPiece
import com.example.domain.chess.PieceColor
import com.example.domain.chess.PieceType

/**
 * Custom Staunton / Chess.com Vector Canvas Piece Renderer:
 * Guaranteed 100% Cross-Free (King features an Imperial Royal Crown with Crescent & Jewels).
 */
@Composable
fun ChessPieceVector(
    piece: ChessPiece,
    modifier: Modifier = Modifier
) {
    val isWhite = piece.color == PieceColor.WHITE
    val fillColor = if (isWhite) Color(0xFFFFFFFF) else Color(0xFF262421)
    val strokeColor = if (isWhite) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val accentColor = if (isWhite) Color(0xFFE2E8F0) else Color(0xFF475569)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
    ) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.045f

        when (piece.type) {
            PieceType.PAWN -> drawStauntonPawn(w, h, fillColor, strokeColor, accentColor, strokeWidth)
            PieceType.KNIGHT -> drawStauntonKnight(w, h, fillColor, strokeColor, accentColor, strokeWidth)
            PieceType.BISHOP -> drawStauntonBishop(w, h, fillColor, strokeColor, accentColor, strokeWidth)
            PieceType.ROOK -> drawStauntonRook(w, h, fillColor, strokeColor, accentColor, strokeWidth)
            PieceType.QUEEN -> drawStauntonQueen(w, h, fillColor, strokeColor, accentColor, strokeWidth)
            PieceType.KING -> drawStauntonKing(w, h, fillColor, strokeColor, accentColor, strokeWidth)
        }
    }
}

private fun DrawScope.drawStauntonPawn(
    w: Float, h: Float,
    fill: Color, stroke: Color, accent: Color, strokeWidth: Float
) {
    // Pedestal Base
    val basePath = Path().apply {
        moveTo(w * 0.22f, h * 0.88f)
        lineTo(w * 0.78f, h * 0.88f)
        lineTo(w * 0.72f, h * 0.78f)
        lineTo(w * 0.28f, h * 0.78f)
        close()
    }
    drawPath(basePath, fill)
    drawPath(basePath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Body
    val bodyPath = Path().apply {
        moveTo(w * 0.32f, h * 0.78f)
        cubicTo(w * 0.36f, h * 0.60f, w * 0.40f, h * 0.50f, w * 0.42f, h * 0.42f)
        lineTo(w * 0.58f, h * 0.42f)
        cubicTo(w * 0.60f, h * 0.50f, w * 0.64f, h * 0.60f, w * 0.68f, h * 0.78f)
        close()
    }
    drawPath(bodyPath, fill)
    drawPath(bodyPath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Collar Ring
    drawOval(
        color = fill,
        topLeft = Offset(w * 0.36f, h * 0.38f),
        size = Size(w * 0.28f, h * 0.08f)
    )
    drawOval(
        color = stroke,
        topLeft = Offset(w * 0.36f, h * 0.38f),
        size = Size(w * 0.28f, h * 0.08f),
        style = Stroke(strokeWidth)
    )

    // Spherical Head
    drawCircle(color = fill, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.26f))
    drawCircle(color = stroke, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.26f), style = Stroke(strokeWidth))

    // Inner highlight
    drawCircle(color = accent.copy(alpha = 0.4f), radius = w * 0.06f, center = Offset(w * 0.45f, h * 0.23f))
}

private fun DrawScope.drawStauntonRook(
    w: Float, h: Float,
    fill: Color, stroke: Color, accent: Color, strokeWidth: Float
) {
    // Base
    val basePath = Path().apply {
        moveTo(w * 0.20f, h * 0.88f)
        lineTo(w * 0.80f, h * 0.88f)
        lineTo(w * 0.74f, h * 0.78f)
        lineTo(w * 0.26f, h * 0.78f)
        close()
    }
    drawPath(basePath, fill)
    drawPath(basePath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Tower Wall
    val towerPath = Path().apply {
        moveTo(w * 0.30f, h * 0.78f)
        lineTo(w * 0.34f, h * 0.40f)
        lineTo(w * 0.66f, h * 0.40f)
        lineTo(w * 0.70f, h * 0.78f)
        close()
    }
    drawPath(towerPath, fill)
    drawPath(towerPath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Rampart / Battlements (Castles)
    val battlePath = Path().apply {
        moveTo(w * 0.25f, h * 0.40f)
        lineTo(w * 0.25f, h * 0.22f)
        lineTo(w * 0.38f, h * 0.22f)
        lineTo(w * 0.38f, h * 0.28f)
        lineTo(w * 0.45f, h * 0.28f)
        lineTo(w * 0.45f, h * 0.22f)
        lineTo(w * 0.55f, h * 0.22f)
        lineTo(w * 0.55f, h * 0.28f)
        lineTo(w * 0.62f, h * 0.28f)
        lineTo(w * 0.62f, h * 0.22f)
        lineTo(w * 0.75f, h * 0.22f)
        lineTo(w * 0.75f, h * 0.40f)
        close()
    }
    drawPath(battlePath, fill)
    drawPath(battlePath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Castle window embrasure
    drawLine(stroke, Offset(w * 0.5f, h * 0.48f), Offset(w * 0.5f, h * 0.62f), strokeWidth = strokeWidth)
}

private fun DrawScope.drawStauntonKnight(
    w: Float, h: Float,
    fill: Color, stroke: Color, accent: Color, strokeWidth: Float
) {
    // Base
    val basePath = Path().apply {
        moveTo(w * 0.22f, h * 0.88f)
        lineTo(w * 0.78f, h * 0.88f)
        lineTo(w * 0.72f, h * 0.78f)
        lineTo(w * 0.28f, h * 0.78f)
        close()
    }
    drawPath(basePath, fill)
    drawPath(basePath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Arabian Knight Steed Silhouette
    val knightPath = Path().apply {
        moveTo(w * 0.30f, h * 0.78f)
        cubicTo(w * 0.32f, h * 0.65f, w * 0.25f, h * 0.55f, w * 0.22f, h * 0.48f) // chest
        cubicTo(w * 0.20f, h * 0.40f, w * 0.24f, h * 0.35f, w * 0.32f, h * 0.36f) // muzzle bottom
        lineTo(w * 0.30f, h * 0.30f) // mouth
        lineTo(w * 0.40f, h * 0.24f) // snout top
        lineTo(w * 0.45f, h * 0.16f) // ear tip
        lineTo(w * 0.50f, h * 0.24f) // back of ear
        cubicTo(w * 0.65f, h * 0.28f, w * 0.75f, h * 0.42f, w * 0.74f, h * 0.60f) // mane curve
        lineTo(w * 0.70f, h * 0.78f)
        close()
    }
    drawPath(knightPath, fill)
    drawPath(knightPath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Eye
    drawCircle(color = stroke, radius = w * 0.035f, center = Offset(w * 0.42f, h * 0.32f))

    // Mane notches
    val manePath = Path().apply {
        moveTo(w * 0.58f, h * 0.32f)
        lineTo(w * 0.66f, h * 0.38f)
        moveTo(w * 0.62f, h * 0.44f)
        lineTo(w * 0.70f, h * 0.50f)
        moveTo(w * 0.64f, h * 0.56f)
        lineTo(w * 0.71f, h * 0.64f)
    }
    drawPath(manePath, stroke, style = Stroke(strokeWidth * 0.8f, cap = StrokeCap.Round))
}

private fun DrawScope.drawStauntonBishop(
    w: Float, h: Float,
    fill: Color, stroke: Color, accent: Color, strokeWidth: Float
) {
    // Base
    val basePath = Path().apply {
        moveTo(w * 0.22f, h * 0.88f)
        lineTo(w * 0.78f, h * 0.88f)
        lineTo(w * 0.72f, h * 0.78f)
        lineTo(w * 0.28f, h * 0.78f)
        close()
    }
    drawPath(basePath, fill)
    drawPath(basePath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Body
    val bodyPath = Path().apply {
        moveTo(w * 0.32f, h * 0.78f)
        cubicTo(w * 0.35f, h * 0.65f, w * 0.38f, h * 0.52f, w * 0.40f, h * 0.45f)
        lineTo(w * 0.60f, h * 0.45f)
        cubicTo(w * 0.62f, h * 0.52f, w * 0.65f, h * 0.65f, w * 0.68f, h * 0.78f)
        close()
    }
    drawPath(bodyPath, fill)
    drawPath(bodyPath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Mitre Dome Head
    val domePath = Path().apply {
        moveTo(w * 0.35f, h * 0.45f)
        cubicTo(w * 0.28f, h * 0.35f, w * 0.38f, h * 0.20f, w * 0.50f, h * 0.16f)
        cubicTo(w * 0.62f, h * 0.20f, w * 0.72f, h * 0.35f, w * 0.65f, h * 0.45f)
        close()
    }
    drawPath(domePath, fill)
    drawPath(domePath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Slit
    val slitPath = Path().apply {
        moveTo(w * 0.52f, h * 0.24f)
        lineTo(w * 0.40f, h * 0.36f)
    }
    drawPath(slitPath, stroke, style = Stroke(strokeWidth, cap = StrokeCap.Round))

    // Top Orb Finial
    drawCircle(color = fill, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.14f))
    drawCircle(color = stroke, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.14f), style = Stroke(strokeWidth))
}

private fun DrawScope.drawStauntonQueen(
    w: Float, h: Float,
    fill: Color, stroke: Color, accent: Color, strokeWidth: Float
) {
    // Base
    val basePath = Path().apply {
        moveTo(w * 0.20f, h * 0.88f)
        lineTo(w * 0.80f, h * 0.88f)
        lineTo(w * 0.73f, h * 0.78f)
        lineTo(w * 0.27f, h * 0.78f)
        close()
    }
    drawPath(basePath, fill)
    drawPath(basePath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Body
    val bodyPath = Path().apply {
        moveTo(w * 0.30f, h * 0.78f)
        cubicTo(w * 0.34f, h * 0.62f, w * 0.38f, h * 0.50f, w * 0.40f, h * 0.44f)
        lineTo(w * 0.60f, h * 0.44f)
        cubicTo(w * 0.62f, h * 0.50f, w * 0.66f, h * 0.62f, w * 0.70f, h * 0.78f)
        close()
    }
    drawPath(bodyPath, fill)
    drawPath(bodyPath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Coronet Spikes (5-point Royal Tiara)
    val crownPath = Path().apply {
        moveTo(w * 0.26f, h * 0.44f)
        lineTo(w * 0.20f, h * 0.24f)
        lineTo(w * 0.35f, h * 0.32f)
        lineTo(w * 0.50f, h * 0.18f)
        lineTo(w * 0.65f, h * 0.32f)
        lineTo(w * 0.80f, h * 0.24f)
        lineTo(w * 0.74f, h * 0.44f)
        close()
    }
    drawPath(crownPath, fill)
    drawPath(crownPath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Pearls on tiara tips
    val pearls = listOf(
        Offset(w * 0.20f, h * 0.23f),
        Offset(w * 0.35f, h * 0.31f),
        Offset(w * 0.50f, h * 0.17f),
        Offset(w * 0.65f, h * 0.31f),
        Offset(w * 0.80f, h * 0.23f)
    )
    pearls.forEach { pt ->
        drawCircle(fill, radius = w * 0.035f, center = pt)
        drawCircle(stroke, radius = w * 0.035f, center = pt, style = Stroke(strokeWidth * 0.8f))
    }
}

/**
 * Staunton King: Royal Imperial Crown with Crescent/Jewel Top (STRICTLY NO CROSS/CRUCIFIX).
 */
private fun DrawScope.drawStauntonKing(
    w: Float, h: Float,
    fill: Color, stroke: Color, accent: Color, strokeWidth: Float
) {
    // Base
    val basePath = Path().apply {
        moveTo(w * 0.18f, h * 0.88f)
        lineTo(w * 0.82f, h * 0.88f)
        lineTo(w * 0.74f, h * 0.78f)
        lineTo(w * 0.26f, h * 0.78f)
        close()
    }
    drawPath(basePath, fill)
    drawPath(basePath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Robe / Body
    val bodyPath = Path().apply {
        moveTo(w * 0.28f, h * 0.78f)
        cubicTo(w * 0.32f, h * 0.60f, w * 0.36f, h * 0.48f, w * 0.38f, h * 0.42f)
        lineTo(w * 0.62f, h * 0.42f)
        cubicTo(w * 0.64f, h * 0.48f, w * 0.68f, h * 0.60f, w * 0.72f, h * 0.78f)
        close()
    }
    drawPath(bodyPath, fill)
    drawPath(bodyPath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Imperial Crown Arch
    val crownPath = Path().apply {
        moveTo(w * 0.24f, h * 0.42f)
        cubicTo(w * 0.20f, h * 0.26f, w * 0.34f, h * 0.22f, w * 0.50f, h * 0.20f)
        cubicTo(w * 0.66f, h * 0.22f, w * 0.80f, h * 0.26f, w * 0.76f, h * 0.42f)
        close()
    }
    drawPath(crownPath, fill)
    drawPath(crownPath, stroke, style = Stroke(strokeWidth, join = StrokeJoin.Round))

    // Royal Crown Ribs & Jewels
    drawLine(stroke, Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.42f), strokeWidth = strokeWidth * 0.9f)
    drawLine(stroke, Offset(w * 0.36f, h * 0.24f), Offset(w * 0.36f, h * 0.42f), strokeWidth = strokeWidth * 0.7f)
    drawLine(stroke, Offset(w * 0.64f, h * 0.24f), Offset(w * 0.64f, h * 0.42f), strokeWidth = strokeWidth * 0.7f)

    // Imperial Crescent & Royal Pearl Finial (NO CROSS)
    // Pearl Dome
    drawCircle(color = fill, radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.14f))
    drawCircle(color = stroke, radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.14f), style = Stroke(strokeWidth))

    // Crescent Peak
    val crescentPath = Path().apply {
        moveTo(w * 0.44f, h * 0.11f)
        cubicTo(w * 0.42f, h * 0.05f, w * 0.58f, h * 0.05f, w * 0.56f, h * 0.11f)
        cubicTo(w * 0.52f, h * 0.08f, w * 0.48f, h * 0.08f, w * 0.44f, h * 0.11f)
        close()
    }
    drawPath(crescentPath, stroke)
}
