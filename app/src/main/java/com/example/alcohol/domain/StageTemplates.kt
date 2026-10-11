package com.example.alcohol.domain

import com.example.alcohol.data.DrinkType
import com.example.alcohol.data.StageType

/**
 * Шаг процесса: через сколько дней после предыдущего этапа он обычно наступает.
 * Первый этап отсчитывается от даты постановки (daysAfterPrevious = 0).
 */
data class StageStep(
    val type: StageType,
    val daysAfterPrevious: Int,
)

/** Типовые сроки. Это ориентиры, после каждого фактического этапа прогноз пересчитывается. */
object StageTemplates {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun steps(drinkType: DrinkType): List<StageStep> = when (drinkType) {
        // Пиво: только первичное брожение и розлив. Розлив — через 32 дня (прежние 8 + 10 + 14).
        DrinkType.BEER -> listOf(
            StageStep(StageType.PRIMARY, 0),
            StageStep(StageType.BOTTLING, 32),
        )
        DrinkType.CIDER -> listOf(
            StageStep(StageType.PRIMARY, 0),
            StageStep(StageType.RACKING, 14),
            StageStep(StageType.CONDITIONING, 21),
            StageStep(StageType.BOTTLING, 14),
        )
    }

    /** Плановые даты всех этапов, считая от даты постановки. */
    fun plan(drinkType: DrinkType, startedAt: Long): Map<StageType, Long> {
        var cursor = startedAt
        return steps(drinkType).associate { step ->
            cursor += step.daysAfterPrevious * DAY_MS
            step.type to cursor
        }
    }

    /**
     * Пересчёт прогноза после фактического этапа: все следующие этапы
     * отсчитываются от даты, когда этап реально выполнен.
     */
    fun replan(drinkType: DrinkType, completed: StageType, completedAt: Long): Map<StageType, Long> {
        val all = steps(drinkType)
        val index = all.indexOfFirst { it.type == completed }
        require(index >= 0) { "Этапа $completed нет в шаблоне ${drinkType.name}" }

        var cursor = completedAt
        val result = mutableMapOf<StageType, Long>()
        for (step in all.drop(index + 1)) {
            cursor += step.daysAfterPrevious * DAY_MS
            result[step.type] = cursor
        }
        return result
    }
}
