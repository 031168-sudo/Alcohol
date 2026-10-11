package com.example.alcohol.domain

import com.example.alcohol.data.IngredientKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CostCalculatorTest {

    @Test
    fun totalAndPerLiterAreComputed() {
        val lines = listOf(
            CostLine(kind = IngredientKind.MALT, cost = 600.0),
            CostLine(kind = IngredientKind.HOP, cost = 40.5),
            CostLine(kind = IngredientKind.YEAST, cost = 150.0),
        )

        val result = CostCalculator.calculate(lines, batchVolumeL = 13.0)

        assertEquals(790.5, result.total, 1e-9)
        assertEquals(790.5 / 13.0, result.perLiter, 1e-9)
        assertEquals(600.0, result.byKind.getValue(IngredientKind.MALT), 1e-9)
    }

    @Test
    fun emptyBatchCostsNothing() {
        val result = CostCalculator.calculate(emptyList(), batchVolumeL = 10.0)
        assertEquals(0.0, result.total, 1e-9)
        assertEquals(0.0, result.perLiter, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroVolumeIsRejected() {
        CostCalculator.calculate(emptyList(), batchVolumeL = 0.0)
    }
}
