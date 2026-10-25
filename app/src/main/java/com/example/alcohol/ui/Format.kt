package com.example.alcohol.ui

import com.example.alcohol.data.StageType
import java.text.SimpleDateFormat
import java.util.Locale

private val ruDate = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

fun formatDate(millis: Long): String = ruDate.format(millis)

fun parseDate(text: String): Long? = try {
    SimpleDateFormat("dd.MM.yyyy", Locale("ru")).apply { isLenient = false }
        .parse(text.trim())?.time
} catch (_: Exception) {
    null
}

fun stageTitle(type: StageType): String = when (type) {
    StageType.MASHING -> "Затирание"
    StageType.BOIL -> "Варка"
    StageType.COOLING -> "Охлаждение"
    StageType.PRIMARY -> "Первичное брожение"
    StageType.RACKING -> "Перелив с осадка"
    StageType.SECONDARY -> "Вторичное брожение"
    StageType.CONDITIONING -> "Дображивание и дозревание"
    StageType.BOTTLING -> "Розлив"
    StageType.KEGGING -> "Розлив в кег"
    StageType.CUSTOM -> "Этап"
}

/** Название этапа партии: своё, а у совсем старых записей по типу. */
fun com.example.alcohol.data.BatchStage.displayTitle(): String =
    stageLabel(icon, title.ifBlank { stageTitle(type) })

/** «🍾 Розлив»: эмодзи, пробел и название. Если эмодзи нет, только название. */
fun stageLabel(icon: String?, title: String): String =
    if (icon.isNullOrBlank()) title else "${icon.trim()} $title"

