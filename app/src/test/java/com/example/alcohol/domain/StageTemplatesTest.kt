package com.example.alcohol.domain

import com.example.alcohol.data.DrinkType
import com.example.alcohol.data.StageType
import org.junit.Assert.assertEquals
import org.junit.Test

class StageTemplatesTest {

    private val day = 24L * 60 * 60 * 1000
    private val start = 1_000_000_000L

    @Test
    fun planAccumulatesDaysFromStart() {
        val plan = StageTemplates.plan(DrinkType.BEER, start)

        assertEquals(start, plan.getValue(StageType.PRIMARY))
        assertEquals(start + 8 * day, plan.getValue(StageType.RACKING))
        assertEquals(start + 18 * day, plan.getValue(StageType.CONDITIONING))
    }

    @Test
    fun replanStartsFromActualCompletionDate() {
        val actual = start + 10 * day   // перелили на два дня позже плана
        val replanned = StageTemplates.replan(DrinkType.BEER, StageType.RACKING, actual)

        assertEquals(actual + 10 * day, replanned.getValue(StageType.CONDITIONING))
        assertEquals(actual + 24 * day, replanned.getValue(StageType.BOTTLING))
        assertEquals(false, replanned.containsKey(StageType.RACKING))
    }
}
