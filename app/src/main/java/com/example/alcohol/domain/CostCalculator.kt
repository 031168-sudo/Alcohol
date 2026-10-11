package com.example.alcohol.domain

import com.example.alcohol.data.IngredientKind

/** Одна строка рецепта с ценой ингредиента, достаточная для расчёта. */
data class CostLine(
    val recipeId: Long,
    /** Количество в единицах ингредиента (г, мл, шт.). */
    val amount: Double,
    val kind: IngredientKind,
    /** Цена за одну единицу, в рублях. */
    val pricePerUnit: Double,
)

data class CostBreakdown(
    /** Полная себестоимость партии, в рублях. */
    val total: Double,
    /** Себестоимость литра готового напитка, в рублях. */
    val perLiter: Double,
    /** Стоимость по категориям ингредиентов. */
    val byKind: Map<IngredientKind, Double>,
)

object CostCalculator {

    fun calculate(lines: List<CostLine>, batchVolumeL: Double): CostBreakdown {
        require(batchVolumeL > 0) { "Объём партии должен быть больше нуля" }

        val byKind = lines
            .groupBy { it.kind }
            .mapValues { (_, items) -> items.sumOf { it.amount * it.pricePerUnit } }
        val total = byKind.values.sum()

        return CostBreakdown(
            total = total,
            perLiter = total / batchVolumeL,
            byKind = byKind,
        )
    }
}
