package com.example.alcohol.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Пиктограмма «Итоги»: три столбика на линии. Одноцветная, красится цветом вкладки. */
val TotalsChartIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TotalsChart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(pathData = addPathNodes("M4 19h16v2H4z"), fill = SolidColor(Color.Black))
        .addPath(pathData = addPathNodes("M5 11h3v7H5z"), fill = SolidColor(Color.Black))
        .addPath(pathData = addPathNodes("M10.5 6h3v12h-3z"), fill = SolidColor(Color.Black))
        .addPath(pathData = addPathNodes("M16 9h3v9h-3z"), fill = SolidColor(Color.Black))
        .build()
}
