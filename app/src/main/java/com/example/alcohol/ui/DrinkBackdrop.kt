package com.example.alcohol.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import com.example.alcohol.R
import com.example.alcohol.data.DrinkType

/**
 * Фон вкладки: рисунок, повторённый по всей странице.
 * Пиво — хмель, сидр — яблоки. Картинки одноцветные, прозрачность задана в файлах.
 */
@Composable
fun Modifier.drinkBackdrop(drinkType: DrinkType): Modifier {
    val tile = ImageBitmap.imageResource(
        if (drinkType == DrinkType.BEER) R.drawable.hop_pattern else R.drawable.apple_pattern,
    )
    val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    return this.drawBehind {
        drawRect(brush = brush, alpha = 0.16f)
    }
}
