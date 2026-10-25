package com.example.alcohol.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StagePlannerTest {

    private val day = 24L * 60 * 60 * 1000
    private val start = 1_000_000_000L

    @Test
    fun beerPlanPrimaryThenBottlingAfterEightDays() {
        val plan = StagePlanner.plan(listOf(0, 8), start)

        assertEquals(listOf(start, start + 8 * day), plan)
    }

    @Test
    fun ciderPlanIsCumulative() {
        val plan = StagePlanner.plan(listOf(0, 14, 21, 14), start)

        assertEquals(start + 14 * day, plan[1])
        assertEquals(start + 35 * day, plan[2])
        assertEquals(start + 49 * day, plan[3])
    }

    @Test
    fun emptyProcessHasNoStages() {
        assertEquals(emptyList<Long>(), StagePlanner.plan(emptyList(), start))
    }
}
