package com.example.alcohol.domain

/** Расчёт плановых дат этапов. Сроки задаются в днях после предыдущего этапа. */
object StagePlanner {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Плановые даты всех этапов подряд, считая от даты постановки (у первого этапа срок обычно 0 дней). */
    fun plan(daysAfterPrevious: List<Int>, startedAt: Long): List<Long> {
        var cursor = startedAt
        return daysAfterPrevious.map { days ->
            cursor += days * DAY_MS
            cursor
        }
    }
}
