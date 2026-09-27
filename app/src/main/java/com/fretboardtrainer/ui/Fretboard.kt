package com.fretboardtrainer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.fretboardtrainer.music.FretPosition
import com.fretboardtrainer.music.STRING_COUNT
import kotlin.math.min
import kotlin.math.pow

val TargetGreen = Color(0xFF2ECC71)
val MissOrange = Color(0xFFF39C12)
val WrongRed = Color(0xFFE74C3C)
val MutedGray = Color(0xFF9A9A9A)

private val Wood = listOf(Color(0xFF5A3719), Color(0xFF3B220E))
private val FretMetal = Color(0xFFB8BCC2)
private val NutColor = Color(0xFFEDE4CC)
private val InlayColor = Color(0xFFE6DCC3)
private val PlainString = Color(0xFFE0E0E0)
private val WoundString = Color(0xFFCBA86E)
private val SINGLE_INLAYS = listOf(3, 5, 7, 9, 15, 17, 19, 21)

/** A dot drawn on the neck. [ring] draws an outline around it (e.g. red after a wrong answer). */
data class FretMarker(
    val position: FretPosition,
    val color: Color,
    val label: String? = null,
    val pulsing: Boolean = false,
    val ring: Color? = null,
)

/**
 * A horizontal guitar neck: nut on the left (right when [mirrored], for left-handers),
 * string 1 on top, as in tab. Frets outside [activeFrets] are dimmed.
 */
@Composable
fun Fretboard(
    frets: Int,
    activeFrets: IntRange,
    markers: List<FretMarker>,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer()
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.82f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "pulse",
    )

    Canvas(modifier) {
        // Layout is computed left-to-right, then every x goes through mx() to mirror if needed.
        fun mx(x: Float) = if (mirrored) size.width - x else x
        fun rect(brush: Brush, x0: Float, x1: Float, y0: Float, y1: Float) {
            val a = mx(x0)
            val b = mx(x1)
            drawRect(brush, Offset(minOf(a, b), y0), Size(kotlin.math.abs(b - a), y1 - y0))
        }
        fun rect(color: Color, x0: Float, x1: Float, y0: Float, y1: Float) = rect(SolidColor(color), x0, x1, y0, y1)

        val numberArea = 20.dp.toPx()
        val top = 4.dp.toPx()
        val bottom = size.height - numberArea
        val openArea = min(56.dp.toPx(), size.width * 0.08f)
        val left = openArea
        val right = size.width - 6.dp.toPx()
        val span = 1.0 - 2.0.pow(-frets / 12.0)
        fun fretX(n: Int) = left + (right - left) * ((1.0 - 2.0.pow(-n / 12.0)) / span).toFloat()
        fun cellStart(fret: Int) = if (fret == 0) 0f else fretX(fret - 1)
        fun cellEnd(fret: Int) = if (fret == 0) left else fretX(fret)
        fun noteX(fret: Int) = (cellStart(fret) + cellEnd(fret)) / 2
        val gap = (bottom - top) / STRING_COUNT
        fun stringY(string: Int) = top + gap * (string - 0.5f)
        val midY = (top + bottom) / 2
        val markedFrets = markers.map { it.position.fret }.toSet()

        rect(Brush.verticalGradient(Wood, top, bottom), left, right, top, bottom)

        val inlayRadius = gap * 0.17f
        SINGLE_INLAYS.filter { it <= frets && it !in markedFrets }
            .forEach { drawCircle(InlayColor, inlayRadius, Offset(mx(noteX(it)), midY)) }
        if (frets >= 12 && 12 !in markedFrets) {
            drawCircle(InlayColor, inlayRadius, Offset(mx(noteX(12)), stringY(2) + gap / 2))
            drawCircle(InlayColor, inlayRadius, Offset(mx(noteX(12)), stringY(4) + gap / 2))
        }
        for (n in 1..frets) {
            drawLine(FretMetal, Offset(mx(fretX(n)), top), Offset(mx(fretX(n)), bottom), 3.dp.toPx())
        }
        rect(NutColor, left - 6.dp.toPx(), left, top, bottom)
        for (string in 1..STRING_COUNT) {
            val y = stringY(string)
            drawLine(
                color = if (string <= 2) PlainString else WoundString,
                start = Offset(mx(0f), y), end = Offset(mx(right), y),
                strokeWidth = (1f + (string - 1) * 0.55f).dp.toPx(),
            )
        }

        for (fret in 0..frets) {
            if (fret !in activeFrets) rect(Color.Black.copy(alpha = 0.55f), cellStart(fret), cellEnd(fret), top, bottom)
        }

        for (n in 0..frets) {
            val style = TextStyle(color = if (n in activeFrets) Color(0xFFD0D0D0) else Color(0xFF6A6A6A), fontSize = 12.dp.toSp())
            val layout = textMeasurer.measure(n.toString(), style)
            drawText(layout, topLeft = Offset(mx(noteX(n)) - layout.size.width / 2, bottom + 2.dp.toPx()))
        }

        for (marker in markers) {
            val fret = marker.position.fret
            val radius = min(gap * 0.46f, (cellEnd(fret) - cellStart(fret)) * 0.46f)
            val center = Offset(mx(noteX(fret)), stringY(marker.position.string))
            drawMarker(marker, center, if (marker.pulsing) radius * pulse else radius, radius, textMeasurer)
        }
    }
}

private fun DrawScope.drawMarker(marker: FretMarker, center: Offset, r: Float, baseRadius: Float, textMeasurer: TextMeasurer) {
    drawCircle(marker.color.copy(alpha = 0.35f), r * 1.25f, center)
    drawCircle(marker.color, r, center)
    marker.ring?.let { drawCircle(it, r * 1.12f, center, style = Stroke(3.dp.toPx())) }
    marker.label?.let { label ->
        val style = TextStyle(
            color = Color.Black, fontWeight = FontWeight.Bold,
            fontSize = (baseRadius * if (label.length > 2) 0.6f else 0.8f).toSp(),
        )
        val layout = textMeasurer.measure(label, style)
        drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
}
