package com.example.alcohol.domain

/** Одна списанная позиция партии: категория ингредиента и её стоимость. */
data class CostLine(
    val category: String,
    /** Стоимость списанного, в рублях. */
    val cost: Double,
)

data class CostBreakdown(
    /** Полная себестоимость партии, в рублях. */
    val total: Double,
    /** Себестоимость литра готового напитка, в рублях. */
    val perLiter: Double,
    /** Стоимость по категориям ингредиентов. */
    val byCategory: Map<String, Double>,
)

object CostCalculator {

    fun calculate(lines: List<CostLine>, batchVolumeL: Double): CostBreakdown {
        require(batchVolumeL > 0) { "Объём партии должен быть больше нуля" }

        val byCategory = lines
            .groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.cost } }
        val total = byCategory.values.sum()

        return CostBreakdown(
            total = total,
            perLiter = total / batchVolumeL,
            byCategory = byCategory,
        )
    }
}
