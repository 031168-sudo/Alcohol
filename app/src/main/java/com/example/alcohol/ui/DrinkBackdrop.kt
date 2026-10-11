package com.example.alcohol.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.example.alcohol.data.DrinkType

/**
 * Фон вкладки: однотонный зелёный с редким узором в том же тоне.
 * Пиво — хмелевые шишки и пивные кружки, сидр — яблоки.
 */
fun Modifier.drinkBackdrop(drinkType: DrinkType, ink: Color): Modifier = this.drawBehind {
    val step = 96.dp.toPx()
    val cols = (size.width / step).toInt() + 2
    val rows = (size.height / step).toInt() + 2
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val x = col * step + if (row % 2 == 0) 0f else step / 2
            val y = row * step
            val angle = if ((row + col) % 2 == 0) -18f else 14f
            withTransform({ rotate(angle, pivot = Offset(x, y)) }) {
                when (drinkType) {
                    DrinkType.BEER -> drawMug(x, y, ink)
                    DrinkType.CIDER -> drawApple(x, y, ink)
                }
            }
        }
    }
}

/** Пивная кружка с пеной и ручкой. */
private fun DrawScope.drawMug(cx: Float, cy: Float, color: Color) {
    val w = 22.dp.toPx()
    val h = 26.dp.toPx()
    val left = cx - w / 2
    val top = cy - h / 2
    val stroke = 2.dp.toPx()

    // Тело кружки
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top + 4.dp.toPx()),
        size = Size(w, h - 4.dp.toPx()),
        cornerRadius = CornerRadius(3.dp.toPx()),
    )
    // Пена
    drawRoundRect(
        color = color,
        topLeft = Offset(left - 1.dp.toPx(), top),
        size = Size(w + 2.dp.toPx(), 6.dp.toPx()),
        cornerRadius = CornerRadius(3.dp.toPx()),
    )
    // Ручка
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(left + w - 2.dp.toPx(), top + 9.dp.toPx()),
        size = Size(9.dp.toPx(), 11.dp.toPx()),
        style = Stroke(width = stroke),
    )
}

/** Яблоко с листиком и черенком. */
private fun DrawScope.drawApple(cx: Float, cy: Float, color: Color) {
    val r = 11.dp.toPx()
    val stroke = 2.dp.toPx()
    // Тело яблока
    drawCircle(color = color, radius = r, center = Offset(cx, cy + 2.dp.toPx()))
    // Черенок
    drawLine(
        color = color,
        start = Offset(cx, cy - 9.dp.toPx()),
        end = Offset(cx + 2.dp.toPx(), cy - 15.dp.toPx()),
        strokeWidth = stroke,
    )
    // Листик
    drawOval(
        color = color,
        topLeft = Offset(cx + 2.dp.toPx(), cy - 17.dp.toPx()),
        size = Size(9.dp.toPx(), 5.dp.toPx()),
    )
}
