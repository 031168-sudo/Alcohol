package com.example.alcohol.domain

import com.example.alcohol.data.IngredientKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CostCalculatorTest {

    @Test
    fun totalAndPerLiterAreComputed() {
        val lines = listOf(
            CostLine(recipeId = 1, amount = 3000.0, kind = IngredientKind.MALT, pricePerUnit = 0.2),   // 600
            CostLine(recipeId = 1, amount = 27.0, kind = IngredientKind.HOP, pricePerUnit = 1.5),      // 40.5
            CostLine(recipeId = 1, amount = 1.0, kind = IngredientKind.YEAST, pricePerUnit = 150.0),   // 150
        )

        val result = CostCalculator.calculate(lines, batchVolumeL = 13.0)

        assertEquals(790.5, result.total, 1e-9)
        assertEquals(790.5 / 13.0, result.perLiter, 1e-9)
        assertEquals(600.0, result.byKind.getValue(IngredientKind.MALT), 1e-9)
    }

    @Test
    fun emptyRecipeCostsNothing() {
        val result = CostCalculator.calculate(emptyList(), batchVolumeL = 10.0)
        assertEquals(0.0, result.total, 1e-9)
        assertEquals(0.0, result.perLiter, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroVolumeIsRejected() {
        CostCalculator.calculate(emptyList(), batchVolumeL = 0.0)
    }
}
