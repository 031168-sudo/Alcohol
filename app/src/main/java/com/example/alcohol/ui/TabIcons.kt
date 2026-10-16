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

/** Календарь с галочкой: этап уже занесён в календарь. Одноцветный, красится цветом иконки. */
val CalendarCheckIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CalendarCheck",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes("M19 4h-1V2h-2v2H8V2H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z"),
            fill = SolidColor(Color.Black),
        )
        .addPath(
            pathData = addPathNodes("M10.6 18L7.4 14.8L8.8 13.4L10.6 15.2L15.2 10.6L16.6 12Z"),
            fill = SolidColor(Color.Black),
        )
        .build()
}
