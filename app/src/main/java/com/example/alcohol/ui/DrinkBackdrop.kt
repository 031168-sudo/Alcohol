package com.example.alcohol.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.example.alcohol.data.DrinkType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Фон вкладки: однотонный зелёный с редким узором в том же тоне.
 * Пиво — веточки хмеля с шишками, сидр — яблоки.
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
                    DrinkType.BEER -> Unit // Для пива фон — картинка с хмелем, см. DrinkScreen
                    DrinkType.CIDER -> drawApple(x, y, ink)
                }
            }
        }
    }
}

/** Вьющаяся веточка хмеля: изогнутый стебель, два листа и шишка на конце. */
private fun DrawScope.drawHopVine(cx: Float, cy: Float, color: Color) {
    val vine = Path().apply {
        moveTo(cx - 12.dp.toPx(), cy + 12.dp.toPx())
        cubicTo(
            cx - 4.dp.toPx(), cy + 4.dp.toPx(),
            cx + 2.dp.toPx(), cy - 4.dp.toPx(),
            cx + 8.dp.toPx(), cy - 8.dp.toPx(),
        )
    }
    drawPath(
        path = vine,
        color = color,
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
    )

    drawPath(leafPath(cx - 7.dp.toPx(), cy + 5.dp.toPx(), -150f, 9.dp.toPx(), 3.5.dp.toPx()), color)
    drawPath(leafPath(cx + 1.dp.toPx(), cy - 1.dp.toPx(), -60f, 9.dp.toPx(), 3.5.dp.toPx()), color)

    // Шишка: четыре перекрывающихся чешуйки
    val hx = cx + 11.dp.toPx()
    val hy = cy - 12.dp.toPx()
    val r = 2.8.dp.toPx()
    val offsets = listOf(0f to -3.2f, -2.6f to 0f, 2.6f to 0f, 0f to 3.2f)
    for ((ox, oy) in offsets) {
        drawCircle(color = color, radius = r, center = Offset(hx + ox.dp.toPx(), hy + oy.dp.toPx()))
    }
}

/**
 * Яблоко как в логотипе Apple: круглое тело с ямкой сверху, черенок и листик.
 */
private fun DrawScope.drawApple(cx: Float, cy: Float, color: Color) {
    val body = Path().apply {
        moveTo(cx, cy - 7.dp.toPx())
        cubicTo(
            cx + 4.dp.toPx(), cy - 11.dp.toPx(),
            cx + 12.dp.toPx(), cy - 11.dp.toPx(),
            cx + 12.dp.toPx(), cy - 2.dp.toPx(),
        )
        cubicTo(
            cx + 12.dp.toPx(), cy + 8.dp.toPx(),
            cx + 6.dp.toPx(), cy + 14.dp.toPx(),
            cx, cy + 11.dp.toPx(),
        )
        cubicTo(
            cx - 6.dp.toPx(), cy + 14.dp.toPx(),
            cx - 12.dp.toPx(), cy + 8.dp.toPx(),
            cx - 12.dp.toPx(), cy - 2.dp.toPx(),
        )
        cubicTo(
            cx - 12.dp.toPx(), cy - 11.dp.toPx(),
            cx - 4.dp.toPx(), cy - 11.dp.toPx(),
            cx, cy - 7.dp.toPx(),
        )
        close()
    }
    drawPath(body, color)

    // Черенок
    drawLine(
        color = color,
        start = Offset(cx, cy - 8.dp.toPx()),
        end = Offset(cx + 1.dp.toPx(), cy - 14.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    // Листик
    drawPath(leafPath(cx + 2.dp.toPx(), cy - 12.dp.toPx(), -35f, 8.dp.toPx(), 3.dp.toPx()), color)
}

/** Острый лист от точки (bx, by) в направлении angleDeg. */
private fun leafPath(bx: Float, by: Float, angleDeg: Float, length: Float, width: Float): Path {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val nx = -dy
    val ny = dx
    val tipX = bx + dx * length
    val tipY = by + dy * length
    val mx = bx + dx * length * 0.5f
    val my = by + dy * length * 0.5f
    return Path().apply {
        moveTo(bx, by)
        quadraticBezierTo(mx + nx * width, my + ny * width, tipX, tipY)
        quadraticBezierTo(mx - nx * width, my - ny * width, bx, by)
        close()
    }
}
