package com.example.alcohol.domain

/** Открытая закупка: сколько осталось и за сколько за единицу. */
data class Lot(
    val purchaseId: Long,
    val remaining: Double,
    val unitPrice: Double,
)

/** Сколько списано из одной закупки и во что это обошлось. */
data class Allocation(
    val purchaseId: Long,
    val amount: Double,
    val cost: Double,
)

data class AllocationResult(
    val allocations: List<Allocation>,
    /** Сколько не хватило на складе. Ноль, если потребность закрыта полностью. */
    val shortfall: Double,
)

/**
 * Списание по FIFO: сначала уходит самая старая закупка.
 * Лоты передаются от старых к новым.
 */
object FifoAllocator {

    private const val EPS = 1e-9

    fun allocate(lots: List<Lot>, need: Double): AllocationResult {
        require(need >= 0) { "Потребность не может быть отрицательной" }

        var left = need
        val allocations = mutableListOf<Allocation>()

        for (lot in lots) {
            if (left <= EPS) break
            if (lot.remaining <= EPS) continue

            val take = minOf(lot.remaining, left)
            allocations += Allocation(
                purchaseId = lot.purchaseId,
                amount = take,
                cost = take * lot.unitPrice,
            )
            left -= take
        }

        return AllocationResult(
            allocations = allocations,
            shortfall = if (left > EPS) left else 0.0,
        )
    }
}
