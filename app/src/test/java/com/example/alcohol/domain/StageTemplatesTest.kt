package com.example.alcohol.domain

import com.example.alcohol.data.DrinkType
import com.example.alcohol.data.StageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StageTemplatesTest {

    private val day = 24L * 60 * 60 * 1000
    private val start = 1_000_000_000L

    @Test
    fun beerPlanHasOnlyPrimaryAndBottling() {
        val plan = StageTemplates.plan(DrinkType.BEER, start)

        assertEquals(setOf(StageType.PRIMARY, StageType.BOTTLING), plan.keys)
        assertEquals(start, plan.getValue(StageType.PRIMARY))
        assertEquals(start + 32 * day, plan.getValue(StageType.BOTTLING))
    }

    @Test
    fun beerReplanStartsFromActualPrimaryDate() {
        val actual = start + 3 * day   // поставили на три дня позже
        val replanned = StageTemplates.replan(DrinkType.BEER, StageType.PRIMARY, actual)

        assertEquals(actual + 32 * day, replanned.getValue(StageType.BOTTLING))
        assertFalse(replanned.containsKey(StageType.PRIMARY))
    }

    @Test
    fun replanOfStageNotInTemplateChangesNothing() {
        assertEquals(emptyMap<StageType, Long>(), StageTemplates.replan(DrinkType.BEER, StageType.RACKING, start))
    }

    @Test
    fun ciderPlanIsUnchanged() {
        val plan = StageTemplates.plan(DrinkType.CIDER, start)

        assertEquals(start + 14 * day, plan.getValue(StageType.RACKING))
        assertEquals(start + 35 * day, plan.getValue(StageType.CONDITIONING))
        assertEquals(start + 49 * day, plan.getValue(StageType.BOTTLING))
    }
}
