package com.example.alcohol.data

import androidx.room.withTransaction
import com.example.alcohol.domain.FifoAllocator
import com.example.alcohol.domain.Lot
import kotlinx.coroutines.flow.Flow

sealed interface BrewResult {
    data class Brewed(val batchId: Long, val total: Double) : BrewResult

    /** Ингредиентов не хватает. Ключ — ingredientId, значение — сколько не хватило. Ничего не списано. */
    data class NotEnoughStock(val shortfalls: Map<Long, Double>) : BrewResult
}

/**
 * Склад и варка: закупки пополняют остатки, варка списывает их по FIFO
 * и сразу фиксирует стоимость партии.
 */
class Warehouse(private val db: AppDatabase) {

    private val stockDao = db.stockDao()
    private val batchDao = db.batchDao()

    fun stock(): Flow<List<StockRow>> = stockDao.stock()

    fun batches(type: DrinkType): Flow<List<BatchSummary>> = batchDao.summaries(type)

    /**
     * Записывает закупку. Если ингредиента с таким именем ещё нет, создаёт его.
     * Цена за единицу = totalPrice / quantity.
     */
    suspend fun purchase(
        name: String,
        kind: IngredientKind,
        unit: String,
        quantity: Double,
        totalPrice: Double,
        purchasedAt: Long,
        note: String = "",
    ): Long {
        require(quantity > 0) { "Количество должно быть больше нуля" }
        require(totalPrice >= 0) { "Цена не может быть отрицательной" }

        return db.withTransaction {
            val ingredientId = stockDao.findIngredientByName(name)?.id
                ?: stockDao.insertIngredient(Ingredient(name = name, kind = kind, unit = unit))

            stockDao.insertPurchase(
                Purchase(
                    ingredientId = ingredientId,
                    purchasedAt = purchasedAt,
                    quantity = quantity,
                    totalPrice = totalPrice,
                    remaining = quantity,
                    note = note,
                )
            )
        }
    }

    /**
     * Варит партию: списывает needs (ingredientId → количество) со склада по FIFO
     * и записывает партию со стоимостью. Если чего-то не хватает, ничего не меняется.
     */
    suspend fun brew(
        name: String,
        drinkType: DrinkType,
        recipeId: Long?,
        volumeL: Double,
        startedAt: Long,
        needs: Map<Long, Double>,
    ): BrewResult {
        require(volumeL > 0) { "Объём партии должен быть больше нуля" }

        return db.withTransaction {
            val purchases = mutableMapOf<Long, Purchase>()
            val shortfalls = mutableMapOf<Long, Double>()
            val plans = mutableListOf<Pair<Long, com.example.alcohol.domain.AllocationResult>>()

            for ((ingredientId, amount) in needs) {
                val open = stockDao.openPurchases(ingredientId)
                open.forEach { purchases[it.id] = it }
                val lots = open.map { Lot(it.id, it.remaining, it.totalPrice / it.quantity) }

                val result = FifoAllocator.allocate(lots, amount)
                if (result.shortfall > 0) shortfalls[ingredientId] = result.shortfall
                plans += ingredientId to result
            }

            if (shortfalls.isNotEmpty()) {
                return@withTransaction BrewResult.NotEnoughStock(shortfalls)
            }

            val batchId = batchDao.insertBatch(
                Batch(
                    recipeId = recipeId,
                    name = name,
                    drinkType = drinkType,
                    startedAt = startedAt,
                    volumeL = volumeL,
                )
            )

            val uses = mutableListOf<BatchIngredient>()
            for ((ingredientId, result) in plans) {
                for (allocation in result.allocations) {
                    uses += BatchIngredient(
                        batchId = batchId,
                        purchaseId = allocation.purchaseId,
                        ingredientId = ingredientId,
                        amount = allocation.amount,
                        cost = allocation.cost,
                    )
                    val before = purchases.getValue(allocation.purchaseId).remaining
                    stockDao.setRemaining(allocation.purchaseId, before - allocation.amount)
                    purchases[allocation.purchaseId] = purchases.getValue(allocation.purchaseId)
                        .copy(remaining = before - allocation.amount)
                }
            }
            batchDao.insertUses(uses)

            BrewResult.Brewed(batchId = batchId, total = uses.sumOf { it.cost })
        }
    }
}
